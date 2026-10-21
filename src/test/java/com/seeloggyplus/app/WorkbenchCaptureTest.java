package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.IntArrayList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class WorkbenchCaptureTest {
    private MainController controller;
    private Parent root;
    private Stage stage;
    private LogSession session;
    private java.util.concurrent.CompletableFuture<Void> recentReady;

    @Start void start(Stage stage) throws Exception {
        this.stage=stage;
        var loader=new FXMLLoader(getClass().getResource("/fxml/MainView.fxml")); root=loader.load(); controller=loader.getController();
        stage.setScene(AppTheme.scene(root)); stage.show();
        if (Arrays.stream(MainController.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals("resetWorkbenchLayout"))) invoke("resetWorkbenchLayout");
        session=new LogSession("payment-engine-production-node-01.log (Demo SSH)",LogSession.SessionType.REMOTE);
        for(int i=0;i<250;i++) session.getLiveTailList().add(new LogEntry(i,"2026-10-21 10:24:"+String.format("%02d",i%60)+".120 "+(i%5==0?"ERROR":"INFO ")+" [payment-engine] POST /api/payments status="+(i%5==0?"503":"200")+" duration=24ms trace=demo-"+i));
        session.setTailModeEnabled(true);
        var tab=(Tab) invoke("createTabForSession",session);
        ((TabSessionManager)field("tabSessionManager")).register(tab,session);
        var tabs=(TabPane)root.lookup("#logTabPane"); tabs.getTabs().add(tab); tabs.getSelectionModel().select(tab); invoke("onActiveSessionChanged",session);
        var matches=new IntArrayList();for(int i=0;i<250;i+=5)matches.add(i);
        invoke("showSearchResultPanelForTail",matches,java.util.regex.Pattern.compile("ERROR"));
        ((TextField)root.lookup("#searchField")).setText("ERROR");
        ((javafx.animation.PauseTransition)field("searchDebounce")).stop();
        session.getCanvasLogViewer().setFollowTail(false);
        session.getCanvasLogViewer().jumpToLine(0); session.getCanvasLogViewer().selectLine(0);
        ((com.seeloggyplus.features.search.presentation.SearchNavigator)field("searchNavigator")).setMatchCount(50);
        var panel=(com.seeloggyplus.features.recent.presentation.RecentFilesPanelController)field("recentPanelController");
        var entries=new ArrayList<com.seeloggyplus.features.recent.domain.RecentFilesDto>();
        for (String name : List.of("payment-engine-production-node-01.log","nginx-access.log","application.log")) {
            var file=new com.seeloggyplus.shared.model.LogFile(); file.setId(name);file.setName(name);file.setFilePath("/var/log/production/"+name);file.setSize("64 MiB");file.setRemote(true);file.setSshServerID("demo");
            entries.add(new com.seeloggyplus.features.recent.domain.RecentFilesDto(file,null,"Demo SSH","OPEN"));
        }
        var service=(com.seeloggyplus.features.recent.application.RecentFileService) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{com.seeloggyplus.features.recent.application.RecentFileService.class},
                (proxy,method,args) -> method.getName().equals("findAll") ? entries : method.getReturnType()==Optional.class ? Optional.empty() : null);
        var recentService=panel.getClass().getDeclaredField("recentFileService");recentService.setAccessible(true);recentService.set(panel,service);
        recentReady=panel.refresh();
        invoke("displayLogDetailFromCanvas",0L,"2026-10-21 ERROR [payment-engine] POST /api/payments 503 | {\"traceId\":\"demo-0\",\"message\":\"Upstream unavailable\"}");
    }

    @Test void captureMatchedWorkloadAtFourSizesAndBothThemes() throws Exception {
        recentReady.get(10,TimeUnit.SECONDS);
        String phase=System.getenv().getOrDefault("SEELOGGY_WORKBENCH_PHASE","review");
        Path directory=phase.equals("review")?Path.of("build/workbench-review"):Path.of(System.getenv().getOrDefault("SEELOGGY_WORKBENCH_ROOT","docs/reports/assets/workbench"),phase);
        Files.createDirectories(directory);
        var metrics=new ArrayList<Map<String,Object>>();
        try {
            for(var theme:AppTheme.Theme.values())for(int[] size:new int[][]{{800,600},{1024,700},{1366,768},{1920,1080}}){
                fx(() -> {AppTheme.setTheme(theme); stage.setWidth(size[0]);stage.setHeight(size[1]);});
                fx(() -> {
                    root.applyCss();root.layout();
                    if (Arrays.stream(MainController.class.getDeclaredMethods()).anyMatch(m -> m.getName().equals("resetWorkbenchLayout"))) {
                        var split=(SplitPane)field("searchSplitPane");
                        if (split.getItems().size()==2) assertEquals(.66,split.getDividerPositions()[0],.08,"Auto docking must keep results readable after resize");
                    }
                    assertTrue(root.lookup("#logContainer").getLayoutBounds().getWidth()>0);
                    var image=root.snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];
                    image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
                    var png=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,pixels,0,w);
                    String name=theme.name().toLowerCase()+"-"+size[0]+"x"+size[1];
                    try {javax.imageio.ImageIO.write(png,"png",directory.resolve(name+".png").toFile());}catch(Exception e){throw new AssertionError(e);}
                    var log=root.lookup("#logContainer").getLayoutBounds();
                    metrics.add(Map.of("name",name,"rootWidth",w,"rootHeight",h,"logWidth",log.getWidth(),"logHeight",log.getHeight(),"logArea",log.getWidth()*log.getHeight(),"visibleRows",visibleRows()));
                });
            }
            Files.writeString(directory.resolve("metrics.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(metrics));
            if(System.getenv().containsKey("SEELOGGY_WORKBENCH_ROOT")) for(var theme:AppTheme.Theme.values()) {
                fx(() -> {AppTheme.setTheme(theme);stage.setWidth(1024);stage.setHeight(700);invoke("revealWorkbenchDetail");});
                fx(() -> {((ToggleButton)root.lookup("#rawModeButton")).fire();saveSnapshot(directory.resolve(theme.name().toLowerCase()+"-detail-raw.png"));});
                fx(() -> {((ToggleButton)root.lookup("#formattedModeButton")).fire();
                    assertTrue(((com.seeloggyplus.features.detail.presentation.LogDetailPaneController)field("detailPaneController")).getCodeArea().getText().contains("\n"));
                    saveSnapshot(directory.resolve(theme.name().toLowerCase()+"-detail-formatted.png"));});
            }
        }finally{fx(() -> {invoke("hideSearchResultPanel");session.close();AppTheme.setTheme(AppTheme.Theme.LIGHT);});}
    }
    private void saveSnapshot(Path path) {
        root.applyCss();root.layout();var image=root.snapshot(null,null);
        int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        var png=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,pixels,0,w);
        try {javax.imageio.ImageIO.write(png,"png",path.toFile());}catch(Exception error){throw new AssertionError(error);}
    }
    private int visibleRows() {
        try {var f=session.getCanvasLogViewer().getClass().getDeclaredField("visibleLineCount"); f.setAccessible(true);return f.getInt(session.getCanvasLogViewer());}
        catch(Exception error){throw new AssertionError(error);}
    }
    private Object field(String name){try{var f=MainController.class.getDeclaredField(name);f.setAccessible(true);return f.get(controller);}catch(Exception e){throw new AssertionError(e);}}
    private Object invoke(String name,Object... args){try{var m=Arrays.stream(MainController.class.getDeclaredMethods()).filter(v -> v.getName().equals(name)&&v.getParameterCount()==args.length).findFirst().orElseThrow();m.setAccessible(true);return m.invoke(controller,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void fx(Runnable action)throws Exception{WaitForAsyncUtils.asyncFx(action).get(15,TimeUnit.SECONDS);WaitForAsyncUtils.waitForFxEvents();}
}
