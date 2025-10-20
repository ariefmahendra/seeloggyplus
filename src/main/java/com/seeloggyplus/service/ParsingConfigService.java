package com.seeloggyplus.service;

import com.seeloggyplus.model.ParsingConfig;

import java.util.List;
import java.util.Optional;

public interface ParsingConfigService {
    Optional<ParsingConfig> findById(int id);
    List<ParsingConfig> findAll();
    void save(ParsingConfig config);
    void update(ParsingConfig config);
    void delete(ParsingConfig config);
    Optional<ParsingConfig> findDefault();
}
