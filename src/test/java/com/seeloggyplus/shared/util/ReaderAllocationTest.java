package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class ReaderAllocationTest {
    @Test void readingRepeatedLinesDoesNotAllocateAByteBufferPerLine() throws Exception {
        Path directory=Path.of("build/performance/test-fixtures");Files.createDirectories(directory);
        Path file=Files.createTempFile(directory,"reader",".log");
        Files.writeString(file,"POST "+"x".repeat(1018)+"\n");
        var index=new LineOffsetIndex();try(var input=new RandomAccessFile(file.toFile(),"r")){index.buildIndex(input,null);}
        try(var reader=new MappedFileReader(file.toFile())) {
            for(int i=0;i<10000;i++)reader.readLine(index,0);
            var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
            int sum=0;for(int i=0;i<2000;i++)sum+=reader.readLine(index,0).length();
            long allocated=bean.getThreadAllocatedBytes(thread)-before;
            assertEquals(2046000,sum);
            assertTrue(allocated<3_000_000,"reader allocated "+allocated+" bytes for 2000 1KiB lines");
        }
    }

    @Test void literalMatchingPreservesUnicodeLocaleAndChunkBoundaries() throws Exception {
        var method=assertDoesNotThrow(() -> MappedFileReader.class.getMethod("lineContainsLiteral",LineOffsetIndex.class,int.class,String.class,boolean.class));
        Path directory=Path.of("build/performance/test-fixtures");Files.createDirectories(directory);
        Path file=Files.createTempFile(directory,"literal",".log");
        Files.writeString(file,"POST /example\r\nKelvin \u212a value\nI\n\u0130\n");
        var index=new LineOffsetIndex();try(var input=new RandomAccessFile(file.toFile(),"r")){index.buildIndex(input,null);}
        Locale previous=Locale.getDefault();
        try(var reader=new MappedFileReader(file.toFile(),java.nio.charset.StandardCharsets.UTF_8,7)) {
            assertEquals(true,method.invoke(reader,index,0,"post",false));
            assertEquals(false,method.invoke(reader,index,0,"post",true));
            assertEquals(true,method.invoke(reader,index,1,"kelvin k",false));
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(false,method.invoke(reader,index,2,"i",false));
            assertEquals(true,method.invoke(reader,index,3,"i",false));
        }finally{Locale.setDefault(previous);}
    }
}
