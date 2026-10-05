package com.seeloggyplus.shared.ssh;

@FunctionalInterface
public interface SSHServiceFactory {
    SSHService create();
}
