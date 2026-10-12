package com.seeloggyplus.app;

import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SortEncodingPropertyTest {

    private static final List<String> COLUMN_IDS = List.of(
            "nameColumn", "modifiedColumn", "sizeColumn", "typeColumn"
    );

    private static final List<String> DIRECTIONS = List.of("ASCENDING", "DESCENDING");

    private String encode(String columnId, String direction) {
        return columnId + ":" + direction;
    }

    private Optional<String[]> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Optional.empty();
        String[] parts = encoded.split(":");
        if (parts.length != 2) return Optional.empty();
        if (!COLUMN_IDS.contains(parts[0])) return Optional.empty();
        if (!DIRECTIONS.contains(parts[1])) return Optional.empty();
        return Optional.of(parts);
    }

    private String sortKey(String serverName) {
        return serverName == null
                ? "file_manager_sort_local"
                : "file_manager_sort_" + serverName;
    }

    @Property(tries = 100)
    @Label("Feature: file-manager-recent-ordering, Property 1: Sort ordering round-trip")
    void roundTrip(@ForAll("validColumnIds") String columnId, @ForAll("validDirections") String direction) {
        String encoded = encode(columnId, direction);
        Optional<String[]> decoded = decode(encoded);

        assertThat(decoded).isPresent();
        assertThat(decoded.get()[0]).isEqualTo(columnId);
        assertThat(decoded.get()[1]).isEqualTo(direction);
    }

    @Provide
    Arbitrary<String> validColumnIds() {
        return Arbitraries.of(COLUMN_IDS);
    }

    @Provide
    Arbitrary<String> validDirections() {
        return Arbitraries.of(DIRECTIONS);
    }

    @Property(tries = 100)
    @Label("Feature: file-manager-recent-ordering, Property 2: Location key uniqueness")
    void locationKeyUniqueness(
            @ForAll("locationNames") String a,
            @ForAll("locationNames") String b
    ) {
        Assume.that(!Objects.equals(a, b));
        assertThat(sortKey(a)).isNotEqualTo(sortKey(b));
    }

    @Provide
    Arbitrary<String> locationNames() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
        );
    }

    @Property(tries = 100)
    @Label("Feature: file-manager-recent-ordering, Property 3: Malformed preference fallback")
    void malformedPreferenceFallback(@ForAll("malformedStrings") String input) {
        Optional<String[]> result = decode(input);
        assertThat(result).isEmpty();
    }

    @Provide
    Arbitrary<String> malformedStrings() {
        return Arbitraries.oneOf(
                Arbitraries.of("", "   ", "\t", "\n"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                        .filter(s -> !s.contains(":")),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .filter(s -> !COLUMN_IDS.contains(s))
                        .map(s -> s + ":ASCENDING"),
                Arbitraries.of(COLUMN_IDS)
                        .map(col -> col + ":INVALID_DIR"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                        .map(s -> s + ":" + s + ":" + s),
                Arbitraries.strings().ofMinLength(0).ofMaxLength(50)
                        .filter(s -> {
                            String[] parts = s.split(":");

                            return parts.length != 2
                                    || !COLUMN_IDS.contains(parts[0])
                                    || !DIRECTIONS.contains(parts[1]);
                        })
        );
    }
}
