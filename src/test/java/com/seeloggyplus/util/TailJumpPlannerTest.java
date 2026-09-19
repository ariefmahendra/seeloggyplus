package com.seeloggyplus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TailJumpPlannerTest {

    @Test
    void targetInsideWindowIsReachableWithComputedIndex() {
        TailJumpPlanner.Plan plan = TailJumpPlanner.plan(1000, 990, 100);
        assertEquals(TailJumpPlanner.Mode.REACHABLE, plan.mode());
        assertEquals(100, plan.tailLines());
        // first reachable = 901, target 990 -> index 89
        assertEquals(89, plan.jumpIndex());
    }

    @Test
    void windowLargerThanFileStartsAtFirstLine() {
        TailJumpPlanner.Plan plan = TailJumpPlanner.plan(500, 250, 20000);
        assertEquals(TailJumpPlanner.Mode.REACHABLE, plan.mode());
        assertEquals(20000, plan.tailLines());
        assertEquals(249, plan.jumpIndex());
        assertEquals(1, plan.firstReachableLine());
    }

    @Test
    void targetInHeadRecommendsLargerWindow() {
        TailJumpPlanner.Plan plan = TailJumpPlanner.plan(100_000, 50, 20_000);
        assertEquals(TailJumpPlanner.Mode.RECOMMEND_LARGER_WINDOW, plan.mode());
        assertEquals(99_951, plan.neededLines());
        assertEquals(99_951, plan.tailLines());
        assertEquals(0, plan.jumpIndex());
        assertEquals(80_001, plan.firstReachableLine());
    }

    @Test
    void exactBoundaryTargetIsReachable() {
        // target 80001 with window 20000 on 100000 lines -> needed == 20000
        TailJumpPlanner.Plan plan = TailJumpPlanner.plan(100_000, 80_001, 20_000);
        assertEquals(TailJumpPlanner.Mode.REACHABLE, plan.mode());
        assertEquals(0, plan.jumpIndex());
    }

    @Test
    void unknownWhenTotalOrTargetMissing() {
        assertEquals(TailJumpPlanner.Mode.UNKNOWN, TailJumpPlanner.plan(-1, 10, 100).mode());
        assertEquals(TailJumpPlanner.Mode.UNKNOWN, TailJumpPlanner.plan(100, 0, 100).mode());
        assertEquals(TailJumpPlanner.Mode.UNKNOWN, TailJumpPlanner.plan(0, 10, 100).mode());
    }

    @Test
    void lastLineIsAlwaysInWindow() {
        TailJumpPlanner.Plan plan = TailJumpPlanner.plan(12345, 12345, 10);
        assertEquals(TailJumpPlanner.Mode.REACHABLE, plan.mode());
        assertEquals(9, plan.jumpIndex());
    }
}
