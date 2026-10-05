package com.seeloggyplus.shared.util;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;

import java.util.Locale;

/** Translates SSH client diagnostics into recovery guidance, retaining the original in Details. */
public final class SshConnectionFeedback {

    public record Feedback(String title, String message, String detail) { }

    private SshConnectionFeedback() { }

    public static Feedback describe(String detail, boolean keyAuth) {
        String raw = detail == null || detail.isBlank() ? "The SSH client did not provide technical information." : detail;
        String text = raw.toLowerCase(Locale.ROOT);
        if (text.contains("host key") && (text.contains("changed") || text.contains("mismatch"))) {
            return new Feedback("Server identity changed",
                    "The server's identity no longer matches the saved identity. The connection was stopped.\n\n"
                            + "Ask the server administrator to confirm the change before updating the saved server identity.", raw);
        }
        if (text.contains("auth fail") || text.contains("authentication fail") || text.contains("userauth fail")) {
            String guidance = keyAuth
                    ? "Check the username, private key file and passphrase in the server settings, then try again."
                    : "Check the username and password in the server settings, then try again. "
                            + "If this account requires a private key, choose Private key as the authentication method.";
            return new Feedback("Unable to sign in to server", "The server rejected the login.\n\n" + guidance, raw);
        }
        if (text.contains("invalid privatekey") || text.contains("invalid private key")
                || text.contains("private key file") || text.contains("cannot decrypt")) {
            return new Feedback("Unable to use private key",
                    "The selected private key file could not be used.\n\n"
                            + "Choose the correct private key file and check its passphrase, then try again.", raw);
        }
        if (text.contains("unknownhost") || text.contains("unknown host") || text.contains("name or service not known")) {
            return new Feedback("Server address not found",
                    "The server address could not be found.\n\n"
                            + "Check the server address and your network or VPN connection, then try again.", raw);
        }
        if (text.contains("timed out") || text.contains("timeout")) {
            return new Feedback("Server did not respond",
                    "The server did not respond in time.\n\n"
                            + "Check the server address and port, and make sure your network or VPN connection is available. Then try again.", raw);
        }
        if (text.contains("connection refused")) {
            return new Feedback("Server refused the connection",
                    "The connection was refused.\n\n"
                            + "Check the server address and port. Ask the server administrator to confirm that the SSH service is running.", raw);
        }
        return new Feedback("Unable to connect to server",
                "The connection could not be established.\n\n"
                        + "Check the server settings and your network connection, then try again. More information is available in Details.", raw);
    }

    public static Alert createAlert(String detail, SSHServerModel server) {
        Feedback feedback = describe(detail, server != null && server.usesKeyAuth());
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connection problem");
        alert.setHeaderText(feedback.title());
        alert.setContentText(feedback.message());
        String context = server == null ? "" : "Server: " + server.getHost() + ":" + server.getPort()
                + "\nUsername: " + server.getUsername() + "\nAuthentication: "
                + (server.usesKeyAuth() ? "Private key" : "Password") + "\n\n";
        TextArea details = new TextArea(context + feedback.detail());
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefColumnCount(48);
        details.setPrefRowCount(5);
        var pane = alert.getDialogPane();
        pane.setExpandableContent(details);
        pane.setPrefWidth(520);
        pane.getStyleClass().add("ssh-connection-feedback");
        pane.getStylesheets().setAll(AppTheme.sceneStylesheets(AppTheme.getTheme()));
        AppTheme.applyThemeState(pane, AppTheme.getTheme());
        return alert;
    }
}
