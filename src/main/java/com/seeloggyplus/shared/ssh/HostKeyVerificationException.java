package com.seeloggyplus.shared.ssh;

/**
 * Raised when an SSH host key cannot be trusted: the server is unknown, or its
 * key changed since it was saved in the application known_hosts file.
 */
public class HostKeyVerificationException extends RuntimeException {

    public enum Reason {
        /** Host is not present in known_hosts yet (first connection). */
        UNKNOWN,
        /** A different key was already trusted for this host (possible MITM). */
        CHANGED
    }

    private final String host;
    private final int port;
    private final String keyType;
    private final String fingerprint;
    private final Reason reason;

    public HostKeyVerificationException(String host, int port, String keyType, String fingerprint, Reason reason) {
        super(buildMessage(host, port, keyType, fingerprint, reason));
        this.host = host;
        this.port = port;
        this.keyType = keyType;
        this.fingerprint = fingerprint;
        this.reason = reason;
    }

    private static String buildMessage(String host, int port, String keyType, String fingerprint, Reason reason) {
        if (reason == Reason.CHANGED) {
            return "Host key for " + host + ":" + port + " has changed! Possible man-in-the-middle attack. "
                    + "Saved key type: " + keyType + ", new fingerprint: " + fingerprint;
        }
        return "Unknown host " + host + ":" + port + " (" + keyType + " fingerprint " + fingerprint + ")";
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getKeyType() {
        return keyType;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Reason getReason() {
        return reason;
    }
}
