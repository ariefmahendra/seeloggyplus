package com.seeloggyplus.shared.ui;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.HostKeyVerificationException;
import com.seeloggyplus.shared.ssh.SshAuthConfig;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

public final class SshConnectFlow {

    public interface HostKeyConfirmer {
        boolean confirmUnknownHostKey(HostKeyVerificationException problem);

        void showHostKeyProblem(HostKeyVerificationException problem);
    }

    private SshConnectFlow() {
    }

    public static boolean connect(SSHService service, SSHServerModel server, String secret) {
        return connect(service, server, secret, uiConfirmer());
    }

    static boolean connect(SSHService service, SSHServerModel server, String secret, HostKeyConfirmer confirmer) {
        service.setAuthConfig(SshAuthConfig.from(server));
        try {
            return service.connect(server.getHost(), server.getPort(), server.getUsername(), secret);
        } catch (HostKeyVerificationException problem) {
            if (problem.getReason() == HostKeyVerificationException.Reason.UNKNOWN
                    && confirmer.confirmUnknownHostKey(problem)) {
                service.trustPendingHostKey();
                service.setAuthConfig(SshAuthConfig.from(server));
                try {
                    return service.connect(server.getHost(), server.getPort(), server.getUsername(), secret);
                } catch (HostKeyVerificationException retry) {
                    confirmer.showHostKeyProblem(retry);
                    return false;
                }
            }
            confirmer.showHostKeyProblem(problem);
            return false;
        }
    }

    private static HostKeyConfirmer uiConfirmer() {
        return new HostKeyConfirmer() {
            @Override
            public boolean confirmUnknownHostKey(HostKeyVerificationException problem) {
                return runOnFxAndWait(() -> {
                    Alert alert = ThemedAlerts.create(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Unknown SSH Host");
                    alert.setHeaderText("The authenticity of host '" + problem.getHost() + ":"
                            + problem.getPort() + "' can't be established.");
                    alert.setContentText(problem.getKeyType() + " key fingerprint is "
                            + problem.getFingerprint()
                            + ".\n\nTrust this host and save its key to known_hosts?");
                    ButtonType trust = new ButtonType("Trust & Save", ButtonBar.ButtonData.OK_DONE);
                    ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(trust, cancel);
                    return alert.showAndWait().orElse(cancel) == trust;
                });
            }

            @Override
            public void showHostKeyProblem(HostKeyVerificationException problem) {
                runOnFxAndWait(() -> {
                    Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR);
                    alert.setTitle("SSH Host Key Problem");
                    alert.setHeaderText(problem.getReason() == HostKeyVerificationException.Reason.CHANGED
                            ? "Host key verification failed! The server key changed."
                            : "Host key was not trusted");
                    alert.setContentText(problem.getMessage());
                    alert.showAndWait();
                    return null;
                });
            }
        };
    }

    private static <T> T runOnFxAndWait(Supplier<T> action) {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Host key confirmation interrupted", new InterruptedException());
        }
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        FutureTask<T> task = new FutureTask<>(action::get);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Host key confirmation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Host key confirmation failed", e);
        }
    }
}
