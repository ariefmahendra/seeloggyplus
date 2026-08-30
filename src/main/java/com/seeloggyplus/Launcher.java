package com.seeloggyplus;

import com.seeloggyplus.web.WebServer;

import java.util.Arrays;

public class Launcher {
    public static void main(String[] args) {
        boolean isWebMode = Arrays.stream(args).anyMatch(arg ->
                arg.equalsIgnoreCase("--web") ||
                arg.equalsIgnoreCase("-w") ||
                arg.startsWith("--port=") ||
                arg.startsWith("--host=")
        );

        if (isWebMode) {
            WebServer.start(args);
        } else {
            Main.main(args);
        }
    }
}
