package com.seeloggyplus.performance;

import com.google.gson.GsonBuilder;
import com.seeloggyplus.app.MainController;
import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.shared.util.LineOffsetIndex;
import com.seeloggyplus.shared.util.MappedFileReader;
import com.sun.jna.*;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class PerformanceProfile {
    public interface MemoryApi extends StdCallLibrary {
        MemoryApi INSTANCE=Native.load("psapi",MemoryApi.class);
        boolean GetProcessMemoryInfo(HANDLE process,Counters counters,int size);
    }
    @Structure.FieldOrder({"cb","faults","peakWorking","working","peakPaged","paged","peakNonPaged","nonPaged","pagefile","peakPagefile","privateBytes"})
    public static class Counters extends Structure {
        public int cb,faults;
        public long peakWorking,working,peakPaged,paged,peakNonPaged,nonPaged,pagefile,peakPagefile,privateBytes;
    }
    private static final Map<String,Object> result=new LinkedHashMap<>();
    private static final List<Map<String,Object>> operations=new ArrayList<>();
    private static final List<Long> uiLatencies=new CopyOnWriteArrayList<>();
    private static final AtomicBoolean running=new AtomicBoolean(true);
    private static final AtomicReference<String> scenario=new AtomicReference<>("startup");
    private static Path output;
    private static CanvasLogViewer viewer;
    private static final List<LogSession> sessions=new ArrayList<>();

    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);
        int soakSeconds=Integer.parseInt(args.length>1?args[1]:"1800");
        result.put("java",System.getProperty("java.runtime.version"));
        result.put("processors",Runtime.getRuntime().availableProcessors());
        result.put("flags",ManagementFactory.getRuntimeMXBean().getInputArguments());
        result.put("headless",true);
        CountDownLatch startup=new CountDownLatch(1);
        Platform.startup(() -> {viewer=new CanvasLogViewer();var stage=new Stage();stage.setScene(AppTheme.scene(new StackPane(viewer)));stage.setWidth(1024);stage.setHeight(700);stage.show();startup.countDown();});
        if(!startup.await(30,TimeUnit.SECONDS))throw new IllegalStateException("FX startup timed out");
        Thread sampler=Thread.ofPlatform().daemon().name("profile-sampler").start(PerformanceProfile::sampleLoop);
        try(var recording=new Recording(Configuration.getConfiguration("profile"))) {
            recording.setMaxSize(128L*1024*1024);recording.setMaxAge(Duration.ofMinutes(40));
            recording.setDestination(output.resolve("profile.jfr"));recording.start();
            scenario.set("idle");Thread.sleep(60000);
            List<Path> fixtures=new ArrayList<>();
            List<LineOffsetIndex> indexes=new ArrayList<>();
            List<MappedFileReader> readers=new ArrayList<>();
            for(int mib:new int[]{100,512,1024}) {
                Path fixture=fixture(mib);fixtures.add(fixture);
                LineOffsetIndex index=null;
                for(int run=0;run<6;run++) {
                    scenario.set("index-"+mib);long begin=System.nanoTime();index=new LineOffsetIndex();
                    try(var file=new RandomAccessFile(fixture.toFile(),"r")){index.buildIndex(file,null);}
                    operation("index",mib,run,System.nanoTime()-begin,index.getLineCount());
                }
                indexes.add(index);readers.add(new MappedFileReader(fixture.toFile()));
            }
            MappedFileReader reader=readers.getLast();LineOffsetIndex index=indexes.getLast();
            fx(() -> viewer.loadFile(reader,index));
            var predicateMethod=MainController.class.getDeclaredMethod("createBooleanSearchPredicate",String.class,boolean.class);predicateMethod.setAccessible(true);
            java.lang.reflect.Method literal=null;
            try{literal=MappedFileReader.class.getMethod("lineContainsLiteral",LineOffsetIndex.class,int.class,String.class,boolean.class);}catch(NoSuchMethodException ignored){}
            result.put("literalFastPath",literal!=null);
            for(String query:new String[]{"ABSENT_MARKER","ERROR","2026","ERROR.*synthetic","POST AND NOT skip"}) {
                boolean regex=query.equals("ERROR.*synthetic"),simple=!regex&&!query.contains(" AND ");
                @SuppressWarnings("unchecked") Predicate<String> predicate=regex?Pattern.compile(query).asPredicate():(Predicate<String>)predicateMethod.invoke(new MainController(),query,false);
                for(int run=0;run<6;run++) {
                    scenario.set("search-"+query);long begin=System.nanoTime();long count=0;
                    for(int line=0;line<index.getLineCount();line++) {
                        boolean match=literal!=null&&simple?(boolean)literal.invoke(reader,index,line,query,false):reader.lineMatches(index,line,raw -> predicate.test(raw.toString()));
                        if(match)count++;
                    }
                    operation("search:"+query,1024,run,System.nanoTime()-begin,count);
                }
            }
            for(int i=0;i<5;i++)sessions.add(new LogSession("profile-"+i,LogSession.SessionType.REMOTE));
            long sequence=0;
            for(int batch=0;batch<500;batch++) {
                appendBatch(sequence);sequence+=100;
            }
            fx(() -> {viewer.resetView();viewer.setTailBuffer(sessions.getFirst().getLiveTailList());viewer.setFollowTail(false);});
            scenario.set("tail-soak");long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(soakSeconds);
            long ticks=0;
            while(System.nanoTime()<until) {
                long begin=System.nanoTime();appendBatch(sequence);sequence+=100;
                if(ticks++%2==0)fx(() -> {viewer.refreshTail();viewer.selectLine(Math.min(20,viewer.getItemCount()-1));});
                long remaining=TimeUnit.MILLISECONDS.toNanos(100)-(System.nanoTime()-begin);
                if(remaining>0)TimeUnit.NANOSECONDS.sleep(remaining);
                if(ticks%100==0)System.out.println("SOAK ticks="+ticks+" rows="+sequence);
            }
            result.put("soakSeconds",soakSeconds);result.put("soakTicks",ticks);result.put("tailRowsPerSession",sessions.getFirst().getLiveTailList().size());
            diagnostic("peak");
            scenario.set("close-sessions");
            fx(() -> viewer.resetView());for(var session:sessions)session.close();sessions.clear();for(var item:readers)item.close();indexes.clear();readers.clear();
            for(int round=0;round<10;round++) {
                var item=new LogSession("cycle-"+round,LogSession.SessionType.REMOTE);
                for(int row=0;row<1000;row++)item.getLiveTailList().add(new LogEntry(row,"POST synthetic "+row));
                item.close();
            }
            Thread.sleep(15000);diagnostic("closed");
            recording.stop();
        } finally {
            running.set(false);sampler.join(3000);result.put("operations",operations);
            var latency=new ArrayList<>(uiLatencies);Collections.sort(latency);
            result.put("uiP95Ms",percentile(latency,.95)/1e6);result.put("uiP99Ms",percentile(latency,.99)/1e6);
            Files.writeString(output.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            Platform.exit();
        }
        System.out.println("PROFILE COMPLETE "+output);System.exit(0);
    }

    private static void appendBatch(long sequence) {
        for(int s=0;s<sessions.size();s++) {
            var lines=sessions.get(s).getLiveTailList();
            List<LogEntry> batch=new ArrayList<>();
            for(int i=0;i<100;i++) {
                String prefix="2026 POST synthetic session="+s+" sequence="+(sequence+i)+" ";
                batch.add(new LogEntry(sequence+i,prefix+"x".repeat(1023-prefix.length())));
            }
            synchronized(lines){lines.addAll(batch);if(lines.size()>50000)lines.subList(0,lines.size()-50000).clear();}
        }
    }

    private static Path fixture(int mib) throws Exception {
        Path file=Path.of("build/performance/fixtures",mib+"MiB.log");Files.createDirectories(file.getParent());
        if(Files.exists(file)&&Files.size(file)==mib*1024L*1024)return file;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var out=new java.security.DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(file),1024*1024),digest)) {
            for(long row=0;row<mib*1024L;row++) {
                String prefix="2026 "+(row%100==0?"ERROR":row%2==0?"POST":"GET")+" synthetic /api/example ";
                out.write((prefix+"x".repeat(1023-prefix.length())+"\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        Files.writeString(file.resolveSibling(file.getFileName()+".sha256"),HexFormat.of().formatHex(digest.digest()));return file;
    }

    private static void operation(String type,int mib,int run,long ns,long count) {
        var value=new LinkedHashMap<String,Object>();value.put("operation",type);value.put("mib",mib);value.put("run",run);value.put("milliseconds",ns/1e6);value.put("count",count);operations.add(value);
        System.out.println(value);
    }
    private static void sampleLoop() {
        try(var writer=Files.newBufferedWriter(output.resolve("samples.csv"))) {
            writer.write("elapsedSeconds,scenario,heapUsed,heapCommitted,workingSet,privateBytes,cpuNanos,threads,fxLatencyNanos\n");
            long start=System.nanoTime();var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
            while(running.get()) {
                var counters=new Counters();counters.cb=counters.size();MemoryApi.INSTANCE.GetProcessMemoryInfo(Kernel32.INSTANCE.GetCurrentProcess(),counters,counters.size());
                long submitted=System.nanoTime();Platform.runLater(() -> uiLatencies.add(System.nanoTime()-submitted));
                var runtime=Runtime.getRuntime();long delay=uiLatencies.isEmpty()?0:uiLatencies.getLast();
                writer.write((System.nanoTime()-start)/1e9+","+scenario.get()+","+(runtime.totalMemory()-runtime.freeMemory())+","+runtime.totalMemory()+","+counters.working+","+counters.privateBytes+","+os.getProcessCpuTime()+","+ManagementFactory.getThreadMXBean().getThreadCount()+","+delay+"\n");writer.flush();Thread.sleep(1000);
            }
        }catch(Exception error){error.printStackTrace();result.put("samplerError",error.toString());}
    }
    private static long percentile(List<Long> values,double p){return values.isEmpty()?0:values.get(Math.min(values.size()-1,(int)Math.ceil(values.size()*p)-1));}
    private static void diagnostic(String name) throws Exception {
        Path jcmd=Path.of(System.getProperty("java.home"),"bin/jcmd.exe");
        for(String command:new String[]{"GC.heap_info","GC.class_histogram","VM.native_memory summary"}) {
            var args=new ArrayList<String>();args.add(jcmd.toString());args.add(Long.toString(ProcessHandle.current().pid()));args.addAll(List.of(command.split(" ")));
            new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output.resolve(name+"-"+command.split(" ")[0]+".txt").toFile()).start().waitFor(60,TimeUnit.SECONDS);
        }
    }
    private static void fx(Runnable action) throws Exception {var future=new CompletableFuture<Void>();Platform.runLater(() -> {try{action.run();future.complete(null);}catch(Throwable error){future.completeExceptionally(error);}});future.get(30,TimeUnit.SECONDS);}
}
