package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.SavedFilter;
import com.seeloggyplus.repository.SavedFilterRepository;
import com.seeloggyplus.repository.impl.SavedFilterRepositoryImpl;
import com.seeloggyplus.service.SavedFilterService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class SavedFilterServiceImpl implements SavedFilterService {

    private final SavedFilterRepository repository;

    public SavedFilterServiceImpl() {
        this.repository = new SavedFilterRepositoryImpl();
    }

    @Override
    public void saveFilter(SavedFilter filter) {
        if (filter.getName() == null || filter.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Filter name cannot be empty");
        }
        if (filter.getId() == null || filter.getId().isEmpty()) {
            filter.setId(UUID.randomUUID().toString());
            filter.setCreatedAt(LocalDateTime.now().toString());
        }
        repository.save(filter);
    }

    @Override
    public void deleteFilter(String id) {
        repository.delete(id);
    }

    @Override
    public List<SavedFilter> getAllFilters() {
        return repository.findAll();
    }

    @Override
    public boolean isFilterNameExists(String name) {
        return repository.findByName(name).isPresent();
    }
}
