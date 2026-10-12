package com.seeloggyplus.features.update.application;

import com.seeloggyplus.features.update.domain.UpdateCheckResult;

public interface UpdateService {

    String DEFAULT_ASSET_KEY = "portable-nojre";

    UpdateCheckResult check(String channel);
}
