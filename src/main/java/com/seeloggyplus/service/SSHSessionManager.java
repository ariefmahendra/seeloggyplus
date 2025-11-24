package com.seeloggyplus.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.util.Set;

public interface SSHSessionManager {
    Session getSession(String host, int port, String username, String password, long ttlMillis) throws JSchException;
    void closeSession(String host, int port, String username);
    Set<String> getActiveSessionKeys();
}
