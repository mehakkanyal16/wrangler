/*
 * Copyright © 2017-2019 Cask Data, Inc.
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

import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.Arguments;
import io.cdap.cdap.etl.api.Lookup;
import io.cdap.cdap.etl.api.LookupProvider;
import io.cdap.cdap.etl.api.StageMetrics;
import io.cdap.wrangler.api.TransientStore;
import org.junit.Test;
import io.cdap.wrangler.api.parser.Token;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.net.URL;

import static org.junit.Assert.*;

public class AggregateDirectiveTest {
    @Test
    public void testAggregateTotal() throws Exception {
        AggregateDirective directive = new AggregateDirective();
        directive.initialize(
                createArguments("bytes", "duration", "total_bytes", "total_time", "MB", "seconds", "total"));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "1s"));
        rows.add(createRow("2MB", "2s"));
        rows.add(createRow("3MB", "3s"));

        List<Row> results = directive.execute(rows, new TestExecutorContext());
        assertEquals(1, results.size());

        Row result = results.get(0);
        assertEquals(6.0, (Double) result.getValue("total_bytes"), 0.001); // 6MB total
        assertEquals(6.0, (Double) result.getValue("total_time"), 0.001); // 6 seconds total
    }

    @Test
    public void testAggregateAverage() throws Exception {
        AggregateDirective directive = new AggregateDirective();
        directive.initialize(
                createArguments("bytes", "duration", "total_bytes", "total_time", "MB", "seconds", "average"));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "1s"));
        rows.add(createRow("2MB", "2s"));
        rows.add(createRow("3MB", "3s"));

        List<Row> results = directive.execute(rows, new TestExecutorContext());
        assertEquals(1, results.size());

        Row result = results.get(0);
        assertEquals(6.0, (Double) result.getValue("total_bytes"), 0.001); // 6MB total
        assertEquals(2.0, (Double) result.getValue("total_time"), 0.001); // 2 seconds average
    }

    @Test
    public void testUnitConversion() throws Exception {
        AggregateDirective directive = new AggregateDirective();
        directive.initialize(
                createArguments("bytes", "duration", "total_bytes", "total_time", "GB", "minutes", "total"));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1024MB", "60s")); // 1GB, 1 minute
        rows.add(createRow("2048MB", "120s")); // 2GB, 2 minutes

        List<Row> results = directive.execute(rows, new TestExecutorContext());
        assertEquals(1, results.size());

        Row result = results.get(0);
        assertEquals(3.0, (Double) result.getValue("total_bytes"), 0.001); // 3GB total
        assertEquals(3.0, (Double) result.getValue("total_time"), 0.001); // 3 minutes total
    }

    @Test(expected = DirectiveExecutionException.class)
    public void testInvalidSizeFormat() throws Exception {
        AggregateDirective directive = new AggregateDirective();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", null, null, null));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("invalid", "1s"));
        directive.execute(rows, new TestExecutorContext());
    }

    @Test(expected = DirectiveExecutionException.class)
    public void testInvalidTimeFormat() throws Exception {
        AggregateDirective directive = new AggregateDirective();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", null, null, null));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "invalid"));
        directive.execute(rows, new TestExecutorContext());
    }

    @Test
    public void testNullValues() throws Exception {
        AggregateDirective directive = new AggregateDirective();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", null, null, null));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow(null, "1s"));
        rows.add(createRow("1MB", null));
        rows.add(createRow("2MB", "2s"));

        List<Row> results = directive.execute(rows, new TestExecutorContext());
        assertEquals(1, results.size());

        Row result = results.get(0);
        assertEquals(3.0, (Double) result.getValue("total_bytes"), 0.001); // Only valid values counted
        assertEquals(3.0, (Double) result.getValue("total_time"), 0.001); // Only valid values counted
    }

    @Test
    public void testAggregateDirective() throws Exception {
        List<Row> rows = new ArrayList<>();
        Row row1 = new Row();
        row1.add("group", "A").add("size", "10KB").add("time", "5s");
        Row row2 = new Row();
        row2.add("group", "A").add("size", "20KB").add("time", "10s");
        Row row3 = new Row();
        row3.add("group", "B").add("size", "30KB").add("time", "15s");
        Row row4 = new Row();
        row4.add("group", "B").add("size", "40KB").add("time", "20s");
        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);

        AggregateDirective directive = new AggregateDirective();
        directive.initialize(createArguments("size", "time", "total_size", "total_time", "MB", "minutes", "total"));

        List<Row> result = directive.execute(rows, new TestExecutorContext());

        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getValue("group"));
        assertEquals(0.03, (Double) result.get(0).getValue("total_size"), 0.001);
        assertEquals(0.25, (Double) result.get(0).getValue("total_time"), 0.001);
        assertEquals("B", result.get(1).getValue("group"));
        assertEquals(0.07, (Double) result.get(1).getValue("total_size"), 0.001);
        assertEquals(0.583, (Double) result.get(1).getValue("total_time"), 0.001);
    }

    private Row createRow(String size, String time) {
        Row row = new Row();
        row.add("bytes", size);
        row.add("duration", time);
        return row;
    }

    private Arguments createArguments(String sizeCol, String timeCol, String totalSizeCol, String totalTimeCol,
            String sizeUnit, String timeUnit, String timeAgg) {
        return new Arguments() {
            @Override
            public <T extends Token> T value(String name) {
                switch (name) {
                    case "size_column":
                        return (T) new ColumnName(sizeCol);
                    case "time_column":
                        return (T) new ColumnName(timeCol);
                    case "total_size_column":
                        return (T) new ColumnName(totalSizeCol);
                    case "total_time_column":
                        return (T) new ColumnName(totalTimeCol);
                    case "size_unit":
                        return (T) new Text(sizeUnit);
                    case "time_unit":
                        return (T) new Text(timeUnit);
                    case "time_aggregation":
                        return (T) new Text(timeAgg);
                    default:
                        return null;
                }
            }

            @Override
            public boolean contains(String name) {
                return true;
            }

            @Override
            public JsonElement toJson() {
                JsonObject object = new JsonObject();
                JsonObject arguments = new JsonObject();
                object.addProperty("line", 0);
                object.addProperty("column", 0);
                object.addProperty("source", "test");
                object.add("arguments", arguments);
                return object;
            }

            @Override
            public int column() {
                return 0;
            }

            @Override
            public int line() {
                return 0;
            }

            @Override
            public int size() {
                return 7;
            }

            @Override
            public TokenType type(String name) {
                switch (name) {
                    case "size_column":
                    case "time_column":
                    case "total_size_column":
                    case "total_time_column":
                        return TokenType.COLUMN_NAME;
                    case "size_unit":
                    case "time_unit":
                    case "time_aggregation":
                        return TokenType.TEXT;
                    default:
                        return null;
                }
            }

            @Override
            public String source() {
                return "test";
            }
        };
    }

    private static class TestExecutorContext implements ExecutorContext, LookupProvider {
        private final Map<String, Object> context = new HashMap<>();
        private final Map<String, String> metrics = new HashMap<>();
        private final Map<String, String> environment = new HashMap<>();
        private final Properties properties = new Properties();

        @Override
        public Environment getEnvironment() {
            return Environment.TESTING;
        }

        @Override
        public String getNamespace() {
            return "test";
        }

        @Override
        public StageMetrics getMetrics() {
            return null;
        }

        @Override
        public String getContextName() {
            return "test";
        }

        @Override
        public Map<String, String> getProperties() {
            Map<String, String> props = new HashMap<>();
            properties.forEach((k, v) -> props.put(k.toString(), v.toString()));
            return props;
        }

        @Override
        public URL getService(String applicationId, String serviceId) {
            return null;
        }

        @Override
        public TransientStore getTransientStore() {
            return null;
        }

        @Override
        public <T> Lookup<T> provide(String namespace, Map<String, String> arguments) {
            return null;
        }

        @Override
        public boolean isSchemaManagementEnabled() {
            return false;
        }
    }
}
