package com.seeloggyplus.service;

import com.seeloggyplus.model.SavedFilter;
import java.util.List;

public interface SavedFilterService {
    void saveFilter(SavedFilter filter);
    void deleteFilter(String id);
    List<SavedFilter> getAllFilters();
    boolean isFilterNameExists(String name);
}
