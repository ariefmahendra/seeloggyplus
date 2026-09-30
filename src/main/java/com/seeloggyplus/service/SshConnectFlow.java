package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.ssh.HostKeyVerificationException;
import com.seeloggyplus.ssh.SshAuthConfig;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Shared connect flow: applies the server's authentication method and handles
 * host-key trust-on-first-use. When the host key is unknown the user is asked to
 * accept and save the fingerprint; changed keys are always refused.
 */
public final class SshConnectFlow {

    /** UI hook, injectable for tests. */
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
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
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
                    Alert alert = new Alert(Alert.AlertType.ERROR);
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
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        FutureTask<T> task = new FutureTask<>(action::get);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception e) {
            throw new IllegalStateException("Host key confirmation failed", e);
        }
    }
}
