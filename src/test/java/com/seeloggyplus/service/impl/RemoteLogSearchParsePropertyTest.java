package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.RemoteLogSearchMatch;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Property-based test: any generated {@code path + ":" + line + ":" + content}
 * must round-trip through the parser unchanged, as long as the path has no colon.
 */
class RemoteLogSearchParsePropertyTest {

    @Property
    void roundTripsPathLineAndContent(@ForAll("paths") String path,
                                      @ForAll("lines") int line,
                                      @ForAll("contents") String content) {
        RemoteLogSearchMatch match = RemoteLogSearchServiceImpl.parseMatchLine(path + ":" + line + ":" + content);

        assertNotNull(match, "Generated match must parse");
        assertEquals(path, match.path());
        assertEquals(line, match.lineNumber());
        assertEquals(content, match.content());
    }

    @Provide
    Arbitrary<String> paths() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('/', '.', '_', '-', ' ')
                .ofMinLength(1)
                .ofMaxLength(40)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<Integer> lines() {
        return Arbitraries.integers().between(1, 2_000_000);
    }

    @Provide
    Arbitrary<String> contents() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars(':', ' ', '-', '=', '.', ',')
                .ofMinLength(0)
                .ofMaxLength(60)
                .filter(s -> !s.contains("\n") && !s.contains("\r"));
    }
}
