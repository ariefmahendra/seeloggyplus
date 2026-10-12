package com.seeloggyplus.shared.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SSHServerModel {

    public static final String AUTH_PASSWORD = "PASSWORD";

    public static final String AUTH_KEY = "KEY";

    public enum ConnectionStatus {
        UNKNOWN,
        CONNECTED,
        DISCONNECTED,
        TESTING
    }

    private String id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private String defaultPath;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private boolean savePassword;
    private boolean favorite;
    private Integer sortOrder;
    private String groupName;
    private String authType = AUTH_PASSWORD;
    private String keyPath;
    private String keyPassphrase;

    private transient ConnectionStatus connectionStatus = ConnectionStatus.UNKNOWN;

    public SSHServerModel(String name, String host, int port, String username) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.savePassword = false;
        this.createdAt = LocalDateTime.now();
    }

    public boolean usesKeyAuth() {
        return AUTH_KEY.equalsIgnoreCase(authType);
    }

    public String getDisplayString() {
        if (name != null && !name.isEmpty()) {
            return String.format("%s (%s@%s:%d)", name, username, host, port);
        }
        return String.format("%s@%s:%d", username, host, port);
    }

    public boolean isValid() {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return port > 0 && port <= 65535;
    }

    public String getValidationError() {
        if (host == null || host.trim().isEmpty()) {
            return "Host is required";
        }
        if (username == null || username.trim().isEmpty()) {
            return "Username is required";
        }
        if (port <= 0 || port > 65535) {
            return "Port must be between 1 and 65535";
        }
        return null;
    }

    @Override
    public String toString() {
        return getDisplayString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SSHServerModel that = (SSHServerModel) o;
        return port == that.port &&
               Objects.equals(host, that.host) &&
               Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username);
    }
}
