package com.seeloggyplus.util;

import com.seeloggyplus.model.LogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchQueryParser {

    private static final Logger logger = LoggerFactory.getLogger(SearchQueryParser.class);

    // Regex untuk memecah query: mendukung "phrase search", (keyword), AND/OR/NOT
    // Memisahkan berdasarkan spasi, kecuali di dalam tanda kutip atau kurung
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\"([^\"]*)\"|" +    // Group 1: Double quoted phrase
            "'([^']*)'|" +     // Group 2: Single quoted phrase
            "(AND|OR|NOT)|" +  // Group 3: Operators
            "(\\w+)",          // Group 4: Regular words
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses a search query string and returns a list of SearchTokens.
     * Supports AND, OR, NOT operators and "phrase search".
     * Example: "error AND \"database connection\" OR NOT warning"
     *
     * @param query The search query string.
     * @return A list of SearchTokens representing the parsed query.
     */
    public List<SearchToken> parse(String query) {
        List<SearchToken> tokens = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return tokens;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(query);
        while (matcher.find()) {
            String tokenValue = null;
            if (matcher.group(1) != null) { // Double quoted phrase
                tokenValue = matcher.group(1);
                tokens.add(new SearchToken(tokenValue, SearchToken.Type.PHRASE));
            } else if (matcher.group(2) != null) { // Single quoted phrase
                tokenValue = matcher.group(2);
                tokens.add(new SearchToken(tokenValue, SearchToken.Type.PHRASE));
            } else if (matcher.group(3) != null) { // Operator (AND, OR, NOT)
                tokenValue = matcher.group(3);
                if (tokenValue.equalsIgnoreCase("AND")) {
                    tokens.add(new SearchToken(tokenValue, SearchToken.Type.OPERATOR_AND));
                } else if (tokenValue.equalsIgnoreCase("OR")) {
                    tokens.add(new SearchToken(tokenValue, SearchToken.Type.OPERATOR_OR));
                } else if (tokenValue.equalsIgnoreCase("NOT")) {
                    tokens.add(new SearchToken(tokenValue, SearchToken.Type.OPERATOR_NOT));
                }
            } else if (matcher.group(4) != null) { // Regular keyword
                tokenValue = matcher.group(4);
                tokens.add(new SearchToken(tokenValue, SearchToken.Type.KEYWORD));
            }
        }
        return tokens;
    }

    /**
     * Represents a token in the parsed search query.
     */
    public static class SearchToken {
        public enum Type { KEYWORD, PHRASE, OPERATOR_AND, OPERATOR_OR, OPERATOR_NOT }
        
        public String value;
        public Type type;

        public SearchToken(String value, Type type) {
            this.value = value;
            this.type = type;
        }

        @Override
        public String toString() {
            return "SearchToken{"
                    + "value='" + value + "'"
                    + ", type=" + type + 
                    '}';
        }
    }
}
