package com.seeloggyplus.shared.util;

public final class TailJumpPlanner {

    public enum Mode {
        REACHABLE,
        RECOMMEND_LARGER_WINDOW,
        UNKNOWN
    }

    public record Plan(Mode mode, int tailLines, int jumpIndex, long neededLines, long firstReachableLine) {
    }

    private TailJumpPlanner() {
    }

    public static Plan plan(long totalLines, int targetLine, int maxTailWindow) {
        int window = Math.max(1, maxTailWindow);
        if (totalLines <= 0 || targetLine <= 0) {
            return new Plan(Mode.UNKNOWN, 0, -1, -1, -1);
        }
        long needed = Math.max(1L, totalLines - targetLine + 1L);
        if (needed <= window) {
            long first = Math.max(1L, totalLines - window + 1L);
            long index = targetLine - first;
            if (index < 0) {
                index = 0;
            }
            return new Plan(Mode.REACHABLE, window, (int) index, needed, first);
        }
        long firstReachable = Math.max(1L, totalLines - window + 1L);
        return new Plan(Mode.RECOMMEND_LARGER_WINDOW, (int) Math.min(Integer.MAX_VALUE, needed), 0, needed,
                firstReachable);
    }
}
