package com.seeloggyplus.web.controller;

import com.seeloggyplus.model.*;
import com.seeloggyplus.repository.LogFileRepository;
import com.seeloggyplus.service.*;
import com.seeloggyplus.service.impl.TailServiceImpl;
import com.seeloggyplus.util.IntArrayList;
import com.seeloggyplus.util.LineOffsetIndex;
import com.seeloggyplus.util.MappedFileReader;
import com.seeloggyplus.web.dto.ApiResponse;
import com.seeloggyplus.web.dto.LogResponseDto;
import com.seeloggyplus.web.dto.OpenLogRequest;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LogApiController {

    private static final Logger logger = LoggerFactory.getLogger(LogApiController.class);

    private final LogParser logParser;
    private final ParsingConfigService parsingConfigService;
    private final SSHService sshService;
    private final ServerManagementService serverService;
    private final RecentFileService recentFileService;
    private final LogFileRepository logFileRepository;

    public static class LogSession implements AutoCloseable {
        public String id;
        public String filePath;
        public String fileName;
        public String source;
        public String serverId;
        public ParsingConfig config;
        public RandomAccessFile raf;
        public LineOffsetIndex index;
        public MappedFileReader reader;
        public List<LogEntry> entries = new CopyOnWriteArrayList<>();
        public AtomicLong currentLineNumber = new AtomicLong(0);
        public TailService tailService;
        public Set<SseClient> sseClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
        public final Map<String, IntArrayList> filterCache = Collections.synchronizedMap(
                new LinkedHashMap<String, IntArrayList>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, IntArrayList> eldest) {
                        return size() > 10;
                    }
                }
        );


        public int getTotalLineCount() {
            if (index != null) {
                return index.getLineCount() + entries.size();
            }
            return entries.size();
        }

        public String getRawLine(int lineIdx) {
            if (index != null && reader != null) {
                if (lineIdx < index.getLineCount()) {
                    return reader.readLine(index, lineIdx);
                }
                int tailIdx = lineIdx - index.getLineCount();
                if (tailIdx >= 0 && tailIdx < entries.size()) {
                    LogEntry e = entries.get(tailIdx);
                    return e.getRawLog() != null ? e.getRawLog() : e.getMessage();
                }
                return "";
            }
            if (lineIdx >= 0 && lineIdx < entries.size()) {
                LogEntry e = entries.get(lineIdx);
                return e.getRawLog() != null ? e.getRawLog() : e.getMessage();
            }
            return "";
        }

        @Override
        public void close() {
            if (tailService != null) {
                try { tailService.stopTail(); } catch (Exception ignored) {}
            }
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (raf != null) {
                try { raf.close(); } catch (Exception ignored) {}
            }
            filterCache.clear();
        }
    }

    private final Map<String, LogSession> activeSessions = new ConcurrentHashMap<>();

    public LogApiController(LogParser logParser, ParsingConfigService parsingConfigService,
                            SSHService sshService, ServerManagementService serverService,
                            RecentFileService recentFileService, LogFileRepository logFileRepository) {
        this.logParser = logParser;
        this.parsingConfigService = parsingConfigService;
        this.sshService = sshService;
        this.serverService = serverService;
        this.recentFileService = recentFileService;
        this.logFileRepository = logFileRepository;
    }

    public void openLog(Context ctx) {
        try {
            OpenLogRequest req = ctx.bodyAsClass(OpenLogRequest.class);
            if (req.getPath() == null || req.getPath().isBlank()) {
                ctx.json(ApiResponse.error("File path must not be empty"));
                return;
            }

            String sessionId = UUID.randomUUID().toString();
            LogSession session = new LogSession();
            session.id = sessionId;
            session.filePath = req.getPath();
            session.fileName = new File(req.getPath()).getName();
            session.source = req.getSource() != null ? req.getSource().toUpperCase() : "LOCAL";
            session.serverId = req.getServerId();

            List<LogEntry> previewEntries = new ArrayList<>();
            int totalLines = 0;

            if ("REMOTE".equalsIgnoreCase(session.source)) {
                if (session.serverId != null && !session.serverId.isBlank()) {
                    SSHServerModel server = serverService.getServerById(session.serverId);
                    if (server != null && !sshService.isConnected()) {
                        sshService.connect(server.getHost(), server.getPort(), server.getUsername(), server.getPassword());
                    }
                }
                List<String> lines = sshService.readFileLines(session.filePath);
                long lineNum = 1;
                for (String raw : lines) {
                    LogEntry entry = new LogEntry(lineNum++, raw);
                    session.entries.add(entry);
                }
                session.currentLineNumber.set(lineNum);
                totalLines = session.entries.size();
                previewEntries = session.entries.stream().limit(500).collect(Collectors.toList());
            } else {
                File file = new File(session.filePath);
                if (!file.exists()) {
                    ctx.json(ApiResponse.error("File does not exist: " + session.filePath));
                    return;
                }

                // High-performance Memory-Mapped Indexing (handles 177MB+ with zero copy in < 150ms)
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                LineOffsetIndex index = new LineOffsetIndex();
                index.buildIndex(raf, null);
                MappedFileReader reader = new MappedFileReader(file);

                session.raf = raf;
                session.index = index;
                session.reader = reader;
                totalLines = index.getLineCount();
                session.currentLineNumber.set(totalLines);

                int previewLimit = Math.min(500, totalLines);
                for (int i = 0; i < previewLimit; i++) {
                    String raw = reader.readLine(index, i);
                    previewEntries.add(new LogEntry(i + 1, raw));
                }
            }

            activeSessions.put(sessionId, session);

            // Record to recent files history
            try {
                boolean isRemote = "REMOTE".equalsIgnoreCase(session.source);
                LogFile logFile = null;
                try {
                    logFile = logFileRepository.findByPathAndName(session.filePath, session.fileName);
                } catch (Exception notFound) {
                    logFile = null;
                }

                if (logFile == null) {
                    logFile = new LogFile(
                            UUID.randomUUID().toString(),
                            session.fileName,
                            session.filePath,
                            totalLines + " lines",
                            LocalDateTime.now().toString(),
                            isRemote,
                            session.serverId,
                            null
                    );
                    logFileRepository.insert(logFile);
                    logger.info("Created new log_files record: {} (id: {})", session.fileName, logFile.getId());
                }

                RecentFile recentFile = new RecentFile();
                recentFile.setId(UUID.randomUUID().toString());
                recentFile.setFileId(logFile.getId());
                recentFile.setLastOpened(LocalDateTime.now());
                recentFileService.save(logFile, recentFile);
                logger.info("Recorded recent file: {} (fileId: {})", session.filePath, logFile.getId());
            } catch (Exception ex) {
                logger.error("Could not save recent file entry", ex);
            }


            LogResponseDto responseDto = LogResponseDto.builder()
                    .fileId(sessionId)
                    .fileName(session.fileName)
                    .filePath(session.filePath)
                    .source(session.source)
                    .totalLines(totalLines)
                    .filteredLines(totalLines)
                    .entries(previewEntries)
                    .build();

            ctx.json(ApiResponse.ok("File opened successfully", responseDto));
        } catch (Exception e) {
            logger.error("Failed to open log file", e);
            ctx.json(ApiResponse.error("Failed to open log file: " + e.getMessage()));
        }
    }

    public void getLogPage(Context ctx) {
        try {
            String fileId = ctx.queryParam("fileId");
            if (fileId == null || !activeSessions.containsKey(fileId)) {
                ctx.json(ApiResponse.error("Active log session not found."));
                return;
            }

            LogSession session = activeSessions.get(fileId);
            int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
            String search = ctx.queryParam("search");
            String level = ctx.queryParam("level");
            boolean isRegex = ctx.queryParamAsClass("isRegex", Boolean.class).getOrDefault(false);
            boolean caseSensitive = ctx.queryParamAsClass("caseSensitive", Boolean.class).getOrDefault(false);

            int totalLines = session.getTotalLineCount();
            List<LogEntry> pageEntries = new ArrayList<>();
            int totalFiltered = totalLines;

            boolean hasSearch = search != null && !search.isBlank();
            boolean hasLevel = level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level);

            if (session.reader != null && session.index != null) {
                if (!hasSearch && !hasLevel) {
                    // Direct fast range slice via memory mapped index
                    int from = Math.max(0, Math.min(offset, totalLines));
                    int to = Math.min(from + limit, totalLines);
                    for (int i = from; i < to; i++) {
                        String raw = session.getRawLine(i);
                        pageEntries.add(new LogEntry(i + 1, raw));
                    }
                    totalFiltered = totalLines;
                } else {
                    String cacheKey = (search != null ? search.trim() : "") + "||" + (level != null ? level.trim().toUpperCase() : "") + "||" + isRegex + "||" + caseSensitive;
                    IntArrayList matchIndices = session.filterCache.get(cacheKey);
                    if (matchIndices == null) {
                        matchIndices = buildFilterIndex(session, search, level, isRegex, caseSensitive);
                        session.filterCache.put(cacheKey, matchIndices);
                    }

                    totalFiltered = matchIndices.size();
                    int from = Math.max(0, Math.min(offset, totalFiltered));
                    int to = Math.min(from + limit, totalFiltered);
                    for (int i = from; i < to; i++) {
                        int lineIdx = matchIndices.get(i);
                        String raw = session.getRawLine(lineIdx);
                        pageEntries.add(new LogEntry(lineIdx + 1, raw));
                    }
                }
            } else {
                List<LogEntry> source = session.entries;
                if (hasLevel) {
                    Pattern lp = Pattern.compile("\\b" + Pattern.quote(level.toUpperCase()) + "\\b", Pattern.CASE_INSENSITIVE);
                    source = source.stream()
                            .filter(e -> {
                                String raw = e.getRawLog();
                                return raw != null && lp.matcher(raw.substring(0, Math.min(120, raw.length()))).find();
                            })
                            .collect(Collectors.toList());
                }
                if (hasSearch) {
                    if (isRegex) {
                        try {
                            Pattern p = Pattern.compile(search, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
                            source = source.stream()
                                    .filter(e -> e.getRawLog() != null && p.matcher(e.getRawLog()).find())
                                    .collect(Collectors.toList());
                        } catch (Exception ignored) {}
                    } else {
                        String q = caseSensitive ? search : search.toLowerCase();
                        source = source.stream()
                                .filter(e -> {
                                    String raw = e.getRawLog();
                                    return raw != null && (caseSensitive ? raw.contains(q) : raw.toLowerCase().contains(q));
                                })
                                .collect(Collectors.toList());
                    }
                }
                totalFiltered = source.size();
                int from = Math.max(0, Math.min(offset, totalFiltered));
                int to = Math.min(from + limit, totalFiltered);
                pageEntries = source.subList(from, to);
            }

            LogResponseDto responseDto = LogResponseDto.builder()
                    .fileId(session.id)
                    .fileName(session.fileName)
                    .filePath(session.filePath)
                    .source(session.source)
                    .totalLines(totalLines)
                    .filteredLines(totalFiltered)
                    .entries(pageEntries)
                    .build();

            ctx.json(ApiResponse.ok(responseDto));
        } catch (Exception e) {
            logger.error("Failed to get log page", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    private IntArrayList buildFilterIndex(LogSession session, String search, String level, boolean isRegex, boolean caseSensitive) {
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasLevel = level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level);

        Pattern regexPattern = null;
        if (hasSearch && isRegex) {
            try {
                regexPattern = Pattern.compile(search, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception ignored) {}
        }
        Pattern levelPattern = hasLevel ? Pattern.compile("\\b" + Pattern.quote(level.toUpperCase()) + "\\b", Pattern.CASE_INSENSITIVE) : null;
        String plainQuery = hasSearch && !isRegex ? (caseSensitive ? search : search.toLowerCase()) : null;

        int totalLines = session.getTotalLineCount();
        int chunkSize = 25000;
        int numChunks = (totalLines + chunkSize - 1) / chunkSize;

        final Pattern fRegexPattern = regexPattern;
        final Pattern fLevelPattern = levelPattern;
        final String fPlainQuery = plainQuery;

        List<IntArrayList> partials = IntStream.range(0, numChunks).parallel().mapToObj(chunkIdx -> {
            int from = chunkIdx * chunkSize;
            int to = Math.min(from + chunkSize, totalLines);
            IntArrayList chunkList = new IntArrayList(Math.min(1000, to - from));

            for (int i = from; i < to; i++) {
                String raw = session.getRawLine(i);
                if (raw == null || raw.isEmpty()) continue;

                if (fLevelPattern != null) {
                    int checkLen = Math.min(120, raw.length());
                    if (!fLevelPattern.matcher(raw.substring(0, checkLen)).find()) {
                        continue;
                    }
                }

                if (hasSearch) {
                    if (fRegexPattern != null) {
                        if (!fRegexPattern.matcher(raw).find()) continue;
                    } else if (fPlainQuery != null) {
                        String testStr = caseSensitive ? raw : raw.toLowerCase();
                        if (!testStr.contains(fPlainQuery)) continue;
                    }
                }

                chunkList.add(i);
            }
            return chunkList;
        }).collect(Collectors.toList());

        int totalMatches = partials.stream().mapToInt(IntArrayList::size).sum();
        IntArrayList merged = new IntArrayList(totalMatches);
        for (IntArrayList chunkList : partials) {
            for (int i = 0; i < chunkList.size(); i++) {
                merged.add(chunkList.get(i));
            }
        }
        return merged;
    }

    public void handleTailSse(SseClient client) {
        String fileId = client.ctx().queryParam("fileId");
        if (fileId == null || !activeSessions.containsKey(fileId)) {
            client.sendEvent("error", "Session not found");
            client.close();
            return;
        }

        LogSession session = activeSessions.get(fileId);
        session.sseClients.add(client);

        if ("REMOTE".equalsIgnoreCase(session.source)) {
            try {
                if (session.serverId != null && !session.serverId.isBlank() && !sshService.isConnected()) {
                    SSHServerModel server = serverService.getServerById(session.serverId);
                    if (server != null) {
                        sshService.connect(server.getHost(), server.getPort(), server.getUsername(), server.getPassword());
                    }
                }
                sshService.tailFile(session.filePath, 0, rawLog -> {
                    long lineNum = session.currentLineNumber.incrementAndGet();
                    LogEntry entry = new LogEntry(lineNum, rawLog);
                    session.entries.add(entry);

                    for (SseClient c : session.sseClients) {
                        try {
                            c.sendEvent("log", entry);
                        } catch (Exception ex) {
                            session.sseClients.remove(c);
                        }
                    }
                }, err -> logger.error("Remote SSH tail error: {}", err));
            } catch (Exception e) {
                logger.error("Failed to start remote tail", e);
            }
        } else {
            if (session.tailService == null) {
                session.tailService = new TailServiceImpl();
                File f = new File(session.filePath);
                session.tailService.startLocalTail(f, rawLog -> {
                    long lineNum = session.currentLineNumber.incrementAndGet();
                    LogEntry entry = new LogEntry(lineNum, rawLog);
                    session.entries.add(entry);

                    for (SseClient c : session.sseClients) {
                        try {
                            c.sendEvent("log", entry);
                        } catch (Exception ex) {
                            session.sseClients.remove(c);
                        }
                    }
                }, null, false);
            }
        }

        client.onClose(() -> {
            session.sseClients.remove(client);
            if (session.sseClients.isEmpty()) {
                if (session.tailService != null) {
                    session.tailService.stopTail();
                    session.tailService = null;
                }
                if ("REMOTE".equalsIgnoreCase(session.source)) {
                    sshService.stopTailing();
                }
            }
        });
    }

    public void stopTail(Context ctx) {
        String fileId = ctx.pathParam("fileId");
        if (activeSessions.containsKey(fileId)) {
            LogSession session = activeSessions.get(fileId);
            if (session.tailService != null) {
                session.tailService.stopTail();
                session.tailService = null;
            }
            if ("REMOTE".equalsIgnoreCase(session.source)) {
                sshService.stopTailing();
            }
        }
        ctx.json(ApiResponse.ok("Tail stopped"));
    }


    public void exportLogs(Context ctx) {
        try {
            String fileId = ctx.queryParam("fileId");
            String format = ctx.queryParam("format");
            if (fileId == null || !activeSessions.containsKey(fileId)) {
                ctx.status(404).result("Log session not found");
                return;
            }

            LogSession session = activeSessions.get(fileId);
            int total = session.getTotalLineCount();
            StringBuilder sb = new StringBuilder();

            if ("csv".equalsIgnoreCase(format)) {
                ctx.contentType("text/csv");
                ctx.header("Content-Disposition", "attachment; filename=\"" + session.fileName + ".csv\"");
                sb.append("Line,Content\n");
                for (int i = 0; i < total; i++) {
                    sb.append(i + 1).append(",\"").append(session.getRawLine(i).replace("\"", "\"\"")).append("\"\n");
                }
            } else if ("json".equalsIgnoreCase(format)) {
                ctx.contentType("application/json");
                ctx.header("Content-Disposition", "attachment; filename=\"" + session.fileName + ".json\"");
                sb.append("[\n");
                for (int i = 0; i < total; i++) {
                    sb.append("  {\"lineNumber\": ").append(i + 1).append(", \"rawLog\": \"")
                      .append(escapeJson(session.getRawLine(i))).append("\"}");
                    if (i < total - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("]");
            } else {
                ctx.contentType("text/plain");
                ctx.header("Content-Disposition", "attachment; filename=\"" + session.fileName + ".txt\"");
                for (int i = 0; i < total; i++) {
                    sb.append(session.getRawLine(i)).append("\n");
                }
            }

            ctx.result(sb.toString());
        } catch (Exception e) {
            logger.error("Export error", e);
            ctx.status(500).result("Export error: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
