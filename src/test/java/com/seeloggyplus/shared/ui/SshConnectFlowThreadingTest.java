package com.seeloggyplus.shared.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SshConnectFlowThreadingTest {

    @Start
    void start(Stage stage) {
    }

    private static Object runOnFxAndWait(Supplier<?> action) throws Exception {
        var method = SshConnectFlow.class.getDeclaredMethod("runOnFxAndWait", Supplier.class);
        method.setAccessible(true);
        try {
            return method.invoke(null, action);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }

    @Test
    void interruptedWorkerPreservesItsInterruptAndDoesNotRunTheQueuedPrompt() throws Exception {
        assertInterruptedPrompt(true);
    }

    @Test
    void interruptionWhileWaitingCancelsTheQueuedPromptAndPreservesInterrupt() throws Exception {
        assertInterruptedPrompt(false);
    }

    private static void assertInterruptedPrompt(boolean interruptBeforeWaiting) throws Exception {
        var invoked = new AtomicBoolean();
        var interrupted = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            if (interruptBeforeWaiting) Thread.currentThread().interrupt();
            try {
                runOnFxAndWait(() -> { invoked.set(true); return true; });
            } catch (Exception exception) {
                failure.set(exception);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        }, "interrupted-host-key-prompt");
        WaitForAsyncUtils.asyncFx(() -> {
            worker.start();
            if (!interruptBeforeWaiting) {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                while (worker.isAlive() && worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertEquals(Thread.State.WAITING, worker.getState());
                worker.interrupt();
            }
            worker.join(3000);
            assertFalse(worker.isAlive());
            return null;
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertInstanceOf(InterruptedException.class, failure.get().getCause());
        assertTrue(interrupted.get());
        assertFalse(invoked.get());
    }

    @Test
    void fxThreadInvocationRunsInlineWithoutQueueing() throws Exception {
        var result = WaitForAsyncUtils.asyncFx(() -> runOnFxAndWait(() -> {
            assertTrue(Platform.isFxApplicationThread());
            return "inline";
        })).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("inline", result);
    }

    @Test
    void fxActionFailureIsReportedAsConfirmationFailure() throws Exception {
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try {
                runOnFxAndWait(() -> {
                    throw new IllegalStateException("boom");
                });
            } catch (Exception exception) {
                failure.set(exception);
            }
        }, "host-key-prompt-failure");
        worker.start();
        worker.join(3000);
        assertFalse(worker.isAlive());
        var thrown = assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals("Host key confirmation failed", thrown.getMessage());
        assertInstanceOf(java.util.concurrent.ExecutionException.class, thrown.getCause());
    }

    @Test
    void workerReceivesTheResultProducedOnTheFxThread() throws Exception {
        var result = new AtomicReference<Object>();
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try {
                result.set(runOnFxAndWait(() -> { assertTrue(Platform.isFxApplicationThread()); return "accepted"; }));
            } catch (Exception exception) {
                failure.set(exception);
            }
        }, "host-key-prompt-result");
        worker.start();
        worker.join(3000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertEquals("accepted", result.get());
    }
}
