package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.repository.ParsingConfigRepository;
import com.seeloggyplus.repository.impl.ParsingConfigRepositoryImpl;
import com.seeloggyplus.service.ParsingConfigService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParsingConfigServiceImpl implements ParsingConfigService {

    private final ParsingConfigRepository parsingConfigRepository;

    public ParsingConfigServiceImpl() {
        this(new ParsingConfigRepositoryImpl());
    }

    public ParsingConfigServiceImpl(ParsingConfigRepository parsingConfigRepository) {
        this.parsingConfigRepository = parsingConfigRepository;
    }

    @Override
    public Optional<ParsingConfig> findById(String id) {
        return parsingConfigRepository.findById(id);
    }

    @Override
    public List<ParsingConfig> findAll() {
        return parsingConfigRepository.findAll();
    }

    @Override
    public void save(ParsingConfig config) {
        config.setId(UUID.randomUUID().toString());
        parsingConfigRepository.save(config);
    }

    @Override
    public void update(ParsingConfig config) {
        parsingConfigRepository.update(config);
    }

    @Override
    public void delete(ParsingConfig config) {
        parsingConfigRepository.delete(config);
    }

    @Override
    public Optional<ParsingConfig> findDefault() {
        return parsingConfigRepository.findDefault();
    }

    @Override
    public ParsingConfig detectLogFormat(List<String> sampleLines) {
        if (sampleLines == null || sampleLines.isEmpty()) {
            return null;
        }

        // Filter and find the first usable line
        String sample = sampleLines.stream()
                .filter(l -> l != null && !l.trim().isEmpty())
                .findFirst()
                .orElse(null);

        if (sample == null)
            return null;

        StringBuilder regexBuilder = new StringBuilder();
        String detectedTimestampFormat = null;

        // --- 1. Define Timestamp Patterns (Specific to General) ---

        // 1.1 Epoch (High Priority due to numeric nature, handled carefully)
        // 10 digits (seconds), 13 (millis), 16 (micros), 19 (nanos)
        // Also supports decimal seconds: 1735726530.123
        String epochPattern = "(?:\\d{10}|\\d{13}|\\d{16}|\\d{19}|\\d{10}\\.\\d{3,9})";

        // 1.2 ISO 8601 Extended / Cloud / Container
        // 2025-01-01T10:15:30.123456Z, 2025-01-01 10:15:30.123+07:00
        // Robust pattern: YYYY-MM-DD[T ]HH:MM:SS[.nanos][Z|Offset]
        // Note: We use a non-capturing group for the separator to allow 'T' or space
        String isoExtended = "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+\\-]\\d{2}(?::?\\d{2})?)?";

        // 1.3 Web Server / Network
        // Apache/Nginx: 01/Jan/2025:10:15:30 +0700
        String apacheCommon = "\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2}(?:\\s+[+\\-]\\d{4})?";
        // Apache Error: [Wed Jan 01 10:15:30.123456 2025] - Pattern is inside brackets
        // usually
        String apacheError = "[A-Za-z]{3}\\s+[A-Za-z]{3}\\s+\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\s+\\d{4}";

        // 1.4 Syslog Variants
        // RFC 3164 (No year): Jan 1 10:15:30
        String syslogRFC3164 = "[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}";
        // RFC 5424 is covered by ISO Extended usually, but ensures no year-less
        // confusion
        // BSD Variant: Tue Jan 1 10:15:30 2025
        String syslogBSD = "[A-Za-z]{3}\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\s+\\d{4}";

        // 1.5 Database / Vendor Specific
        // Oracle: 01-JAN-25 10.15.30.123456 AM or 01-JAN-2025 22:15:30
        String oracle = "\\d{2}-[A-Za-z]{3}-\\d{2,4}\\s+\\d{2}[.:]\\d{2}[.:]\\d{2}(?:[.:]\\d+)?(?:\\s+(?:AM|PM))?";
        // Postgres: 2025-01-01 10:15:30.123 UTC/CET (ISO like but with named timezone
        // suffix)
        // Cisco: Jan 1 2025 10:15:30
        String cisco = "[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}";

        // 1.6 Locale / Region (US, EU, Asian)
        // US: 01/01/2025 10:15:30 AM
        String usDate = "\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}:\\d{2}(?:\\s+(?:AM|PM))?";
        // EU: 01.01.2025 10:15:30 or 01-01-2025...
        String euDate = "\\d{2}[.-]\\d{2}[.-]\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}";
        // Dot separated (User request): 2023.12.01
        String dotDate = "\\d{4}\\.\\d{2}\\.\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?";

        // 1.7 Compact / Mainframe
        // Compact: 20250101101530 (14 digits)
        // Be careful not to match random large numbers. Requires boundaries or logic.
        String compactDate = "\\d{14}";

        // 1.8 Weird / Legacy / Time Only
        // Time Only: 10:15:30 or 10:15:30,123
        String timeOnly = "\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3})?";
        // RFC 1123: Fri, 01 Dec 2023 10:00:00 GMT
        String rfc1123 = "[A-Za-z]{3},\\s+\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+[A-Z]{3}";
        // ANSI C: Fri Dec 1 10:00:00 2023
        // Updated to support fractional seconds for Apache Error logs compatibility
        String ansiC = "[A-Za-z]{3}\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\s+\\d{4}";

        // --- 2. Construct Search Regex ---
        // Priority Order:
        // 1. Epoch (Very specific numeric patterns)
        // 2. ISO Extended (Most common modern standard)
        // 3. Specific Vendor/RFC formats (Apache, Syslog, Web, DB)
        // 4. Compact/Locale/TimeOnly (Fallbacks)

        String timestampSearchPattern = isoExtended + // High confidence matches naturally
                "|" + apacheCommon +
                "|" + rfc1123 +
                "|" + ansiC +
                "|" + oracle +
                "|" + cisco +
                "|" + syslogBSD +
                "|" + syslogRFC3164 + // Shorter, keep after longer BSD/Ansi
                "|" + usDate +
                "|" + euDate +
                "|" + dotDate +
                "|" + "\\d{14}" + // Compact 14
                "|" + epochPattern + // Epoch (can overlap with random numbers, check context)
                "|" + timeOnly;

        Pattern tsPattern = Pattern.compile(timestampSearchPattern);
        Matcher tsMatcher = tsPattern.matcher(sample);

        if (tsMatcher.find()) {
            int start = tsMatcher.start();
            int end = tsMatcher.end();

            String foundTimestamp = sample.substring(start, end);
            String before = sample.substring(0, start);
            String after = sample.substring(end);

            // --- 3. Refine Logic based on matched content ---

            // Check for brackets wrapping the timestamp
            boolean isBracketed = isBracketed(start, end, sample);
            if (isBracketed) {
                before = sample.substring(0, start - 1);
                after = sample.substring(end + 1);
            }

            // Determine Regex and Format Key
            String tsGroupRegex = null;

            // 3.1 Epoch Check
            if (foundTimestamp.matches("^" + epochPattern + "$") && !foundTimestamp.contains(":")) {
                // Likely epoch. Check digits.
                // If it looks like a compact date (14 digits starting with 20...), handle
                // separately
                if (foundTimestamp.length() == 14
                        && (foundTimestamp.startsWith("20") || foundTimestamp.startsWith("19"))) {
                    tsGroupRegex = "\\d{14}";
                    detectedTimestampFormat = "yyyyMMddHHmmss";
                } else {
                    tsGroupRegex = "\\d+(?:\\.\\d+)?"; // Generic number capture
                    detectedTimestampFormat = "Epoch (Unix Timestamp)";
                }
            }
            // 3.2 ISO Extended Check
            else if (foundTimestamp.matches("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}.*")) {
                // Construct strict regex based on separators found
                String sep = foundTimestamp.contains("T") ? "T" : "\\s+";
                String timezonePart = "(?:Z|[+\\-]\\d{2}:?\\d{2})?";
                String fracPart = "(?:[.,]\\d{1,9})?";

                tsGroupRegex = "\\d{4}-\\d{2}-\\d{2}" + sep + "\\d{2}:\\d{2}:\\d{2}" + fracPart + timezonePart;

                // Guess Java Format
                detectedTimestampFormat = "yyyy-MM-dd" + (sep.equals("T") ? "'T'" : " ") + "HH:mm:ss";
                if (foundTimestamp.matches(".*[.,]\\d+.*"))
                    detectedTimestampFormat += ".SSS";
                // Check for Z or +HH:mm / -HH:mm at the END or T...Z
                // Avoid matching YYYY-MM-DD hyphens
                if (foundTimestamp.matches(".*(?:Z|[+\\-]\\d{2}(?::?\\d{2})?)$"))
                    detectedTimestampFormat += "XXX";
            }
            // 3.3 Apache Common
            else if (foundTimestamp.matches("\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2}.*")) {
                tsGroupRegex = "\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2}(?:\\s+[+\\-]\\d{4})?";
                detectedTimestampFormat = "dd/MMM/yyyy:HH:mm:ss Z";
            }
            // 3.4 Apache Error / ANSI C / Syslog BSD / RFC1123
            // These allow spaces in date parts e.g. "Dec 1"
            else if (foundTimestamp.matches("[A-Za-z]{3}\\s+.*")) {
                if (foundTimestamp.contains(",")) { // RFC 1123
                    tsGroupRegex = "[A-Za-z]{3},\\s+\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+[A-Z]{3}";
                    detectedTimestampFormat = "EEE, dd MMM yyyy HH:mm:ss zzz";
                } else if (foundTimestamp.matches(".*\\d{4}$")) { // Ends in year (ANSI C / BSD / Apache Error)
                    tsGroupRegex = "[A-Za-z]{3}\\s+(?:[A-Za-z]{3}\\s+)?\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\s+\\d{4}";
                    detectedTimestampFormat = "EEE MMM dd HH:mm:ss yyyy"; // Generic guess
                } else { // RFC 3164 (No year)
                    tsGroupRegex = "[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}";
                    detectedTimestampFormat = "MMM dd HH:mm:ss";
                }
            }
            // 3.5 Oracle / Cisco
            else if (foundTimestamp.matches("\\d{2}-[A-Za-z]{3}.*")
                    || foundTimestamp.matches("[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{4}.*")) {
                tsGroupRegex = ".*"; // Simplify for complex vendor strings to greedy match until known delimiter if
                                     // hard
                // Better specific regex:
                if (foundTimestamp.contains("-")) {
                    tsGroupRegex = "\\d{2}-[A-Za-z]{3}-\\d{2,4}\\s+\\d{2}[.:]\\d{2}[.:]\\d{2}(?:[.:]\\d+)?(?:\\s+(?:AM|PM))?";
                    detectedTimestampFormat = "dd-MMM-yyyy HH:mm:ss";
                } else {
                    tsGroupRegex = "[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}"; // Cisco
                    detectedTimestampFormat = "MMM dd yyyy HH:mm:ss";
                }
            }
            // 3.6 US / EU / Dot
            else if (foundTimestamp.contains("/")) {
                // US vs EU: If first part > 12, definitely EU. Else ambiguous.
                tsGroupRegex = "\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}:\\d{2}(?:\\s+(?:AM|PM))?";
                detectedTimestampFormat = "MM/dd/yyyy HH:mm:ss"; // Assume US default
            } else if (foundTimestamp.contains(".")) {
                if (foundTimestamp.matches("\\d{4}\\.\\d{2}\\.\\d{2}.*")) {
                    tsGroupRegex = "\\d{4}\\.\\d{2}\\.\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?";
                    detectedTimestampFormat = "yyyy.MM.dd HH:mm:ss";
                } else {
                    // EU with dots
                    tsGroupRegex = "\\d{2}\\.\\d{2}\\.\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}";
                    detectedTimestampFormat = "dd.MM.yyyy HH:mm:ss";
                }
            }
            // 3.7 Compact 14 Digits
            else if (foundTimestamp.matches("\\d{14}")) {
                tsGroupRegex = "\\d{14}";
                detectedTimestampFormat = "yyyyMMddHHmmss";
            }
            // 3.8 Time Only Fallback
            else {
                tsGroupRegex = "\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3})?";
                detectedTimestampFormat = "HH:mm:ss.SSS";
            }

            // Build Final Regex
            regexBuilder.append(buildBeforeRegex(before));

            if (isBracketed) {
                regexBuilder.append("\\[(?<timestamp>" + tsGroupRegex + ")\\]");
            } else {
                regexBuilder.append("(?<timestamp>" + tsGroupRegex + ")");
            }

            // Delimiter handling
            regexBuilder.append(determineSeparatorPattern(after)); // e.g. \s+, |, etc.

            // After handling
            regexBuilder.append(buildAfterRegex(after, isBracketed));

        } else {
            // Fallback
            if (sample.trim().startsWith("{") && sample.trim().endsWith("}")) {
                return new ParsingConfig("JSON Log", "(?<json>.*)");
            }
            return new ParsingConfig("Generic Log", "(?<message>.*)");
        }

        ParsingConfig config = new ParsingConfig("Auto-Detected", regexBuilder.toString());
        config.setTimestampFormat(detectedTimestampFormat);
        config.setDescription("Auto-detected via Comprehensive Analysis");
        config.validatePattern();

        return config;
    }

    // --- Helper Methods ---

    private boolean isBracketed(int start, int end, String sample) {
        return start > 0 && sample.charAt(start - 1) == '[' &&
                end < sample.length() && sample.charAt(end) == ']';
    }

    private String determineSeparatorPattern(String after) {
        // If immediately followed by pipe or specific chars, allow zero whitespace
        if (after.startsWith("|") || after.startsWith(","))
            return "\\s*";
        // Default to flexible whitespace
        return "\\s*";
    }

    private String buildBeforeRegex(String before) {
        StringBuilder sb = new StringBuilder();
        if (!before.trim().isEmpty()) {
            String levelPattern = "(?:INFO|WARN|ERROR|DEBUG|TRACE|FATAL|SEVERE|FINE|NOTICE|CRIT|ALERT|EMERG)";
            // Check for strict Level match
            if (before.trim().matches(levelPattern)) {
                sb.append("(?<level>" + levelPattern + ")\\s+");
            } else {
                sb.append("(?<prefix>.*?)\\s*");
            }
        }
        return sb.toString();
    }

    // Simplifed After Regex builder
    private String buildAfterRegex(String after, boolean wasBracketed) {
        StringBuilder sb = new StringBuilder();
        String remaining = after.trim();

        // 1. Timezone cleanup (if not consumed by timestamp regex)
        // Check for +0700 or Z *separated* from timestamp
        if (remaining.matches("^[+\\-]\\d{4}.*") || remaining.equals("Z") || remaining.startsWith("Z ")) {
            sb.append("(?<timezone>Z|[+\\-]\\d{4})\\s*");
            remaining = remaining.replaceFirst("^(Z|[+\\-]\\d{4})\\s*", "");

            // Handle potential closing bracket if timezone was inside
            if (remaining.startsWith("]") && !wasBracketed) {
                sb.append("\\]\\s*");
                remaining = remaining.substring(1).trim();
            }
        } else if (remaining.matches("^[A-Z]{3,4}\\s+.*") || remaining.matches("^[A-Z]{3,4}$")) {
            // Timezone text like UTC, CET
            sb.append("(?<timezone>[A-Z]{3,4})\\s*");
            remaining = remaining.replaceFirst("^[A-Z]{3,4}\\s*", "");
        }

        // 2. Separators
        if (remaining.startsWith("|-")) {
            sb.append("\\|-");
            remaining = remaining.substring(2).trim();
        } else if (remaining.startsWith("-")) {
            sb.append("-\\s+");
            remaining = remaining.substring(1).trim();
        } else if (remaining.startsWith("|")) {
            sb.append("\\|\\s+");
            remaining = remaining.substring(1).trim();
        }

        // 3. Level Detection
        String levelPattern = "(?:INFO|WARN|ERROR|DEBUG|TRACE|FATAL|SEVERE|FINE|NOTICE|CRIT|ALERT|EMERG)";
        // Simple heuristic: If the start of remaining looks like a level
        Matcher lvlM = Pattern.compile("^" + levelPattern).matcher(remaining);
        if (lvlM.find()) {
            sb.append("(?<level>" + levelPattern + ")\\s+");
            remaining = remaining.substring(lvlM.end()).trim();
        } else {
            // Bracketed level? [INFO]
            lvlM = Pattern.compile("^\\[" + levelPattern + "\\]").matcher(remaining);
            if (lvlM.find()) {
                sb.append("\\[(?<level>" + levelPattern + ")\\]\\s+");
                remaining = remaining.substring(lvlM.end()).trim();
            }
        }

        // 4. Logback "in Context" pattern (restored)
        // Matches "in ch.qos.logback.classic.LoggerContext[default]"
        if (remaining.startsWith("in ")) {
            int dashIdx = remaining.indexOf(" - ");
            if (dashIdx > 0) {
                sb.append("in\\s+(?<context>.*?)\\s+-\\s+");
                remaining = remaining.substring(dashIdx + 3).trim();
            }
        }

        // 5. Thread / Context (Generic bracket capture if present)
        if (remaining.startsWith("[")) {
            // Look for closing bracket
            int closeIdx = remaining.indexOf("]");
            if (closeIdx > 0) {
                sb.append("\\[(?<thread>[^\\]]+)\\]\\s+");
                remaining = remaining.substring(closeIdx + 1).trim();
            }
        }

        // 6. Message
        sb.append("(?<message>.*)");

        return sb.toString();
    }
}
