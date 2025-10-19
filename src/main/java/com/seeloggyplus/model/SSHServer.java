package com.seeloggyplus.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model class representing an SSH server configuration
 * Used for storing and managing SSH connection details
 */
public class SSHServer implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean usePrivateKey;
    private String privateKeyPath;
    private String passphrase;
    private String defaultPath;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private boolean savePassword;

    public SSHServer() {
        this.port = 22;
        this.createdAt = LocalDateTime.now();
        this.savePassword = false;
        this.usePrivateKey = false;
    }

    public SSHServer(String name, String host, int port, String username) {
        this();
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUsePrivateKey() {
        return usePrivateKey;
    }

    public void setUsePrivateKey(boolean usePrivateKey) {
        this.usePrivateKey = usePrivateKey;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public void setDefaultPath(String defaultPath) {
        this.defaultPath = defaultPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

    public boolean isSavePassword() {
        return savePassword;
    }

    public void setSavePassword(boolean savePassword) {
        this.savePassword = savePassword;
    }

    /**
     * Update last used timestamp
     */
    public void updateLastUsed() {
        this.lastUsed = LocalDateTime.now();
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
     * Get connection string
     */
    public String getConnectionString() {
        return String.format("%s@%s:%d", username, host, port);
    }

    /**
     * Check if password is saved
     */
    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    /**
     * Check if private key is configured
     */
    public boolean hasPrivateKey() {
        return usePrivateKey && privateKeyPath != null && !privateKeyPath.isEmpty();
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
        if (port <= 0 || port > 65535) {
            return false;
        }
        if (usePrivateKey) {
            return hasPrivateKey();
        }
        return true;
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
        if (usePrivateKey && !hasPrivateKey()) {
            return "Private key path is required";
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

    /**
     * Create a copy without sensitive data (password, passphrase)
     */
    public SSHServer copyWithoutSensitiveData() {
        SSHServer copy = new SSHServer();
        copy.id = this.id;
        copy.name = this.name;
        copy.host = this.host;
        copy.port = this.port;
        copy.username = this.username;
        copy.usePrivateKey = this.usePrivateKey;
        copy.privateKeyPath = this.privateKeyPath;
        copy.defaultPath = this.defaultPath;
        copy.createdAt = this.createdAt;
        copy.lastUsed = this.lastUsed;
        copy.savePassword = false;
        return copy;
    }
}
