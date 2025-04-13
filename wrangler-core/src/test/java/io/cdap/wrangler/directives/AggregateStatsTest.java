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
import io.cdap.wrangler.api.parser.Token;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cdap.cdap.etl.api.Lookup;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.TestingRig;
import io.cdap.cdap.etl.api.StageMetrics;
import io.cdap.cdap.etl.api.LookupProvider;
import io.cdap.directives.aggregates.DefaultTransientStore;
import java.net.URL;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.*;
import org.mockito.Mockito;

public class AggregateStatsTest {

    @Test
    public void testBasicAggregation() throws Exception {
        // Create sample data
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "1s")); // 1MB, 1 second
        rows.add(createRow("2MB", "2s")); // 2MB, 2 seconds
        rows.add(createRow("3MB", "3s")); // 3MB, 3 seconds

        // Execute recipe
        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        // Verify results
        assertEquals(1, results.size());
        assertEquals(6.0, (Double) results.get(0).getValue("total_size_mb"), 0.001); // 6MB total
        assertEquals(6.0, (Double) results.get(0).getValue("total_time_sec"), 0.001); // 6 seconds total
    }

    @Test
    public void testAverageAggregation() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "1s"));
        rows.add(createRow("2MB", "2s"));
        rows.add(createRow("3MB", "3s"));

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec aggregation:average"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        assertEquals(1, results.size());
        assertEquals(2.0, (Double) results.get(0).getValue("total_size_mb"), 0.001); // Average 2MB
        assertEquals(2.0, (Double) results.get(0).getValue("total_time_sec"), 0.001); // Average 2 seconds
    }

    @Test
    public void testDifferentOutputUnits() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1024KB", "60s")); // 1MB, 1 minute
        rows.add(createRow("2048KB", "120s")); // 2MB, 2 minutes

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_min size_unit:MB time_unit:minutes"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        assertEquals(1, results.size());
        assertEquals(3.0, (Double) results.get(0).getValue("total_size_mb"), 0.001); // 3MB total
        assertEquals(3.0, (Double) results.get(0).getValue("total_time_min"), 0.001); // 3 minutes total
    }

    @Test
    public void testDecimalValues() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1.5MB", "1.5s"));
        rows.add(createRow("2.5MB", "2.5s"));

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        assertEquals(1, results.size());
        assertEquals(4.0, (Double) results.get(0).getValue("total_size_mb"), 0.001); // 4MB total
        assertEquals(4.0, (Double) results.get(0).getValue("total_time_sec"), 0.001); // 4 seconds total
    }

    @Test
    public void testCaseInsensitivity() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1mb", "1s"));
        rows.add(createRow("2MB", "2S"));

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        assertEquals(1, results.size());
        assertEquals(3.0, (Double) results.get(0).getValue("total_size_mb"), 0.001); // 3MB total
        assertEquals(3.0, (Double) results.get(0).getValue("total_time_sec"), 0.001); // 3 seconds total
    }

    @Test(expected = DirectiveExecutionException.class)
    public void testInvalidSizeFormat() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("invalid", "1s"));

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        TestingRig.execute(recipe, rows);
    }

    @Test(expected = DirectiveExecutionException.class)
    public void testInvalidTimeFormat() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "invalid"));

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        TestingRig.execute(recipe, rows);
    }

    @Test
    public void testWithNullValues() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow(null, "1s"));
        rows.add(createRow("1MB", null));
        rows.add(createRow("2MB", "2s"));

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        assertEquals(1, results.size());
        assertEquals(3.0, (Double) results.get(0).getValue("total_size_mb"), 0.001); // Only valid values counted
        assertEquals(3.0, (Double) results.get(0).getValue("total_time_sec"), 0.001); // Only valid values counted
    }

    private Row createRow(String size, String time) {
        Row row = new Row();
        row.add("data_transfer_size", size);
        row.add("response_time", time);
        return row;
    }

    private static class TestExecutorContext implements ExecutorContext {
        private final Map<String, Object> context = new HashMap<>();
        private final Map<String, String> properties = new HashMap<>();
        private final TransientStore store = new DefaultTransientStore();
        private final StageMetrics metrics = Mockito.mock(StageMetrics.class);

        @Override
        public ExecutorContext.Environment getEnvironment() {
            return ExecutorContext.Environment.TESTING;
        }

        @Override
        public String getNamespace() {
            return "test";
        }

        @Override
        public StageMetrics getMetrics() {
            return metrics;
        }

        @Override
        public String getContextName() {
            return "test";
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public URL getService(String applicationId, String serviceId) {
            return null;
        }

        @Override
        public TransientStore getTransientStore() {
            return store;
        }

        @Override
        public <T> Lookup<T> provide(String namespace, Map<String, String> arguments) {
            return null;
        }
    }
}