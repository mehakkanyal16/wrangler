package io.cdap.wrangler.parser;

import com.google.gson.JsonPrimitive;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for ByteSize and TimeDuration parsing.
 */
public class ByteSizeTimeDurationTest {

    private static final long ONE_KB = 1024L;
    private static final long ONE_MB = ONE_KB * 1024;
    private static final long ONE_GB = ONE_MB * 1024;
    private static final long ONE_TB = ONE_GB * 1024;

    private static final long ONE_MS = 1L;
    private static final long ONE_SECOND = 1000L;
    private static final long ONE_MINUTE = 60 * ONE_SECOND;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;

    @Test
    public void testByteSizeParsing() {
        Assert.assertEquals(ONE_KB, new ByteSize("1KB").getBytes());
        Assert.assertEquals(ONE_MB, new ByteSize("1MB").getBytes());
        Assert.assertEquals(ONE_GB, new ByteSize("1GB").getBytes());
        Assert.assertEquals(ONE_TB, new ByteSize("1TB").getBytes());

        Assert.assertEquals(512L, new ByteSize("0.5KB").getBytes());
        Assert.assertEquals(1536L, new ByteSize("1.5KB").getBytes());

        Assert.assertEquals(ONE_KB, new ByteSize("1kb").getBytes());
        Assert.assertEquals(ONE_MB, new ByteSize("1mb").getBytes());

        try {
            new ByteSize("invalid");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Invalid byte size format: invalid", e.getMessage());
        }

        try {
            new ByteSize("1.5");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Invalid byte size format: 1.5", e.getMessage());
        }

        try {
            new ByteSize("1.5XX");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Unsupported byte size unit: XX", e.getMessage());
        }
    }

    @Test
    public void testTimeDurationParsing() {
        Assert.assertEquals(ONE_MS, new TimeDuration("1ms").getMilliseconds());
        Assert.assertEquals(ONE_SECOND, new TimeDuration("1s").getMilliseconds());
        Assert.assertEquals(ONE_MINUTE, new TimeDuration("1m").getMilliseconds());
        Assert.assertEquals(ONE_HOUR, new TimeDuration("1h").getMilliseconds());
        Assert.assertEquals(ONE_DAY, new TimeDuration("1d").getMilliseconds());

        Assert.assertEquals(500L, new TimeDuration("0.5s").getMilliseconds());
        Assert.assertEquals(1500L, new TimeDuration("1.5s").getMilliseconds());

        Assert.assertEquals(ONE_SECOND, new TimeDuration("1S").getMilliseconds());
        Assert.assertEquals(ONE_MINUTE, new TimeDuration("1M").getMilliseconds());

        try {
            new TimeDuration("invalid");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Invalid time duration format: invalid", e.getMessage());
        }

        try {
            new TimeDuration("1.5");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Invalid time duration format: 1.5", e.getMessage());
        }

        try {
            new TimeDuration("1.5XX");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Unsupported time duration unit: xx", e.getMessage());
        }
    }

    @Test
    public void testValue() {
        Assert.assertEquals(ONE_KB, new ByteSize("1KB").value());
        Assert.assertEquals(ONE_MB, new ByteSize("1MB").value());

        Assert.assertEquals(ONE_SECOND, new TimeDuration("1s").value());
        Assert.assertEquals(ONE_MINUTE, new TimeDuration("1m").value());
    }

    @Test
    public void testType() {
        Assert.assertEquals(TokenType.BYTE_SIZE, new ByteSize("1KB").type());
        Assert.assertEquals(TokenType.TIME_DURATION, new TimeDuration("1s").type());
    }

    @Test
    public void testToJson() {
        Assert.assertEquals(new JsonPrimitive(ONE_KB), new ByteSize("1KB").toJson());
        Assert.assertEquals(new JsonPrimitive(ONE_MB), new ByteSize("1MB").toJson());

        Assert.assertEquals(new JsonPrimitive(ONE_SECOND), new TimeDuration("1s").toJson());
        Assert.assertEquals(new JsonPrimitive(ONE_MINUTE), new TimeDuration("1m").toJson());
    }
}
