package com.seeloggyplus.service;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.repository.ParsingConfigRepository;
import com.seeloggyplus.repository.ParsingConfigRepositoryImpl;

import java.util.List;
import java.util.Optional;

public class ParsingConfigServiceImpl implements ParsingConfigService {

    private final ParsingConfigRepository parsingConfigRepository;

    public ParsingConfigServiceImpl() {
        parsingConfigRepository = new ParsingConfigRepositoryImpl();
    }

    @Override
    public Optional<ParsingConfig> findById(int id) {
        return parsingConfigRepository.findById(id);
    }

    @Override
    public List<ParsingConfig> findAll() {
        return parsingConfigRepository.findAll();
    }

    @Override
    public void save(ParsingConfig config) {
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
