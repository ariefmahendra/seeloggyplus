package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.repository.ParsingConfigRepository;
import com.seeloggyplus.repository.impl.ParsingConfigRepositoryImpl;
import com.seeloggyplus.service.ParsingConfigService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ParsingConfigServiceImpl implements ParsingConfigService {

    private final ParsingConfigRepository parsingConfigRepository;

    public ParsingConfigServiceImpl() {
        parsingConfigRepository = new ParsingConfigRepositoryImpl();
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
}
