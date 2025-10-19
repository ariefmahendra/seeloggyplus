package com.seeloggyplus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Service for prettifying and formatting XML strings
 * Provides XML validation, formatting, and minification
 */
public class XmlPrettifyService {

    private static final Logger logger = LoggerFactory.getLogger(XmlPrettifyService.class);

    /**
     * Prettify XML string with indentation
     */
    public static String prettify(String xml) {
        return prettify(xml, 2);
    }

    /**
     * Prettify XML with custom indentation
     */
    public static String prettify(String xml, int indent) {
        if (xml == null || xml.trim().isEmpty()) {
            return xml;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            return writer.toString();
        } catch (Exception e) {
            logger.warn("Invalid XML or error formatting: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Minify XML string (remove unnecessary whitespace)
     */
    public static String minify(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return xml;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            return writer.toString().replaceAll(">\\s+<", "><").trim();
        } catch (Exception e) {
            logger.warn("Invalid XML or error minifying: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Validate if string is valid XML
     */
    public static boolean isValidXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return false;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract XML from a log message
     * Attempts to find and extract XML from text
     */
    public static String extractXml(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // Look for XML declaration
        int xmlStart = text.indexOf("<?xml");
        if (xmlStart >= 0) {
            int xmlEnd = findXmlEnd(text, xmlStart);
            if (xmlEnd > xmlStart) {
                String xmlCandidate = text.substring(xmlStart, xmlEnd);
                if (isValidXml(xmlCandidate)) {
                    return xmlCandidate;
                }
            }
        }

        // Look for root element (common XML tags)
        String[] commonRootTags = {"<root", "<response", "<request", "<data", "<message", "<xml", "<document", "<config"};

        for (String rootTag : commonRootTags) {
            xmlStart = text.indexOf(rootTag);
            if (xmlStart >= 0) {
                int xmlEnd = findXmlEnd(text, xmlStart);
                if (xmlEnd > xmlStart) {
                    String xmlCandidate = text.substring(xmlStart, xmlEnd);
                    if (isValidXml(xmlCandidate)) {
                        return xmlCandidate;
                    }
                }
            }
        }

        // Try to find any XML-like structure
        xmlStart = text.indexOf('<');
        if (xmlStart >= 0) {
            int xmlEnd = text.lastIndexOf('>');
            if (xmlEnd > xmlStart) {
                String xmlCandidate = text.substring(xmlStart, xmlEnd + 1);
                if (isValidXml(xmlCandidate)) {
                    return xmlCandidate;
                }
            }
        }

        return null;
    }

    /**
     * Find the end of an XML document starting from a given position
     */
    private static int findXmlEnd(String text, int startPos) {
        try {
            // Find the root element tag name
            int tagStart = text.indexOf('<', startPos);
            if (tagStart < 0) return -1;

            // Skip <?xml declaration if present
            if (text.startsWith("<?xml", tagStart)) {
                tagStart = text.indexOf('<', tagStart + 5);
                if (tagStart < 0) return -1;
            }

            // Skip comments
            while (text.startsWith("<!--", tagStart)) {
                int commentEnd = text.indexOf("-->", tagStart);
                if (commentEnd < 0) return -1;
                tagStart = text.indexOf('<', commentEnd + 3);
                if (tagStart < 0) return -1;
            }

            // Get root element name
            int tagNameEnd = tagStart + 1;
            while (tagNameEnd < text.length() &&
                   !Character.isWhitespace(text.charAt(tagNameEnd)) &&
                   text.charAt(tagNameEnd) != '>' &&
                   text.charAt(tagNameEnd) != '/') {
                tagNameEnd++;
            }

            String rootTagName = text.substring(tagStart + 1, tagNameEnd);
            String closeTag = "</" + rootTagName + ">";

            // Find closing tag
            int depth = 0;
            int pos = tagStart;

            while (pos < text.length()) {
                int nextOpen = text.indexOf("<" + rootTagName, pos);
                int nextClose = text.indexOf(closeTag, pos);

                // Check for self-closing tag
                if (nextClose < 0) {
                    int selfClose = text.indexOf("/>", tagStart);
                    if (selfClose > tagStart && selfClose < text.length()) {
                        return selfClose + 2;
                    }
                    return -1;
                }

                if (nextOpen >= 0 && nextOpen < nextClose) {
                    // Check if it's actually an opening tag
                    int afterTag = nextOpen + rootTagName.length() + 1;
                    if (afterTag < text.length() &&
                        (Character.isWhitespace(text.charAt(afterTag)) ||
                         text.charAt(afterTag) == '>' ||
                         text.charAt(afterTag) == '/')) {
                        depth++;
                        pos = nextOpen + 1;
                    } else {
                        pos = nextOpen + 1;
                    }
                } else {
                    if (depth == 0) {
                        return nextClose + closeTag.length();
                    }
                    depth--;
                    pos = nextClose + closeTag.length();
                }
            }
        } catch (Exception e) {
            logger.debug("Error finding XML end: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Format and prettify XML from log message
     * Extracts XML and returns prettified version
     */
    public static String prettifyFromLog(String logMessage) {
        String xml = extractXml(logMessage);
        if (xml != null) {
            return prettify(xml);
        }
        return logMessage;
    }

    /**
     * Check if log message contains XML
     */
    public static boolean containsXml(String text) {
        return extractXml(text) != null;
    }

    /**
     * Get XML validation error message
     */
    public static String getValidationError(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return "XML string is empty";
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(xml)));
            return null; // Valid XML
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
