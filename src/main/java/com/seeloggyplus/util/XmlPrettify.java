package com.seeloggyplus.util;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for prettifying and formatting XML strings using dom4j.
 * Provides robust XML validation, formatting, extraction, and minification.
 */
public class XmlPrettify {

    private static final Logger logger = LoggerFactory.getLogger(XmlPrettify.class);

    private static final Pattern TAG_START_PATTERN = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9._:-]*)");

    /**
     * Prettify XML string with default 2-space indentation.
     */
    public static String prettify(String xml) {
        return prettify(xml, 2);
    }

    /**
     * Prettify XML with custom indentation.
     */
    public static String prettify(String xml, int indent) {
        if (xml == null || xml.trim().isEmpty()) {
            return xml;
        }

        try {
            Document document = DocumentHelper.parseText(xml.trim());
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setIndent(" ".repeat(Math.max(0, indent)));
            format.setEncoding("UTF-8");
            format.setSuppressDeclaration(!xml.trim().startsWith("<?xml"));
            format.setNewLineAfterDeclaration(false);

            StringWriter writer = new StringWriter();
            XMLWriter xmlWriter = new XMLWriter(writer, format);
            xmlWriter.write(document);
            xmlWriter.flush();
            xmlWriter.close();

            return writer.toString().trim();
        } catch (Exception e) {
            logger.warn("Invalid XML or error formatting: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Minify XML string (remove unnecessary whitespace).
     */
    public static String minify(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return xml;
        }

        try {
            Document document = DocumentHelper.parseText(xml.trim());
            OutputFormat format = OutputFormat.createCompactFormat();
            format.setEncoding("UTF-8");
            format.setSuppressDeclaration(!xml.trim().startsWith("<?xml"));

            StringWriter writer = new StringWriter();
            XMLWriter xmlWriter = new XMLWriter(writer, format);
            xmlWriter.write(document);
            xmlWriter.flush();
            xmlWriter.close();

            return writer.toString().trim();
        } catch (Exception e) {
            logger.warn("Invalid XML or error minifying: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Validate if string is valid XML.
     */
    public static boolean isValidXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return false;
        }

        try {
            DocumentHelper.parseText(xml.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract the first valid XML block from text.
     */
    public static String extractXml(String text) {
        XmlRegion region = findFirstXmlRegion(text, 0);
        return region != null ? region.xmlText : null;
    }

    /**
     * Check if text contains valid XML.
     */
    public static boolean containsXml(String text) {
        return extractXml(text) != null;
    }

    /**
     * Format and prettify all XML blocks found within a log message,
     * preserving surrounding log prefixes, timestamps, and suffixes.
     */
    public static String prettifyFromLog(String logMessage) {
        if (logMessage == null || logMessage.trim().isEmpty()) {
            return logMessage;
        }

        String trimmed = logMessage.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            if (isValidXml(trimmed)) {
                return prettify(trimmed);
            }
        }

        StringBuilder sb = new StringBuilder();
        int curPos = 0;

        while (curPos < logMessage.length()) {
            XmlRegion region = findFirstXmlRegion(logMessage, curPos);
            if (region == null) {
                sb.append(logMessage.substring(curPos));
                break;
            }

            sb.append(logMessage, curPos, region.startIndex);
            sb.append(prettify(region.xmlText));
            curPos = region.endIndex;
        }

        return sb.toString();
    }

    /**
     * Get XML validation error message, or null if valid.
     */
    public static String getValidationError(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return "XML string is empty";
        }

        try {
            DocumentHelper.parseText(xml.trim());
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static class XmlRegion {
        final int startIndex;
        final int endIndex;
        final String xmlText;

        XmlRegion(int startIndex, int endIndex, String xmlText) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.xmlText = xmlText;
        }
    }

    private static XmlRegion findFirstXmlRegion(String text, int searchFrom) {
        if (text == null || searchFrom >= text.length()) {
            return null;
        }

        // Check if there is an XML declaration <?xml
        int xmlDeclStart = text.indexOf("<?xml", searchFrom);
        if (xmlDeclStart >= 0) {
            int declEnd = text.indexOf("?>", xmlDeclStart);
            if (declEnd > xmlDeclStart) {
                Matcher m = TAG_START_PATTERN.matcher(text);
                if (m.find(declEnd + 2)) {
                    int tagStart = m.start();
                    String tagName = m.group(1);
                    XmlRegion region = findMatchingXmlEnd(text, tagStart, tagName, xmlDeclStart);
                    if (region != null) {
                        return region;
                    }
                }
            }
        }

        // Search for any starting tag
        Matcher m = TAG_START_PATTERN.matcher(text);
        int pos = searchFrom;
        while (m.find(pos)) {
            int tagStart = m.start();
            String tagName = m.group(1);
            XmlRegion region = findMatchingXmlEnd(text, tagStart, tagName, tagStart);
            if (region != null) {
                return region;
            }
            pos = tagStart + 1;
        }

        return null;
    }

    private static XmlRegion findMatchingXmlEnd(String text, int tagStart, String tagName, int actualStart) {
        String closeTag = "</" + tagName + ">";
        int closeIdx = text.indexOf(closeTag, tagStart);
        while (closeIdx >= tagStart) {
            int endIdx = closeIdx + closeTag.length();
            String candidate = text.substring(actualStart, endIdx);
            if (isValidXml(candidate)) {
                return new XmlRegion(actualStart, endIdx, candidate);
            }
            closeIdx = text.indexOf(closeTag, closeIdx + 1);
        }

        int selfClose = text.indexOf("/>", tagStart);
        if (selfClose > tagStart) {
            int endIdx = selfClose + 2;
            String candidate = text.substring(actualStart, endIdx);
            if (isValidXml(candidate)) {
                return new XmlRegion(actualStart, endIdx, candidate);
            }
        }

        return null;
    }
}
