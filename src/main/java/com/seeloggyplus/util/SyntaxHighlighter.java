package com.seeloggyplus.util;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter using RichTextFX StyleSpans for JSON, XML, and Log levels.
 */
public class SyntaxHighlighter {

    // JSON Pattern
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "(?<KEY>\"([^\"\\\\]|\\\\.)*\"(?=\\s*:))|" +
            "(?<STRING>\"([^\"\\\\]|\\\\.)*\")|" +
            "(?<NUMBER>-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)|" +
            "(?<BOOLEAN>\\b(true|false)\\b)|" +
            "(?<NULL>\\bnull\\b)|" +
            "(?<PUNCTUATION>[\\[\\]{}:,])"
    );

    // XML Pattern
    private static final Pattern XML_TAG_PATTERN = Pattern.compile(
            "(?<ELEMENT>(?<OPENBRACKET></?\\h*)(?<TAGNAME>[a-zA-Z_][a-zA-Z0-9._:-]*)(?<ATTRIBUTES>[^<>]*)(?<CLOSEBRACKET>\\h*/?>))|" +
            "(?<COMMENT><!--[\\s\\S]*?-->)|" +
            "(?<DECLARATION><\\?[\\s\\S]*?\\?>)|" +
            "(?<CDATA><!\\[CDATA\\[[\\s\\S]*?\\]\\]>)"
    );

    private static final Pattern ATTRIBUTES_PATTERN = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9._:-]*)\\s*(=)\\s*(\"[^\"]*\"|'[^']*')"
    );

    // Log level Pattern
    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile(
            "(?i)(ERROR|FATAL|EXCEPTION|WARN|INFO|DEBUG|TRACE)"
    );

    /**
     * Compute syntax highlighting spans for JSON text.
     */
    public static StyleSpans<Collection<String>> computeJsonHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.emptyList(), 0).create();
        }

        Matcher matcher = JSON_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass;
            if (matcher.group("KEY") != null) {
                styleClass = "json-key";
            } else if (matcher.group("STRING") != null) {
                styleClass = "json-string";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "json-number";
            } else if (matcher.group("BOOLEAN") != null) {
                styleClass = "json-boolean";
            } else if (matcher.group("NULL") != null) {
                styleClass = "json-null";
            } else if (matcher.group("PUNCTUATION") != null) {
                styleClass = "json-punctuation";
            } else {
                styleClass = "default";
            }

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    /**
     * Compute syntax highlighting spans for XML text.
     */
    public static StyleSpans<Collection<String>> computeXmlHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.emptyList(), 0).create();
        }

        Matcher matcher = XML_TAG_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);

            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("xml-comment"), matcher.end() - matcher.start());
            } else if (matcher.group("DECLARATION") != null) {
                spansBuilder.add(Collections.singleton("xml-declaration"), matcher.end() - matcher.start());
            } else if (matcher.group("CDATA") != null) {
                spansBuilder.add(Collections.singleton("xml-cdata"), matcher.end() - matcher.start());
            } else if (matcher.group("ELEMENT") != null) {
                String openBracket = matcher.group("OPENBRACKET");
                String tagName = matcher.group("TAGNAME");
                String attributesText = matcher.group("ATTRIBUTES");
                String closeBracket = matcher.group("CLOSEBRACKET");

                spansBuilder.add(Collections.singleton("xml-tag"), openBracket.length() + tagName.length());

                if (attributesText != null && !attributesText.isEmpty()) {
                    Matcher attrMatcher = ATTRIBUTES_PATTERN.matcher(attributesText);
                    int lastAttrEnd = 0;
                    while (attrMatcher.find()) {
                        spansBuilder.add(Collections.emptyList(), attrMatcher.start() - lastAttrEnd);
                        spansBuilder.add(Collections.singleton("xml-attribute"), attrMatcher.group(1).length());
                        spansBuilder.add(Collections.singleton("xml-equals"), attrMatcher.group(2).length());
                        spansBuilder.add(Collections.singleton("xml-value"), attrMatcher.group(3).length());
                        lastAttrEnd = attrMatcher.end();
                    }
                    if (attributesText.length() > lastAttrEnd) {
                        spansBuilder.add(Collections.emptyList(), attributesText.length() - lastAttrEnd);
                    }
                }

                if (closeBracket != null && !closeBracket.isEmpty()) {
                    spansBuilder.add(Collections.singleton("xml-tag"), closeBracket.length());
                }
            }
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    /**
     * Compute log level highlighting spans (ERROR, WARN, INFO, DEBUG, TRACE).
     */
    public static StyleSpans<Collection<String>> computeLogHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return new StyleSpansBuilder<Collection<String>>().add(Collections.emptyList(), 0).create();
        }

        Matcher matcher = LOG_LEVEL_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = switch (matcher.group().toUpperCase()) {
                case "ERROR", "FATAL", "EXCEPTION" -> "error";
                case "WARN" -> "warn";
                case "INFO" -> "info";
                case "DEBUG" -> "debug";
                case "TRACE" -> "trace";
                default -> "default";
            };
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
