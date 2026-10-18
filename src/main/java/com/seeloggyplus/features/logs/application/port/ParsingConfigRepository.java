package com.seeloggyplus.features.logs.application.port;

import com.seeloggyplus.shared.model.ParsingConfig;
import java.util.List;
import java.util.Optional;

public interface ParsingConfigRepository {

    Optional<ParsingConfig> findById(String id);

    List<ParsingConfig> findAll();

    void save(ParsingConfig config);

    void update(ParsingConfig config);

    void delete(ParsingConfig config);

    Optional<ParsingConfig> findDefault();
}
