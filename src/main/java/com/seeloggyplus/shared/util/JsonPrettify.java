package com.seeloggyplus.shared.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonPrettify {

    private static final Logger logger = LoggerFactory.getLogger(JsonPrettify.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter PRETTY_WRITER;

    static {
        DefaultPrettyPrinter defaultPrinter = new DefaultPrettyPrinter();
        DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("  ", "\n");
        defaultPrinter.indentObjectsWith(indenter);
        defaultPrinter.indentArraysWith(indenter);
        PRETTY_WRITER = MAPPER.writer(defaultPrinter);
    }

    public static String prettify(String json) {
        return prettify(json, 2);
    }

    public static String prettify(String json, int indent) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || node.isMissingNode()) {
                return json;
            }
            if (indent == 2) {
                return PRETTY_WRITER.writeValueAsString(node);
            } else {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter(" ".repeat(Math.max(0, indent)), "\n");
                printer.indentObjectsWith(indenter);
                printer.indentArraysWith(indenter);
                return MAPPER.writer(printer).writeValueAsString(node);
            }
        } catch (Exception e) {
            logger.warn("Invalid JSON or error formatting: {}", e.getMessage());
            return json;
        }
    }

    public static String minify(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || node.isMissingNode()) {
                return json;
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            logger.warn("Invalid JSON or error minifying: {}", e.getMessage());
            return json;
        }
    }

    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            JsonNode node = MAPPER.readTree(json);
            return node != null && (node.isObject() || node.isArray());
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractJson(String text) {
        JsonRegion region = findFirstJsonRegion(text, 0);
        return region != null ? region.jsonText : null;
    }

    public static boolean containsJson(String text) {
        return extractJson(text) != null;
    }

    public static String prettifyFromLog(String logMessage) {
        if (logMessage == null || logMessage.trim().isEmpty()) {
            return logMessage;
        }

        String trimmed = logMessage.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            if (isValidJson(trimmed)) {
                return prettify(trimmed);
            }
        }

        StringBuilder sb = new StringBuilder();
        int curPos = 0;

        while (curPos < logMessage.length()) {
            JsonRegion region = findFirstJsonRegion(logMessage, curPos);
            if (region == null) {
                sb.append(logMessage.substring(curPos));
                break;
            }

            sb.append(logMessage, curPos, region.startIndex);
            sb.append(prettify(region.jsonText));
            curPos = region.endIndex;
        }

        return sb.toString();
    }

    public static String getValidationError(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "JSON string is empty";
        }

        try {
            MAPPER.readTree(json);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static class JsonRegion {
        final int startIndex;
        final int endIndex;
        final String jsonText;

        JsonRegion(int startIndex, int endIndex, String jsonText) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.jsonText = jsonText;
        }
    }

    private static JsonRegion findFirstJsonRegion(String text, int searchFrom) {
        if (text == null || searchFrom >= text.length()) {
            return null;
        }

        JsonFactory factory = MAPPER.getFactory();

        for (int i = searchFrom; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '[') {
                String candidateSub = text.substring(i);
                try (JsonParser parser = factory.createParser(candidateSub)) {
                    JsonToken firstToken = parser.nextToken();
                    if (firstToken == JsonToken.START_OBJECT || firstToken == JsonToken.START_ARRAY) {
                        int depth = 0;
                        JsonToken token = firstToken;
                        while (token != null) {
                            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                                depth++;
                            } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                                depth--;
                                if (depth == 0) {
                                    long consumed = parser.getCurrentLocation().getCharOffset();
                                    if (consumed > 0 && consumed <= candidateSub.length()) {
                                        int endIdx = i + (int) consumed;
                                        String matched = text.substring(i, endIdx);
                                        if (isValidJson(matched)) {
                                            return new JsonRegion(i, endIdx, matched);
                                        }
                                    }
                                    break;
                                }
                            }
                            token = parser.nextToken();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }
}
