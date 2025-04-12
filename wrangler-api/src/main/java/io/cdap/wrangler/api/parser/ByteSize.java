package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ByteSize implements Token {
    // This holds the value of the byte size in bytes.
    private long valueInBytes;

    // Regex pattern to capture numeric values followed by a unit (KB, MB, GB, TB)
    // The pattern is case-insensitive to handle both lower and upper case input units.
    private static final Pattern SIZE_PATTERN = Pattern.compile("([0-9]+)(KB|MB|GB|TB)?", Pattern.CASE_INSENSITIVE);

    // Constructor to parse the byte size string (e.g., "10KB", "100MB")
    public ByteSize(String value) {
        // Check if the value is null or empty and throw an exception if it is.
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Byte size cannot be null or empty");
        }

        // Trim any leading or trailing spaces and match the string with the regex pattern.
        Matcher matcher = SIZE_PATTERN.matcher(value.trim());
        if (matcher.matches()) {
            // Extract the numeric value and the unit (if present) from the matched string.
            long baseValue = Long.parseLong(matcher.group(1));  // Numeric part
            String unit = matcher.group(2);  // Unit (KB, MB, GB, TB) or null

            // Convert the numeric value to bytes based on the unit provided.
            switch (unit != null ? unit.toUpperCase() : "") {
                case "KB":
                    this.valueInBytes = baseValue * 1024;  // Convert to bytes
                    break;
                case "MB":
                    this.valueInBytes = baseValue * 1024 * 1024;  // Convert to bytes
                    break;
                case "GB":
                    this.valueInBytes = baseValue * 1024 * 1024 * 1024;  // Convert to bytes
                    break;
                case "TB":
                    this.valueInBytes = baseValue * 1024 * 1024 * 1024 * 1024;  // Convert to bytes
                    break;
                case "":
                    this.valueInBytes = baseValue;  // If no unit is provided, assume bytes
                    break;
                default:
                    // If the unit is invalid (not one of KB, MB, GB, TB), throw an exception.
                    throw new IllegalArgumentException("Invalid byte size unit: " + unit);
            }
        } else {
            // If the string doesn't match the expected pattern, throw an exception.
            throw new IllegalArgumentException("Invalid byte size format: " + value);
        }
    }

    // Method to retrieve the value of the byte size in bytes.
    @Override
    public Object value() {
        return this.valueInBytes;
    }

    // Method to return the type of token, which is BYTE_SIZE for this class.
    @Override
    public TokenType type() {
        return TokenType.BYTE_SIZE;
    }

    // Method to convert the value of the byte size to a JSON representation.
    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(this.valueInBytes);  // Convert the byte size to a JsonPrimitive (numeric value)
    }

    // Method to get the value of the byte size in bytes (in case you need to access it directly).
    public long getBytes() {
        return valueInBytes;
    }
} 