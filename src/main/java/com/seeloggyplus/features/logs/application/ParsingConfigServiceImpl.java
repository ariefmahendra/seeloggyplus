package com.seeloggyplus.features.logs.application;

import com.seeloggyplus.features.logs.application.port.LogFormatDetector;
import com.seeloggyplus.features.logs.application.port.ParsingConfigRepository;
import com.seeloggyplus.shared.logs.port.ParsingConfigService;
import com.seeloggyplus.shared.model.ParsingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ParsingConfigServiceImpl implements ParsingConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigServiceImpl.class);
    private final ParsingConfigRepository repository;
    private final LogFormatDetector detector;

    public ParsingConfigServiceImpl(ParsingConfigRepository repository, LogFormatDetector detector) {
        this.repository = Objects.requireNonNull(repository);
        this.detector = Objects.requireNonNull(detector);
    }

    @Override
    public Optional<ParsingConfig> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<ParsingConfig> findAll() {
        return repository.findAll();
    }

    @Override
    public void save(ParsingConfig config) {
        if (config.getId() == null) {
            config.setId(UUID.randomUUID().toString());
        }
        repository.save(config);
        logger.debug("Saved parsing config: {}", config.getName());
    }

    @Override
    public void update(ParsingConfig config) {
        repository.update(config);
        logger.debug("Updated parsing config: {}", config.getName());
    }

    @Override
    public void delete(ParsingConfig config) {
        repository.delete(config);
        logger.debug("Deleted parsing config: {}", config.getName());
    }

    @Override
    public Optional<ParsingConfig> findDefault() {
        return repository.findDefault();
    }

    @Override
    public ParsingConfig detectLogFormat(List<String> sampleLines) {
        return detector.detect(sampleLines);
    }
}
