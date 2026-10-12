package com.seeloggyplus.features.logs.application.port;

import com.seeloggyplus.shared.model.ParsingConfig;
import java.util.List;

@FunctionalInterface
public interface LogFormatDetector {

    ParsingConfig detect(List<String> sampleLines);
}
