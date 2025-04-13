package io.cdap.wrangler.directives;

import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.Arguments;
import io.cdap.cdap.etl.api.Lookup;
import io.cdap.wrangler.api.TransientStore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.*;

public class AggregateStatsTest {

    @Test
    public void testAggregateStats() throws Exception {
        AggregateStats directive = new AggregateStats();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", "MB", "seconds", "total"));

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
    public void testAggregateStatsWithNulls() throws Exception {
        AggregateStats directive = new AggregateStats();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", "MB", "seconds", "total"));

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

    @Test(expected = DirectiveExecutionException.class)
    public void testInvalidSizeFormat() throws Exception {
        AggregateStats directive = new AggregateStats();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", "MB", "seconds", "total"));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("invalid", "1s"));
        directive.execute(rows, new TestExecutorContext());
    }

    @Test(expected = DirectiveExecutionException.class)
    public void testInvalidTimeFormat() throws Exception {
        AggregateStats directive = new AggregateStats();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", "MB", "seconds", "total"));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1MB", "invalid"));
        directive.execute(rows, new TestExecutorContext());
    }

    @Test
    public void testUnitConversion() throws Exception {
        AggregateStats directive = new AggregateStats();
        directive.initialize(createArguments("bytes", "duration", "total_bytes", "total_time", "GB", "minutes", "total"));

        List<Row> rows = new ArrayList<>();
        rows.add(createRow("1024MB", "60s")); // 1GB, 1 minute
        rows.add(createRow("2048MB", "120s")); // 2GB, 2 minutes

        List<Row> results = directive.execute(rows, new TestExecutorContext());
        assertEquals(1, results.size());
        
        Row result = results.get(0);
        assertEquals(3.0, (Double) result.getValue("total_bytes"), 0.001); // 3GB total
        assertEquals(3.0, (Double) result.getValue("total_time"), 0.001); // 3 minutes total
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
                    case "size_column": return (T) new ColumnName(sizeCol);
                    case "time_column": return (T) new ColumnName(timeCol);
                    case "total_size_column": return (T) new ColumnName(totalSizeCol);
                    case "total_time_column": return (T) new ColumnName(totalTimeCol);
                    case "size_unit": return (T) new Text(sizeUnit);
                    case "time_unit": return (T) new Text(timeUnit);
                    case "time_aggregation": return (T) new Text(timeAgg);
                    default: return null;
                }
            }

            @Override
            public boolean contains(String name) {
                return true;
            }

            @Override
            public String toJson() {
                return "{}";
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

    private static class TestExecutorContext implements ExecutorContext {
        private final Map<String, Object> context = new HashMap<>();
        private final Map<String, String> metrics = new HashMap<>();
        private final Map<String, String> environment = new HashMap<>();
        private final Properties properties = new Properties();

        @Override
        public Object get(String key) {
            return context.get(key);
        }

        @Override
        public void set(String key, Object value) {
            context.put(key, value);
        }

        @Override
        public boolean isLastBatch() {
            return true;
        }

        @Override
        public Map<String, String> getMetrics() {
            return metrics;
        }

        @Override
        public Map<String, String> getEnvironment() {
            return environment;
        }

        @Override
        public String getContextName() {
            return "test";
        }

        @Override
        public Lookup getService(String namespace, String name) {
            return null;
        }

        @Override
        public Lookup provide(String namespace, Map<String, String> arguments) {
            return null;
        }

        @Override
        public TransientStore getTransientStore() {
            return null;
        }

        @Override
        public String getNamespace() {
            return "test";
        }

        @Override
        public Properties getProperties() {
            return properties;
        }
    }
} 