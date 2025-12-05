package com.seeloggyplus.repository;

import com.seeloggyplus.model.SavedFilter;
import java.util.List;
import java.util.Optional;

public interface SavedFilterRepository {
    void save(SavedFilter filter);
    void delete(String id);
    List<SavedFilter> findAll();
    Optional<SavedFilter> findByName(String name);
}
