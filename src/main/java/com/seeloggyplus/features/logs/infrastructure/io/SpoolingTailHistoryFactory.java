package com.seeloggyplus.features.logs.infrastructure.io;

import com.seeloggyplus.shared.tail.TailHistory;
import com.seeloggyplus.shared.tail.TailHistoryFactory;
import java.nio.file.Path;

public final class SpoolingTailHistoryFactory implements TailHistoryFactory {
    @Override public TailHistory create() {
        return new SpoolingTailHistory(Path.of(System.getProperty("java.io.tmpdir")), 8L * 1024 * 1024);
    }
}
