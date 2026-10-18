package com.seeloggyplus.shared.tail;

import java.io.File;
import java.util.function.Consumer;

public interface TailService {

    void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext);

    default void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext, int contextRows) {
        startLocalTail(file, lineConsumer, errorHandler, loadContext);
    }

    void stopTail();

    boolean isRunning();
}
