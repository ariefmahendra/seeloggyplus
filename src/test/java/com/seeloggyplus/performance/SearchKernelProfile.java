package com.seeloggyplus.performance;

import com.google.gson.GsonBuilder;
import com.seeloggyplus.shared.util.*;
import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.session.LogSession;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

public final class SearchKernelProfile {
    private record Scan(long nanoseconds, long allocatedBytes, long matches) {}
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        List<Map<String, Object>> runs = new ArrayList<>();
        var index = new LineOffsetIndex();
        Path fixture = Path.of("build/performance/fixtures/1024MiB.log");
        try (var file = new RandomAccessFile(fixture.toFile(), "r")) {index.buildIndex(file, null);}
        boolean fast;
        try {MappedFileReader.class.getMethod("lineContainsLiteral", LineOffsetIndex.class, int.class, String.class, boolean.class); fast = true;}
        catch (NoSuchMethodException error) {fast = false;}
        boolean optimized = fast;
        try (var reader = new MappedFileReader(fixture.toFile())) {
            for (int workers : new int[]{2, 4, 6}) {
                try (var pool = Executors.newFixedThreadPool(workers)) {
                    for (String query : new String[]{"ABSENT_MARKER", "ERROR", "2026"}) {
                        String foldedQuery = query.toLowerCase();
                        Predicate<CharSequence> predicate = raw -> raw.toString().toLowerCase().contains(foldedQuery);
                        for (int run = 0; run < 6; run++) {
                            long begin = System.nanoTime();
                            List<Future<Scan>> tasks = new ArrayList<>();
                            for (int worker = 0; worker < workers; worker++) {
                                int start = index.getLineCount() * worker / workers;
                                int end = index.getLineCount() * (worker + 1) / workers;
                                tasks.add(pool.submit(() -> {
                                    var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                                    long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
                                    long time = System.nanoTime();
                                    var matches = new IntArrayList();
                                    for (int line = start; line < end; line++) {
                                        boolean found = optimized ? reader.lineContainsLiteral(index, line, query, false)
                                                : reader.lineMatches(index, line, predicate);
                                        if (found) matches.add(line);
                                    }
                                    return new Scan(System.nanoTime() - time,
                                            bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before, matches.size());
                                }));
                            }
                            long allocation = 0, count = 0;
                            for (var task : tasks) {Scan scan = task.get(); allocation += scan.allocatedBytes; count += scan.matches;}
                            var data = new LinkedHashMap<String, Object>();
                            data.put("workers", workers); data.put("query", query); data.put("run", run);
                            data.put("milliseconds", (System.nanoTime() - begin) / 1e6);
                            data.put("allocatedBytes", allocation); data.put("matches", count);
                            runs.add(data); System.out.println(data);
                        }
                    }
                }
            }
        }
        Files.writeString(out.resolve("kernel.json"), new GsonBuilder().setPrettyPrinting().create().toJson(runs));
        var tails = new ArrayList<LogSession>();
        long begin = System.nanoTime();
        for (int session = 0; session < 5; session++) {
            var item = new LogSession("burst-" + session, LogSession.SessionType.REMOTE);
            tails.add(item);
            for (int row = 0; row < 1000; row++) item.getLiveTailList().add(new LogEntry(row,
                    "POST burst session=" + session + " row=" + row + " " + "x".repeat(128 * 1024)));
        }
        long bytes = 0;
        for (var tail : tails) for (int row = 0; row < 1000; row += 100) bytes += tail.getLiveTailList().get(row).getRawLog().length();
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        Files.writeString(out.resolve("burst.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "rows", 5000, "payloadBytesPerRow", 128 * 1024, "heapUsedBeforeDiagnosticGc", used,
                "verificationCharacters", bytes, "milliseconds", (System.nanoTime() - begin) / 1e6)));
        for (var tail : tails) tail.close();
        System.out.println("KERNEL COMPLETE " + out);
    }
}
