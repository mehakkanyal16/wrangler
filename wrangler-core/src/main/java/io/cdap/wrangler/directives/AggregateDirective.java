/*
 * Copyright © 2024 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.wrangler.directives;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.Executor;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.api.ErrorRowException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A directive that performs aggregation operations on specified columns.
 */
@Plugin(type = Directive.TYPE)
@Name("aggregate")
@Categories(categories = { "transform", "aggregation" })
@Description("Performs aggregation operations on byte sizes and time durations with unit conversion")
public class AggregateDirective implements Directive, Executor<List<Row>, List<Row>> {
    private String byteSizeColumn;
    private String timeDurationColumn;
    private String totalSizeColumn;
    private String totalTimeColumn;
    private String byteSizeUnit = "bytes"; // Default unit
    private String timeUnit = "seconds"; // Default unit
    private String aggregationType = "total"; // Default aggregation type
    private Map<String, String> aggregations;

    // State management for aggregation
    private final Map<String, Object> aggregationState = new ConcurrentHashMap<>();
    private boolean isFirstBatch = true;
    private boolean shouldOutputResults = false;

    // Constants for unit conversion
    private static final long BYTES_IN_KB = 1024L;
    private static final long BYTES_IN_MB = BYTES_IN_KB * 1024L;
    private static final long BYTES_IN_GB = BYTES_IN_MB * 1024L;
    private static final long NANOS_IN_SECOND = 1_000_000_000L;
    private static final long NANOS_IN_MINUTE = NANOS_IN_SECOND * 60L;
    private static final long NANOS_IN_HOUR = NANOS_IN_MINUTE * 60L;

    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder("aggregate");
        builder.define("byte_size_column", TokenType.COLUMN_NAME);
        builder.define("time_duration_column", TokenType.COLUMN_NAME);
        builder.define("total_size_column", TokenType.COLUMN_NAME);
        builder.define("total_time_column", TokenType.COLUMN_NAME);
        builder.define("byte_size_unit", TokenType.TEXT); // Optional
        builder.define("time_unit", TokenType.TEXT); // Optional
        builder.define("aggregation_type", TokenType.TEXT); // Optional
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        if (args == null) {
            throw new DirectiveParseException("Arguments cannot be null");
        }

        try {
            // Extract source columns (byte size and time duration)
            ColumnName byteSizeColumn = (ColumnName) args.value("byte_size_column");
            if (byteSizeColumn == null || byteSizeColumn.value().trim().isEmpty()) {
                throw new DirectiveParseException("Byte size column argument is required");
            }
            this.byteSizeColumn = byteSizeColumn.value();

            ColumnName timeDurationColumn = (ColumnName) args.value("time_duration_column");
            if (timeDurationColumn == null || timeDurationColumn.value().trim().isEmpty()) {
                throw new DirectiveParseException("Time duration column argument is required");
            }
            this.timeDurationColumn = timeDurationColumn.value();

            // Extract target columns (total size and total time)
            ColumnName totalSizeColumn = (ColumnName) args.value("total_size_column");
            if (totalSizeColumn == null || totalSizeColumn.value().trim().isEmpty()) {
                throw new DirectiveParseException("Target column for total size is required");
            }
            this.totalSizeColumn = totalSizeColumn.value();

            ColumnName totalTimeColumn = (ColumnName) args.value("total_time_column");
            if (totalTimeColumn == null || totalTimeColumn.value().trim().isEmpty()) {
                throw new DirectiveParseException("Target column for total time is required");
            }
            this.totalTimeColumn = totalTimeColumn.value();

            // Optional arguments for output units
            Text byteSizeUnitArg = (Text) args.value("byte_size_unit");
            if (byteSizeUnitArg != null && !byteSizeUnitArg.value().trim().isEmpty()) {
                this.byteSizeUnit = byteSizeUnitArg.value().trim();
            }

            Text timeUnitArg = (Text) args.value("time_unit");
            if (timeUnitArg != null && !timeUnitArg.value().trim().isEmpty()) {
                this.timeUnit = timeUnitArg.value().trim();
            }

            // Optional argument for aggregation type (total or average)
            Text aggregationTypeArg = (Text) args.value("aggregation_type");
            if (aggregationTypeArg != null && !aggregationTypeArg.value().trim().isEmpty()) {
                this.aggregationType = aggregationTypeArg.value().trim().toLowerCase();
                if (!this.aggregationType.equals("total") && !this.aggregationType.equals("average")) {
                    throw new DirectiveParseException("Invalid aggregation type: " + this.aggregationType +
                            ". Supported types are 'total' and 'average'.");
                }
            }

        } catch (ClassCastException e) {
            throw new DirectiveParseException("Invalid argument type: " + e.getMessage());
        } catch (Exception e) {
            throw new DirectiveParseException("Error initializing directive: " + e.getMessage(), e);
        }
    }

    @Override
            
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        if (rows == null) {
            throw new DirectiveExecutionException("Input rows cannot be null");
        }

        if (rows.isEmpty()) {
            return rows;
        }

        try {
            // Initialize state for first batch
            if (isFirstBatch) {
                aggregationState.put("totalByteSize", 0.0);
                aggregationState.put("totalTime", 0.0);
                aggregationState.put("count", 0);
                isFirstBatch = false;
            }

            // Get current totals
            double totalByteSize = (double) aggregationState.get("totalByteSize");
            double totalTime = (double) aggregationState.get("totalTime");
            int count = (int) aggregationState.get("count");

            // Process each row
            for (Row row : rows) {
                Object byteSizeValue = row.getValue(byteSizeColumn);
                Object timeValue = row.getValue(timeDurationColumn);

                if (byteSizeValue != null && byteSizeValue instanceof Number) {
                    totalByteSize += ((Number) byteSizeValue).doubleValue();
                }
                if (timeValue != null && timeValue instanceof Number) {
                    totalTime += ((Number) timeValue).doubleValue();
                }

                count++;
            }

            // Update state with new totals
            aggregationState.put("totalByteSize", totalByteSize);
            aggregationState.put("totalTime", totalTime);
            aggregationState.put("count", count);

            // If this is the last batch, perform final calculations
            if (shouldOutputResults) {
                // Convert total byte size to requested unit
                double convertedSize = convertByteSize(totalByteSize, byteSizeUnit);

                // Convert total time to requested unit
                double convertedTime = convertTime(totalTime, timeUnit);

                // Calculate average if requested
                if (aggregationType.equals("average") && count > 0) {
                    convertedTime = convertedTime / count;
                }

                // Create result row
                List<Row> result = new ArrayList<>();
                Row resultRow = new Row();
                resultRow.add(totalSizeColumn, convertedSize);
                resultRow.add(totalTimeColumn, convertedTime);
                result.add(resultRow);

                return result;
            }

            return rows; // Return input rows for intermediate batches

        } catch (Exception e) {
            throw new DirectiveExecutionException("Error executing aggregate directive: " + e.getMessage(), e);
        }
    }

    private double convertByteSize(double bytes, String targetUnit) {
        switch (targetUnit.toLowerCase()) {
            case "kb":
                return bytes / BYTES_IN_KB;
            case "mb":
                return bytes / BYTES_IN_MB;
            case "gb":
                return bytes / BYTES_IN_GB;
            default: // bytes
                return bytes;
        }
    }

    private double convertTime(double nanoseconds, String targetUnit) {
        switch (targetUnit.toLowerCase()) {
            case "seconds":
                return nanoseconds / NANOS_IN_SECOND;
            case "minutes":
                return nanoseconds / NANOS_IN_MINUTE;
            case "hours":
                return nanoseconds / NANOS_IN_HOUR;
            default: // nanoseconds
                return nanoseconds;
        }
    }

    @Override
    public void destroy() {
        // Clean up any resources if needed
        byteSizeColumn = null;
        timeDurationColumn = null;
        totalSizeColumn = null;
        totalTimeColumn = null;
        byteSizeUnit = null;
        timeUnit = null;
        aggregationType = null;
        aggregations = null;
        aggregationState.clear();
        isFirstBatch = true;
        shouldOutputResults = false;
    }
}
