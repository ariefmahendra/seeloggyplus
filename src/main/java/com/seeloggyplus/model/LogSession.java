package com.seeloggyplus.model;

import com.seeloggyplus.service.TailService;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.util.IntArrayList;
import com.seeloggyplus.util.LineOffsetIndex;
import com.seeloggyplus.util.MappedFileReader;
import javafx.scene.control.Tab;
import javafx.scene.shape.Circle;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Encapsulates the entire state of an open log session (single tab).
 * Supports both local files and remote SSH streams.
 * Handles lifecycle cleanup of readers, indexes, and tail services.
 */
public class LogSession implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(LogSession.class);

    public enum SessionType {
        LOCAL,
        REMOTE
    }

    private final String id;
    private String title;
    private SessionType sessionType;
    private File localFile;
    private String remotePath;
    private SSHServerModel sshServer;
    private SSHServiceImpl sshService;
    private LogFile logFileRecord;
    private ParsingConfig parsingConfig;

    private Tab tab;
    private CanvasLogViewer canvasLogViewer;
    private Circle liveIndicatorDot;
    private Label unreadBadgeLabel;

    private MappedFileReader reader;
    private LineOffsetIndex index;
    private int totalEntries = 0;

    // Tail state
    private boolean tailModeEnabled = false;
    private boolean followTail = true;
    private TailService tailService;
    private final List<LogEntry> liveTailList = Collections.synchronizedList(new ArrayList<>());
    private final List<LogEntry> tailBuffer = new ArrayList<>();
    private long remoteTailLineCounter = 0;
    private final AtomicBoolean tailFlushScheduled = new AtomicBoolean(false);
    private boolean tailColumnsAutoResized = false;
    private int unreadTailLines = 0;
    private volatile boolean active = false;
    private volatile boolean closed = false;

    // Search & Filter state
    private String searchQuery = "";
    private boolean regex = false;
    private boolean caseSensitive = false;
    private IntArrayList filteredIndexes = null;
    private long filteredCount = 0;
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private Predicate<LogEntry> currentTailFilterPredicate = null;
    private Pattern currentTailSearchPattern = null;
    private int tailSearchScannedUpTo = 0;

    // Detail panel state
    private long selectedLine = -1;
    private String selectedLineContent = null;

    public LogSession(String title, SessionType sessionType) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.sessionType = sessionType;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public File getLocalFile() {
        return localFile;
    }

    public void setLocalFile(File localFile) {
        this.localFile = localFile;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public SSHServerModel getSshServer() {
        return sshServer;
    }

    public void setSshServer(SSHServerModel sshServer) {
        this.sshServer = sshServer;
    }

    public SSHServiceImpl getSshService() {
        return sshService;
    }

    public void setSshService(SSHServiceImpl sshService) {
        this.sshService = sshService;
    }

    public LogFile getLogFileRecord() {
        return logFileRecord;
    }

    public void setLogFileRecord(LogFile logFileRecord) {
        this.logFileRecord = logFileRecord;
    }

    public ParsingConfig getParsingConfig() {
        return parsingConfig;
    }

    public void setParsingConfig(ParsingConfig parsingConfig) {
        this.parsingConfig = parsingConfig;
    }

    public Tab getTab() {
        return tab;
    }

    public void setTab(Tab tab) {
        this.tab = tab;
    }

    public CanvasLogViewer getCanvasLogViewer() {
        return canvasLogViewer;
    }

    public void setCanvasLogViewer(CanvasLogViewer canvasLogViewer) {
        this.canvasLogViewer = canvasLogViewer;
    }

    public Circle getLiveIndicatorDot() {
        return liveIndicatorDot;
    }

    public void setLiveIndicatorDot(Circle liveIndicatorDot) {
        this.liveIndicatorDot = liveIndicatorDot;
    }

    public Label getUnreadBadgeLabel() {
        return unreadBadgeLabel;
    }

    public void setUnreadBadgeLabel(Label unreadBadgeLabel) {
        this.unreadBadgeLabel = unreadBadgeLabel;
    }

    public MappedFileReader getReader() {
        return reader;
    }

    public void setReader(MappedFileReader reader) {
        this.reader = reader;
    }

    public LineOffsetIndex getIndex() {
        return index;
    }

    public void setIndex(LineOffsetIndex index) {
        this.index = index;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(int totalEntries) {
        this.totalEntries = totalEntries;
    }

    public boolean isTailModeEnabled() {
        return tailModeEnabled;
    }

    public void setTailModeEnabled(boolean tailModeEnabled) {
        this.tailModeEnabled = tailModeEnabled;
    }

    public boolean isFollowTail() {
        return followTail;
    }

    public void setFollowTail(boolean followTail) {
        this.followTail = followTail;
    }

    public TailService getTailService() {
        return tailService;
    }

    public void setTailService(TailService tailService) {
        this.tailService = tailService;
    }

    public List<LogEntry> getLiveTailList() {
        return liveTailList;
    }

    public List<LogEntry> getTailBuffer() {
        return tailBuffer;
    }

    public long getRemoteTailLineCounter() {
        return remoteTailLineCounter;
    }

    public void setRemoteTailLineCounter(long remoteTailLineCounter) {
        this.remoteTailLineCounter = remoteTailLineCounter;
    }

    public long incrementRemoteTailLineCounter() {
        return ++this.remoteTailLineCounter;
    }

    public AtomicBoolean getTailFlushScheduled() {
        return tailFlushScheduled;
    }

    public boolean isTailColumnsAutoResized() {
        return tailColumnsAutoResized;
    }

    public void setTailColumnsAutoResized(boolean tailColumnsAutoResized) {
        this.tailColumnsAutoResized = tailColumnsAutoResized;
    }

    public int getUnreadTailLines() {
        return unreadTailLines;
    }

    public void setUnreadTailLines(int unreadTailLines) {
        this.unreadTailLines = unreadTailLines;
    }

    public void addUnreadTailLines(int count) {
        this.unreadTailLines += count;
    }

    public void resetUnreadTailLines() {
        this.unreadTailLines = 0;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            this.unreadTailLines = 0;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public IntArrayList getFilteredIndexes() {
        return filteredIndexes;
    }

    public void setFilteredIndexes(IntArrayList filteredIndexes) {
        this.filteredIndexes = filteredIndexes;
    }

    public long getFilteredCount() {
        return filteredCount;
    }

    public void setFilteredCount(long filteredCount) {
        this.filteredCount = filteredCount;
    }

    public int getCurrentMatchIndex() {
        return currentMatchIndex;
    }

    public void setCurrentMatchIndex(int currentMatchIndex) {
        this.currentMatchIndex = currentMatchIndex;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    public Predicate<LogEntry> getCurrentTailFilterPredicate() {
        return currentTailFilterPredicate;
    }

    public void setCurrentTailFilterPredicate(Predicate<LogEntry> currentTailFilterPredicate) {
        this.currentTailFilterPredicate = currentTailFilterPredicate;
    }

    public Pattern getCurrentTailSearchPattern() {
        return currentTailSearchPattern;
    }

    public void setCurrentTailSearchPattern(Pattern currentTailSearchPattern) {
        this.currentTailSearchPattern = currentTailSearchPattern;
    }

    public int getTailSearchScannedUpTo() {
        return tailSearchScannedUpTo;
    }

    public void setTailSearchScannedUpTo(int tailSearchScannedUpTo) {
        this.tailSearchScannedUpTo = tailSearchScannedUpTo;
    }

    public long getSelectedLine() {
        return selectedLine;
    }

    public void setSelectedLine(long selectedLine) {
        this.selectedLine = selectedLine;
    }

    public String getSelectedLineContent() {
        return selectedLineContent;
    }

    public void setSelectedLineContent(String selectedLineContent) {
        this.selectedLineContent = selectedLineContent;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        logger.info("Closing LogSession: {}", title);

        if (tailService != null) {
            try {
                tailService.stopTail();
            } catch (Exception e) {
                logger.warn("Error stopping tail service for session {}", title, e);
            }
            tailService = null;
        }

        if (sshService != null && sshService.isConnected()) {
            try {
                sshService.disconnect();
            } catch (Exception e) {
                logger.warn("Error disconnecting SSH service for session {}", title, e);
            }
            sshService = null;
        }

        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                logger.warn("Error closing MappedFileReader for session {}", title, e);
            }
            reader = null;
        }

        if (canvasLogViewer != null) {
            try {
                canvasLogViewer.resetView();
            } catch (Exception ignored) {}
        }

        synchronized (tailBuffer) {
            tailBuffer.clear();
        }
        liveTailList.clear();

        if (filteredIndexes != null) {
            filteredIndexes.clear();
            filteredIndexes = null;
        }
        index = null;
    }
}
