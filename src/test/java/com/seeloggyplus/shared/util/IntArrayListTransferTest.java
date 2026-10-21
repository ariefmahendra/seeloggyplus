package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntArrayListTransferTest {
    @Test void movingSearchChunksPreservesOrderWithoutRetainingSourceResults() throws Exception {
        var move=assertDoesNotThrow(() -> IntArrayList.class.getMethod("moveAllFrom",IntArrayList.class));
        IntArrayList all=new IntArrayList();
        for(int chunk=0;chunk<7;chunk++) {
            IntArrayList source=new IntArrayList();
            for(int i=0;i<5003;i++)source.add(chunk*5003+i);
            move.invoke(all,source);assertTrue(source.isEmpty());
            source.add(-1);assertEquals(-1,source.get(0));
        }
        for(int i=0;i<35021;i++)assertEquals(i,all.get(i));
        all.set(4096,-77);assertEquals(-77,all.get(4096));
        all.removeLast();assertEquals(35020,all.size());
        assertEquals(4096,all.indexOf(-77));
        all.clear();all.add(19);assertEquals(19,all.get(0));
    }
}
