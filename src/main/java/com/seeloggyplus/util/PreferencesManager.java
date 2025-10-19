package com.seeloggyplus.util;


import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.model.SSHServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class PreferencesManager {


    private static final String RECENT_FILES_KEY = "recentFiles";
    private static final String SSH_SERVERS_KEY = "sshServers";
    private static final String WINDOW_X_KEY = "windowX";
    private static final String WINDOW_Y_KEY = "windowY";
    private static final String WINDOW_WIDTH_KEY = "windowWidth";
    private static final String WINDOW_HEIGHT_KEY = "windowHeight";
    private static final String WINDOW_MAXIMIZED_KEY = "windowMaximized";
    private static final String LEFT_PANEL_VISIBLE_KEY = "leftPanelVisible";
    private static final String LEFT_PANEL_WIDTH_KEY = "leftPanelWidth";
    private static final String BOTTOM_PANEL_VISIBLE_KEY = "bottomPanelVisible";
    private static final String BOTTOM_PANEL_HEIGHT_KEY = "bottomPanelHeight";

    private static PreferencesManager instance;
    private final Preferences prefs;

    private PreferencesManager() {
        prefs = Preferences.userNodeForPackage(PreferencesManager.class);
    }

    public static synchronized PreferencesManager getInstance() {
        if (instance == null) {
            instance = new PreferencesManager();
        }
        return instance;
    }



    public void saveRecentFiles(List<RecentFile> recentFiles) {
        try {
            File file = new File(System.getProperty("user.home"), ".seeloggyplus/recent_files.dat");
            file.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(new ArrayList<>(recentFiles));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public List<RecentFile> getRecentFiles() {
        try {
            File file = new File(System.getProperty("user.home"), ".seeloggyplus/recent_files.dat");
            if (file.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    return (List<RecentFile>) ois.readObject();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void addRecentFile(RecentFile recentFile) {
        List<RecentFile> recentFiles = getRecentFiles();
        recentFiles.remove(recentFile);
        recentFiles.add(0, recentFile);
        if (recentFiles.size() > 10) {
            recentFiles.remove(10);
        }
        saveRecentFiles(recentFiles);
    }

    public void clearRecentFiles() {
        saveRecentFiles(new ArrayList<>());
    }

    public void saveSshServers(List<SSHServer> sshServers) {
        try {
            File file = new File(System.getProperty("user.home"), ".seeloggyplus/ssh_servers.dat");
            file.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(new ArrayList<>(sshServers));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public List<SSHServer> getSshServers() {
        try {
            File file = new File(System.getProperty("user.home"), ".seeloggyplus/ssh_servers.dat");
            if (file.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    return (List<SSHServer>) ois.readObject();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public double getWindowX() {
        return prefs.getDouble(WINDOW_X_KEY, 100);
    }

    public void setWindowX(double x) {
        prefs.putDouble(WINDOW_X_KEY, x);
    }

    public double getWindowY() {
        return prefs.getDouble(WINDOW_Y_KEY, 100);
    }

    public void setWindowY(double y) {
        prefs.putDouble(WINDOW_Y_KEY, y);
    }

    public double getWindowWidth() {
        return prefs.getDouble(WINDOW_WIDTH_KEY, 800);
    }

    public void setWindowWidth(double width) {
        prefs.putDouble(WINDOW_WIDTH_KEY, width);
    }

    public double getWindowHeight() {
        return prefs.getDouble(WINDOW_HEIGHT_KEY, 600);
    }

    public void setWindowHeight(double height) {
        prefs.putDouble(WINDOW_HEIGHT_KEY, height);
    }

    public boolean isWindowMaximized() {
        return prefs.getBoolean(WINDOW_MAXIMIZED_KEY, false);
    }

    public void setWindowMaximized(boolean maximized) {
        prefs.putBoolean(WINDOW_MAXIMIZED_KEY, maximized);
    }

    public boolean isLeftPanelVisible() {
        return prefs.getBoolean(LEFT_PANEL_VISIBLE_KEY, true);
    }

    public void setLeftPanelVisible(boolean visible) {
        prefs.putBoolean(LEFT_PANEL_VISIBLE_KEY, visible);
    }

    public double getLeftPanelWidth() {
        return prefs.getDouble(LEFT_PANEL_WIDTH_KEY, 200);
    }

    public void setLeftPanelWidth(double width) {
        prefs.putDouble(LEFT_PANEL_WIDTH_KEY, width);
    }

    public boolean isBottomPanelVisible() {
        return prefs.getBoolean(BOTTOM_PANEL_VISIBLE_KEY, true);
    }

    public void setBottomPanelVisible(boolean visible) {
        prefs.putBoolean(BOTTOM_PANEL_VISIBLE_KEY, visible);
    }

    public double getBottomPanelHeight() {
        return prefs.getDouble(BOTTOM_PANEL_HEIGHT_KEY, 200);
    }

    public void setBottomPanelHeight(double height) {
        prefs.putDouble(BOTTOM_PANEL_HEIGHT_KEY, height);
    }
}
