package com.seeloggyplus.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for prettifying and formatting JSON strings
 * Provides JSON validation, formatting, and minification
 */
public class JsonPrettify {

    private static final Logger logger = LoggerFactory.getLogger(JsonPrettify.class);
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private static final Gson COMPACT_GSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    /**
     * Prettify JSON string with indentation
     */
    public static String prettify(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        try {
            Object jsonObject = JsonParser.parseString(json);
            return PRETTY_GSON.toJson(jsonObject);
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON: {}", e.getMessage());
            return json;
        } catch (Exception e) {
            logger.error("Error prettifying JSON", e);
            return json;
        }
    }

    /**
     * Prettify JSON with custom indentation
     */
    public static String prettify(String json, int indent) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        try {
            Gson customGson = new GsonBuilder()
                    .setPrettyPrinting()
                    .serializeNulls()
                    .disableHtmlEscaping()
                    .create();

            Object jsonObject = JsonParser.parseString(json);
            String prettified = customGson.toJson(jsonObject);

            // Adjust indentation if not 2 spaces
            if (indent != 2) {
                String twoSpaces = "  ";
                String customIndent = " ".repeat(Math.max(0, indent));
                prettified = prettified.replace(twoSpaces, customIndent);
            }

            return prettified;
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON: {}", e.getMessage());
            return json;
        } catch (Exception e) {
            logger.error("Error prettifying JSON", e);
            return json;
        }
    }

    /**
     * Minify JSON string (remove whitespace)
     */
    public static String minify(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        try {
            Object jsonObject = JsonParser.parseString(json);
            return COMPACT_GSON.toJson(jsonObject);
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON: {}", e.getMessage());
            return json;
        } catch (Exception e) {
            logger.error("Error minifying JSON", e);
            return json;
        }
    }

    /**
     * Validate if string is valid JSON
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        } catch (Exception e) {
            logger.error("Error validating JSON", e);
            return false;
        }
    }

    /**
     * Extract JSON from a log message
     * Attempts to find and extract JSON objects or arrays from text
     */
    public static String extractJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // Try to find JSON object
        int jsonStart = text.indexOf('{');
        if (jsonStart >= 0) {
            int braceCount = 0;
            int jsonEnd = -1;

            for (int i = jsonStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        jsonEnd = i + 1;
                        break;
                    }
                }
            }

            if (jsonEnd > jsonStart) {
                String jsonCandidate = text.substring(jsonStart, jsonEnd);
                if (isValidJson(jsonCandidate)) {
                    return jsonCandidate;
                }
            }
        }

        // Try to find JSON array
        jsonStart = text.indexOf('[');
        if (jsonStart >= 0) {
            int bracketCount = 0;
            int jsonEnd = -1;

            for (int i = jsonStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '[') {
                    bracketCount++;
                } else if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        jsonEnd = i + 1;
                        break;
                    }
                }
            }

            if (jsonEnd > jsonStart) {
                String jsonCandidate = text.substring(jsonStart, jsonEnd);
                if (isValidJson(jsonCandidate)) {
                    return jsonCandidate;
                }
            }
        }

        return null;
    }

    /**
     * Format and prettify JSON from log message
     * Extracts JSON and returns prettified version
     */
    public static String prettifyFromLog(String logMessage) {
        String json = extractJson(logMessage);
        if (json != null) {
            return prettify(json);
        }
        return logMessage;
    }

    /**
     * Check if log message contains JSON
     */
    public static boolean containsJson(String text) {
        return extractJson(text) != null;
    }

    /**
     * Get JSON validation error message
     */
    public static String getValidationError(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "JSON string is empty";
        }

        try {
            JsonParser.parseString(json);
            return null; // Valid JSON
        } catch (JsonSyntaxException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "Unknown error: " + e.getMessage();
        }
    }
}
