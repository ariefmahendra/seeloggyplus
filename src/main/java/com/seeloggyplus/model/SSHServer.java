package com.seeloggyplus.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model class representing an SSH server configuration
 * Used for storing and managing SSH connection details
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SSHServer {

    public enum ConnectionStatus {
        UNKNOWN,      // Not tested yet
        CONNECTED,    // Successfully connected
        DISCONNECTED, // Failed to connect
        TESTING       // Currently testing connection
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
    
    // Transient field - not stored in database
    private transient ConnectionStatus connectionStatus = ConnectionStatus.UNKNOWN;

    public SSHServer(String name, String host, int port, String username) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.savePassword = false;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Get display string for SSH server
     */
    public String getDisplayString() {
        if (name != null && !name.isEmpty()) {
            return String.format("%s (%s@%s:%d)", name, username, host, port);
        }
        return String.format("%s@%s:%d", username, host, port);
    }

    /**
     * Validate server configuration
     */
    public boolean isValid() {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return port > 0 && port <= 65535;
    }

    /**
     * Get validation error message
     */
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
        SSHServer that = (SSHServer) o;
        return port == that.port &&
               Objects.equals(host, that.host) &&
               Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username);
    }
}
