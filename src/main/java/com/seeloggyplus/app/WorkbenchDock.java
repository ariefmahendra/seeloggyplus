package com.seeloggyplus.app;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.Locale;

final class WorkbenchDock {
    private final SplitPane vertical;
    private final SplitPane search;
    private final Node log;
    private final Node detail;
    private final Node results;
    private final TabPane dock = new TabPane();
    private final Tab resultTab;
    private final Tab detailTab;
    private Tab selectedTab;
    private String position = "auto";
    private boolean resultsVisible;
    private boolean detailVisible = true;
    private boolean updating;
    private boolean dividerUpdateQueued;
    private boolean right;
    private double rightDivider = .66;
    private double bottomDivider = .7;

    WorkbenchDock(SplitPane vertical, SplitPane search, Node log, Node detail, Node results) {
        this.vertical=vertical; this.search=search; this.log=log; this.detail=detail; this.results=results;
        dock.setId("workbenchDock"); dock.getStyleClass().add("workbench-dock");
        dock.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        dock.setMinSize(0, 100);
        resultTab=new Tab("Results"); detailTab=new Tab("Detail");
        dock.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> {
            if (!updating && b!=null) selectedTab=b;
        });
        vertical.widthProperty().addListener((o,a,b) -> refresh());
        refresh();
    }

    String position() { return position; }
    void position(String value) {
        rememberDividers();
        String normalized=value == null ? "auto" : value.toLowerCase(Locale.ROOT);
        position=normalized.equals("right") || normalized.equals("bottom") ? normalized : "auto";
        refresh();
    }
    void resultsVisible(boolean value) {
        if (value!=resultsVisible) rememberDividers();
        boolean opening=value && !resultsVisible;
        resultsVisible=value; refresh();
        if (opening && !right) dock.getSelectionModel().select(resultTab);
    }
    void detailVisible(boolean value) {
        if (value!=detailVisible) rememberDividers();
        detailVisible=value; refresh();
    }
    void revealDetail() { if (!right && resultsVisible && detailVisible) dock.getSelectionModel().select(detailTab); }
    void restoreDividers(double horizontal, double bottom) {
        rightDivider=bounded(horizontal,.66); bottomDivider=bounded(bottom,.7);
        applyDividers(); queueDividerUpdate();
    }
    void rememberDividers() {
        rightDivider=rightDivider(); bottomDivider=bottomDivider();
    }
    double rightDivider() { return search.getDividers().isEmpty() ? rightDivider : search.getDividerPositions()[0]; }
    double bottomDivider() { return vertical.getDividers().isEmpty() ? bottomDivider : vertical.getDividerPositions()[0]; }
    private static double bounded(double value,double fallback) { return Double.isFinite(value) && value>=.1 && value<=.9 ? value : fallback; }

    private void refresh() {
        if (updating) return;
        boolean nextRight=position.equals("right") || position.equals("auto") && vertical.getWidth()>=960;
        boolean tabbed=resultsVisible && !nextRight;
        Node nextBottom=tabbed ? dock : detailVisible ? detail : null;
        boolean unchanged=right==nextRight
                && search.getItems().contains(results)==(resultsVisible && nextRight)
                && vertical.getItems().size()==(nextBottom==null?1:2)
                && (nextBottom==null || vertical.getItems().get(1)==nextBottom)
                && (!tabbed || dock.getTabs().contains(detailTab)==detailVisible);
        if (unchanged) return;
        updating=true;
        try {
            Tab selected=selectedTab;
            search.getItems().remove(results);
            vertical.getItems().removeIf(node -> node!=log);
            resultTab.setContent(null); detailTab.setContent(null); dock.getTabs().clear();
            detail.setVisible(detailVisible); detail.setManaged(detailVisible);
            if (resultsVisible && nextRight) search.getItems().add(results);
            if (tabbed) {
                resultTab.setContent(results); dock.getTabs().add(resultTab);
                if (detailVisible) { detailTab.setContent(detail); dock.getTabs().add(detailTab); }
                if (selected!=null && dock.getTabs().contains(selected)) dock.getSelectionModel().select(selected);
                vertical.getItems().add(dock);
            } else if (detailVisible) vertical.getItems().add(detail);
            right=nextRight;
            applyDividers(); queueDividerUpdate();
        } finally { updating=false; }
    }

    private void applyDividers() {
        if (!search.getDividers().isEmpty()) search.setDividerPositions(rightDivider);
        if (!vertical.getDividers().isEmpty()) vertical.setDividerPositions(bottomDivider);
    }

    private void queueDividerUpdate() {
        if (dividerUpdateQueued) return;
        dividerUpdateQueued=true;
        javafx.application.Platform.runLater(() -> { dividerUpdateQueued=false; applyDividers(); });
    }
}
