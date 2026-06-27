package com.seeloggyplus.controller;

import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for file manager sort encoding/decoding logic.
 * Tests the pure string encoding without JavaFX runtime.
 *
 * Validates: Requirements 1.2, 2.2, 3.1, 4.1, 4.2
 */
class SortEncodingPropertyTest {

    // Known column IDs from the file table (fx:id values)
    private static final List<String> COLUMN_IDS = List.of(
            "nameColumn", "modifiedColumn", "sizeColumn", "typeColumn"
    );

    private static final List<String> DIRECTIONS = List.of("ASCENDING", "DESCENDING");

    // ---- Encode/decode logic mirroring the controller ----

    /** Mirrors saveSortOrdering(): col.getId() + ":" + col.getSortType().name() */
    private String encode(String columnId, String direction) {
        return columnId + ":" + direction;
    }

    /** Mirrors restoreSortOrdering() decode: split on ":", validate parts */
    private Optional<String[]> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Optional.empty();
        String[] parts = encoded.split(":");
        if (parts.length != 2) return Optional.empty();
        if (!COLUMN_IDS.contains(parts[0])) return Optional.empty();
        if (!DIRECTIONS.contains(parts[1])) return Optional.empty();
        return Optional.of(parts);
    }

    /** Mirrors sortKey() logic */
    private String sortKey(String serverName) {
        return serverName == null
                ? "file_manager_sort_local"
                : "file_manager_sort_" + serverName;
    }

    // ---- Property 1: Sort ordering round-trip ----

    @Property(tries = 100)
    @Label("Feature: file-manager-recent-ordering, Property 1: Sort ordering round-trip")
    void roundTrip(@ForAll("validColumnIds") String columnId, @ForAll("validDirections") String direction) {
        // Validates: Requirements 1.2, 2.2
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

    // ---- Property 2: Location key uniqueness ----

    @Property(tries = 100)
    @Label("Feature: file-manager-recent-ordering, Property 2: Location key uniqueness")
    void locationKeyUniqueness(
            @ForAll("locationNames") String a,
            @ForAll("locationNames") String b
    ) {
        // Validates: Requirements 3.1
        // null represents local drive; non-null represents a server name
        Assume.that(!Objects.equals(a, b));
        assertThat(sortKey(a)).isNotEqualTo(sortKey(b));
    }

    @Provide
    Arbitrary<String> locationNames() {
        // null = local, non-null = server name
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
        );
    }

    // ---- Property 3: Malformed preference fallback ----

    @Property(tries = 100)
    @Label("Feature: file-manager-recent-ordering, Property 3: Malformed preference fallback")
    void malformedPreferenceFallback(@ForAll("malformedStrings") String input) {
        // Validates: Requirements 4.1, 4.2
        // decode must return empty (fallback) without throwing
        Optional<String[]> result = decode(input);
        assertThat(result).isEmpty();
    }

    @Provide
    Arbitrary<String> malformedStrings() {
        return Arbitraries.oneOf(
                // Blank strings
                Arbitraries.of("", "   ", "\t", "\n"),
                // No colon delimiter
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                        .filter(s -> !s.contains(":")),
                // Colon but unknown column
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .filter(s -> !COLUMN_IDS.contains(s))
                        .map(s -> s + ":ASCENDING"),
                // Valid column but invalid direction
                Arbitraries.of(COLUMN_IDS)
                        .map(col -> col + ":INVALID_DIR"),
                // Multiple colons
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                        .map(s -> s + ":" + s + ":" + s),
                // Random unicode garbage
                Arbitraries.strings().ofMinLength(0).ofMaxLength(50)
                        .filter(s -> {
                            String[] parts = s.split(":");
                            // Exclude anything that would accidentally be valid
                            return parts.length != 2
                                    || !COLUMN_IDS.contains(parts[0])
                                    || !DIRECTIONS.contains(parts[1]);
                        })
        );
    }
}
