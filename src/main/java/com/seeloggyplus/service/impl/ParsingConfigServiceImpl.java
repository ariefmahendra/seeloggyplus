package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.repository.ParsingConfigRepository;
import com.seeloggyplus.repository.impl.ParsingConfigRepositoryImpl;
import com.seeloggyplus.service.ParsingConfigService;
import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link ParsingConfigService}.
 * <p>
 * Manages parsing configurations and provides intelligent log format detection
 * using the Krakens Grok library.
 */
public class ParsingConfigServiceImpl implements ParsingConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigServiceImpl.class);

    private final ParsingConfigRepository parsingConfigRepository;
    private final GrokCompiler grokCompiler;
    private final Map<String, String> candidatePatterns;
    private final Map<String, String> predefinedRegexes;

    /**
     * Default constructor.
     * Initializes the service with a default {@link ParsingConfigRepositoryImpl}.
     */
    public ParsingConfigServiceImpl() {
        this(new ParsingConfigRepositoryImpl());
    }

    /**
     * Constructor for dependency injection.
     *
     * @param parsingConfigRepository The repository to use.
     */
    public ParsingConfigServiceImpl(ParsingConfigRepository parsingConfigRepository) {
        this.parsingConfigRepository = parsingConfigRepository;
        this.grokCompiler = GrokCompiler.newInstance();
        this.grokCompiler.registerDefaultPatterns();
        this.candidatePatterns = new LinkedHashMap<>();
        this.predefinedRegexes = new LinkedHashMap<>();

        initializePatterns();
    }

    /**
     * Initializes Grok patterns and Regex mappings.
     */
    private void initializePatterns() {
        // --- 1. Register Helper Patterns for Detection ---
        this.grokCompiler.register("EPOCH", "\\d{10}|\\d{13}|\\d{10}\\.\\d+");
        this.grokCompiler.register("COMPACT", "\\d{14}");
        this.grokCompiler.register("ISO8601_LOOSE",
                "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+\\-]\\d{2}(?::?\\d{2})?)?");
        this.grokCompiler.register("LOGBACK_PREFIX", "(?:\\|-|\\| |-)");

        // --- 2. Initialize Detection Candidates (Grok Patterns) ---
        // Use LinkedHashMap to preserve priority order

        candidatePatterns.put("Compact", "%{COMPACT:ts}\\s+%{GREEDYDATA}");
        candidatePatterns.put("Logback",
                "%{ISO8601_LOOSE:ts}\\s+%{LOGBACK_PREFIX}%{LOGLEVEL}\\s+in\\s+%{DATA}\\s+-\\s+%{GREEDYDATA}");
        candidatePatterns.put("ISO8601 Standard", "%{ISO8601_LOOSE:ts}\\s+%{LOGLEVEL}\\s+%{GREEDYDATA}");
        candidatePatterns.put("ISO8601 Extended", "%{ISO8601_LOOSE:ts}\\s+%{GREEDYDATA}");

        // Web Logic
        candidatePatterns.put("Apache Common",
                "%{IPORHOST}\\s+%{NOTSPACE}\\s+%{NOTSPACE}\\s+\\[%{HTTPDATE:ts}\\]\\s+\"%{DATA}\"%{GREEDYDATA}");
        candidatePatterns.put("Apache Combined",
                "%{IPORHOST}\\s+%{NOTSPACE}\\s+%{NOTSPACE}\\s+\\[%{HTTPDATE:ts}\\]\\s+\"%{DATA}\"\\s+%{NUMBER}\\s+(?:%{NUMBER}|-)\\s+\"%{DATA}\"\\s+\"%{DATA}\"");
        candidatePatterns.put("Apache Error",
                "\\[%{DAY} %{MONTH} %{MONTHDAY} %{TIME} %{YEAR}\\]\\s+\\[%{WORD}\\]\\s+%{GREEDYDATA}");

        // Syslog Variants
        candidatePatterns.put("Syslog BSD",
                "^%{DAY}\\s+%{MONTH}\\s+%{MONTHDAY}\\s+%{TIME}\\s+%{YEAR}\\s+%{GREEDYDATA}");
        candidatePatterns.put("Syslog",
                "%{SYSLOGTIMESTAMP:ts}\\s+%{SYSLOGHOST}\\s+%{DATA}(?:\\[%{POSINT}\\])?:\\s+%{GREEDYDATA}");

        // Database
        candidatePatterns.put("PostgreSQL",
                "%{DATESTAMP:ts}\\s+%{TZ}\\s+\\[%{NUMBER}\\]:\\s+\\[%{DATA}\\]\\s+%{GREEDYDATA}");
        candidatePatterns.put("Oracle", "\\d{2}-[a-zA-Z]{3}-\\d{4}\\s+%{TIME}\\s+%{GREEDYDATA}");

        // Dates
        candidatePatterns.put("US Date", "%{DATE_US:ts}\\s+%{TIME}\\s+%{GREEDYDATA}");
        candidatePatterns.put("EU Date", "%{DATE_EU:ts}\\s+%{TIME}\\s+%{GREEDYDATA}");
        candidatePatterns.put("Time Only", "%{TIME:ts}\\s+%{GREEDYDATA}");

        // Fallback
        candidatePatterns.put("Epoch", "%{EPOCH:ts}\\s+%{GREEDYDATA}");

        // --- 3. Initialize Safe Regexes (Hybrid Strategy) ---
        String isoRegex = "(?<timestamp>\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+\\-]\\d{2}(?::?\\d{2})?)?)";

        predefinedRegexes.put("Logback",
                isoRegex + "\\s+(?:\\|-|\\| |-)\\s*(?<level>\\w+)\\s+in\\s+(?<context>.*?)\\s+-\\s+(?<message>.*)");
        predefinedRegexes.put("ISO8601 Standard", isoRegex + "\\s+(?<level>\\w+)\\s+(?<message>.*)");
        predefinedRegexes.put("ISO8601 Extended", isoRegex + "\\s+(?<message>.*)");

        predefinedRegexes.put("Apache Common",
                "(?<clientip>[\\d\\.:]+) \\S+ \\S+ \\[(?<timestamp>.*?)\\] \"(?<request>.*?)\" (?<response>\\d+) (?<bytes>\\d+|-)");
        predefinedRegexes.put("Apache Combined",
                "(?<clientip>[\\d\\.:]+) \\S+ \\S+ \\[(?<timestamp>.*?)\\] \"(?<request>.*?)\" (?<response>\\d+) (?<bytes>\\d+|-) \"(?<referrer>.*?)\" \"(?<agent>.*?)\"");
        predefinedRegexes.put("Apache Error", "\\[(?<timestamp>.*?)\\] \\[(?<level>\\w+)\\] (?<message>.*)");

        predefinedRegexes.put("Syslog",
                "(?<timestamp>[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?<logsource>\\S+)\\s+(?<program>.*?)(?:\\[(?<pid>\\d+)\\])?:\\s+(?<message>.*)");
        predefinedRegexes.put("Syslog BSD",
                "(?<timestamp>[A-Za-z]{3}\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}\\s+\\d{4})\\s+(?<message>.*)");

        predefinedRegexes.put("PostgreSQL",
                "(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)\\s+(?<timezone>\\w+|[+\\-]\\d{4})\\s+\\[(?<pid>\\d+)\\]:\\s+\\[(?<session>.*?)\\]\\s+(?<message>.*)");
        predefinedRegexes.put("Oracle",
                "(?<timestamp>\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2})\\s+(?<message>.*)");

        predefinedRegexes.put("US Date",
                "(?<timestamp>\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}:\\d{2}(?:\\s+(?:AM|PM))?)\\s+(?:(?<level>\\w+)\\s+)?(?<message>.*)");
        predefinedRegexes.put("EU Date",
                "(?<timestamp>\\d{1,2}[.-]\\d{1,2}[.-]\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?:(?<level>\\w+)\\s+)?(?<message>.*)");

        predefinedRegexes.put("Time Only",
                "(?<timestamp>\\d{1,2}:\\d{2}:\\d{2}(?:[.,]\\d+)?)\\s+(?:(?<level>\\w+)\\s+)?(?<message>.*)");
        predefinedRegexes.put("Epoch", "(?<timestamp>\\d{10}|\\d{13}|\\d{10}\\.\\d+)\\s+(?<message>.*)");
        predefinedRegexes.put("Compact", "(?<timestamp>\\d{14})\\s+(?:(?<level>\\w+)\\s+)?(?<message>.*)");
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
        if (config.getId() == null) {
            config.setId(UUID.randomUUID().toString());
        }
        parsingConfigRepository.save(config);
        logger.debug("Saved parsing config: {}", config.getName());
    }

    @Override
    public void update(ParsingConfig config) {
        parsingConfigRepository.update(config);
        logger.debug("Updated parsing config: {}", config.getName());
    }

    @Override
    public void delete(ParsingConfig config) {
        parsingConfigRepository.delete(config);
        logger.debug("Deleted parsing config: {}", config.getName());
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

        String sample = sampleLines.stream()
                .filter(l -> l != null && !l.trim().isEmpty())
                .findFirst()
                .orElse(null);

        if (sample == null)
            return null;

        for (Map.Entry<String, String> entry : candidatePatterns.entrySet()) {
            String formatName = entry.getKey();
            String grokPattern = entry.getValue();

            try {
                Grok grok = grokCompiler.compile(grokPattern);
                Match match = grok.match(sample);
                Map<String, Object> capture = match.capture();

                if (capture != null && !capture.isEmpty()) {
                    String safeRegex = predefinedRegexes.get(formatName);
                    if (safeRegex == null) {
                        safeRegex = grok.getNamedRegex();
                    }

                    ParsingConfig config = new ParsingConfig("Auto-Detected (" + formatName + ")", safeRegex);
                    config.setDescription("Detected using Grok Pattern: " + grokPattern);

                    String tsVal = extractTimestampValue(capture);
                    String detectedTimestampFormat = guessTimestampFormat(formatName, tsVal);
                    config.setTimestampFormat(detectedTimestampFormat);

                    try {
                        config.validatePattern();
                        logger.info("Auto-detected log format: {}", formatName);
                        return config;
                    } catch (Exception e) {
                        logger.warn("Detected format {} but validation failed", formatName);
                        continue;
                    }
                }
            } catch (Exception e) {
                // Ignore compilation errors for patterns
            }
        }

        if (sample.trim().startsWith("{") && sample.trim().endsWith("}")) {
            return new ParsingConfig("JSON Log", "(?<json>.*)");
        }

        return new ParsingConfig("Generic Log", "(?<message>.*)");
    }

    private String extractTimestampValue(Map<String, Object> capture) {
        if (capture.get("ts") != null) return capture.get("ts").toString();
        if (capture.get("timestamp") != null) return capture.get("timestamp").toString();
        return "";
    }

    private String guessTimestampFormat(String formatName, String tsVal) {
        if (tsVal == null)
            tsVal = "";

        if (formatName.equals("Epoch"))
            return "Epoch (Unix Timestamp)";
        if (formatName.equals("Compact"))
            return "yyyyMMddHHmmss";

        if (formatName.contains("Logback")) {
            if (tsVal.matches(".*[.,]\\d{3}.*"))
                return "yyyy-MM-dd HH:mm:ss.SSS";
            return "yyyy-MM-dd HH:mm:ss";
        }
        if (formatName.contains("ISO8601") || formatName.contains("PostgreSQL")) {
            boolean hasT = tsVal.contains("T");
            boolean hasFrac = tsVal.matches(".*[.,]\\d+.*");
            boolean hasTZ = tsVal.endsWith("Z") || tsVal.matches(".*[+\\-]\\d{2}:?\\d{2}$");

            String base = hasT ? "yyyy-MM-dd'T'HH:mm:ss" : "yyyy-MM-dd HH:mm:ss";
            if (hasFrac)
                base += ".SSS";
            if (hasTZ)
                base += "XXX";
            return base;
        }

        if (formatName.contains("Apache Common") || formatName.contains("Apache Combined"))
            return "dd/MMM/yyyy:HH:mm:ss Z";

        if (formatName.contains("Apache Error")) {
            if (tsVal.matches(".*\\d{2}:\\d{2}:\\d{2}\\.\\d+.*"))
                return "EEE MMM dd HH:mm:ss.SSS yyyy";
            return "EEE MMM dd HH:mm:ss yyyy";
        }

        if (formatName.equals("Syslog"))
            return "MMM dd HH:mm:ss";
        if (formatName.equals("Syslog BSD"))
            return "EEE MMM dd HH:mm:ss yyyy";
        if (formatName.equals("Oracle"))
            return "dd-MMM-yyyy HH:mm:ss";
        if (formatName.equals("US Date"))
            return "MM/dd/yyyy HH:mm:ss";
        if (formatName.equals("EU Date"))
            return "dd.MM.yyyy HH:mm:ss";
        if (formatName.equals("Time Only")) {
            if (tsVal.matches(".*[.,]\\d+.*"))
                return "HH:mm:ss.SSS";
            return "HH:mm:ss";
        }

        return null; // Let user define
    }
}
