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
package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeDuration implements Token {
    // This holds the value of the time duration in milliseconds
    private long valueInMilliseconds;

    // Regex pattern to match time durations like "150ms", "2s", "3m", "1h", "5d"
    private static final Pattern DURATION_PATTERN = Pattern.compile("([0-9]+)(ms|s|m|h|d)");

    // Constructor to parse the string (e.g., "150ms", "2s")
    public TimeDuration(String value) {
        // Check if the input is null or empty and throw an exception if it is
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Time duration cannot be null or empty");
        }

        // Match the input string against the regex pattern
        Matcher matcher = DURATION_PATTERN.matcher(value.toLowerCase().trim());
        if (matcher.matches()) {
            // Extract the numeric part (e.g., "150", "2", etc.) and the unit part (e.g., "ms", "s", "m", "h", "d")
            long baseValue = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            // Convert the numeric value to milliseconds based on the unit
            switch (unit) {
                case "ms":
                    this.valueInMilliseconds = baseValue; // Milliseconds
                    break;
                case "s":
                    this.valueInMilliseconds = baseValue * 1000; // Seconds to milliseconds
                    break;
                case "m":
                    this.valueInMilliseconds = baseValue * 1000 * 60; // Minutes to milliseconds
                    break;
                case "h":
                    this.valueInMilliseconds = baseValue * 1000 * 60 * 60; // Hours to milliseconds
                    break;
                case "d":
                    this.valueInMilliseconds = baseValue * 1000 * 60 * 60 * 24; // Days to milliseconds
                    break;
                default:
                    // This case should never happen due to regex validation
                    throw new IllegalArgumentException("Invalid time unit: " + unit);
            }
        } else {
            // If the input doesn't match the pattern, throw an exception
            throw new IllegalArgumentException("Invalid time duration format: " + value);
        }
    }

    // Returns the value of the time duration in milliseconds
    @Override
    public Object value() {
        return this.valueInMilliseconds;
    }

    // Returns the type of token (TIME_DURATION)
    @Override
    public TokenType type() {
        return TokenType.TIME_DURATION;
    }

    // Converts the time duration to a JSON primitive (as a number)
    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(this.valueInMilliseconds);
    }

    // Method to get the value in milliseconds (in case you need it directly)
    public long getMilliseconds() {
        return valueInMilliseconds;
    }
}
