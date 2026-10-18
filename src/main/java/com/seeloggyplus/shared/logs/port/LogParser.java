package com.seeloggyplus.shared.logs.port;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.model.ParsingConfig;
import com.seeloggyplus.shared.progress.ProgressCallback;
import lombok.Getter;
import lombok.Setter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface LogParser {

    @Getter
    @Setter
    class TestResult {
        private boolean success;
        private String message;
        private Map<String, String> parsedFields;
        private List<String> groupNames;
        public TestResult() {
            this.parsedFields = new HashMap<>();
            this.groupNames = new ArrayList<>();
        }
    }

    List<LogEntry> parseFileParallel(File file, ParsingConfig config, ProgressCallback callback) throws IOException;

    LogEntry parseLine(String line, long lineNumber);

    TestResult testParsing(String sampleLog, ParsingConfig config);

    List<LogEntry> search(List<LogEntry> entries, String searchText, boolean isRegex, boolean caseSensitive);
}
