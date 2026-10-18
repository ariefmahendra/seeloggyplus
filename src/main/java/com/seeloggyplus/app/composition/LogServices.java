package com.seeloggyplus.app.composition;

import com.seeloggyplus.features.logs.application.LogFileServiceImpl;
import com.seeloggyplus.features.logs.application.ParsingConfigServiceImpl;
import com.seeloggyplus.features.logs.infrastructure.io.LogParserServiceImpl;
import com.seeloggyplus.features.logs.infrastructure.parsing.GrokLogFormatDetector;
import com.seeloggyplus.features.logs.infrastructure.persistence.LogFileRepositoryImpl;
import com.seeloggyplus.features.logs.infrastructure.persistence.ParsingConfigRepositoryImpl;
import com.seeloggyplus.shared.database.DatabaseConfig;
import com.seeloggyplus.shared.logs.port.LogFileService;
import com.seeloggyplus.shared.logs.port.LogParser;
import com.seeloggyplus.shared.logs.port.ParsingConfigService;
import java.sql.Connection;
import java.util.Objects;

public record LogServices(LogFileService files, ParsingConfigService parsingConfigs, LogParser parser) {

    public LogServices {
        Objects.requireNonNull(files);
        Objects.requireNonNull(parsingConfigs);
        Objects.requireNonNull(parser);
    }

    public static LogServices createDefault() {
        return create(DatabaseConfig.getInstance().getConnection());
    }

    public static LogServices create(Connection connection) {
        return new LogServices(new LogFileServiceImpl(new LogFileRepositoryImpl(connection)),
                new ParsingConfigServiceImpl(new ParsingConfigRepositoryImpl(connection), new GrokLogFormatDetector()),
                new LogParserServiceImpl());
    }
}
