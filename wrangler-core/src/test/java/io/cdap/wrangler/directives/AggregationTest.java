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

import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.TestingRig;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AggregationTest {

    @Test
    public void testTotalAggregation() throws Exception {
        // Create sample log/transaction data
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1024KB", "1000ms")); // 1MB, 1 second
        rows.add(createRow("2048KB", "2000ms")); // 2MB, 2 seconds
        rows.add(createRow("3072KB", "3000ms")); // 3MB, 3 seconds

        // Execute recipe
        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        // Verify results
        Assert.assertEquals(1, results.size());
        // Total size: (1024 + 2048 + 3072)KB = 6144KB = 6MB
        Assert.assertEquals(6.0, (Double) results.get(0).getValue("total_size_mb"), 0.001);
        // Total time: (1000 + 2000 + 3000)ms = 6000ms = 6 seconds
        Assert.assertEquals(6.0, (Double) results.get(0).getValue("total_time_sec"), 0.001);
    }

    @Test
    public void testAverageAggregation() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1024KB", "1000ms")); // 1MB, 1 second
        rows.add(createRow("2048KB", "2000ms")); // 2MB, 2 seconds
        rows.add(createRow("3072KB", "3000ms")); // 3MB, 3 seconds

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec aggregation:average"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals(1, results.size());
        // Average size: (1024 + 2048 + 3072)KB / 3 = 2048KB = 2MB
        Assert.assertEquals(2.0, (Double) results.get(0).getValue("total_size_mb"), 0.001);
        // Average time: (1000 + 2000 + 3000)ms / 3 = 2000ms = 2 seconds
        Assert.assertEquals(2.0, (Double) results.get(0).getValue("total_time_sec"), 0.001);
    }

    @Test
    public void testDifferentOutputUnits() throws Exception {
        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1048576B", "60000ms")); // 1MB, 1 minute
        rows.add(createRow("2097152B", "120000ms")); // 2MB, 2 minutes

        String[] recipe = new String[] {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_min size_unit:MB time_unit:minutes"
        };
        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals(1, results.size());
        // Total size: (1048576 + 2097152)B = 3145728B = 3MB
        Assert.assertEquals(3.0, (Double) results.get(0).getValue("total_size_mb"), 0.001);
        // Total time: (60000 + 120000)ms = 180000ms = 3 minutes
        Assert.assertEquals(3.0, (Double) results.get(0).getValue("total_time_min"), 0.001);
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

        Assert.assertEquals(1, results.size());
        // Total size: 1.5MB + 2.5MB = 4MB
        Assert.assertEquals(4.0, (Double) results.get(0).getValue("total_size_mb"), 0.001);
        // Total time: 1.5s + 2.5s = 4s
        Assert.assertEquals(4.0, (Double) results.get(0).getValue("total_time_sec"), 0.001);
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

        Assert.assertEquals(1, results.size());
        // Only valid values counted: 1MB + 2MB = 3MB
        Assert.assertEquals(3.0, (Double) results.get(0).getValue("total_size_mb"), 0.001);
        // Only valid values counted: 1s + 2s = 3s
        Assert.assertEquals(3.0, (Double) results.get(0).getValue("total_time_sec"), 0.001);
    }

    private Row createRow(String size, String time) {
        Row row = new Row();
        row.add("data_transfer_size", size);
        row.add("response_time", time);
        return row;
    }
}