package com.seeloggyplus.app;
import com.seeloggyplus.shared.util.StartupSplash;

public class Launcher {
    public static void main(String[] args) {
        StartupSplash.showStatus("Starting SeeLoggyPlus...");
        Main.main(args);
    }
}
