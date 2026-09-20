package com.seeloggyplus.update;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {

    @Test
    void comparesMajorMinorPatchNumerically() {
        assertTrue(VersionComparator.compare("0.2.0", "0.10.0") < 0);
        assertTrue(VersionComparator.compare("1.0.0", "0.9.9") > 0);
        assertEquals(0, VersionComparator.compare("1.2.3", "1.2.3"));
    }

    @Test
    void missingSegmentsDefaultToZero() {
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"));
        assertTrue(VersionComparator.compare("1.2", "1.2.1") < 0);
    }

    @Test
    void releaseIsNewerThanPrerelease() {
        assertTrue(VersionComparator.compare("1.0.0", "1.0.0-beta") > 0);
        assertTrue(VersionComparator.compare("1.0.0-alpha", "1.0.0") < 0);
    }

    @Test
    void prereleaseIdentifiersFollowSemver() {
        assertTrue(VersionComparator.compare("1.0.0-alpha", "1.0.0-beta") < 0);
        assertTrue(VersionComparator.compare("1.0.0-beta.2", "1.0.0-beta.1") > 0);
        assertTrue(VersionComparator.compare("1.0.0-1", "1.0.0-alpha") < 0); // numeric < alphanumeric
    }

    @Test
    void buildMetadataIsIgnored() {
        assertEquals(0, VersionComparator.compare("1.0.0+build.5", "1.0.0"));
        assertEquals(0, VersionComparator.compare("1.0.0+1", "1.0.0+2"));
    }

    @Test
    void handlesNullAndBlankAsZero() {
        assertEquals(0, VersionComparator.compare(null, ""));
        assertEquals(0, VersionComparator.compare("0.0.0", null));
        assertTrue(VersionComparator.compare("DEV", "1.0.0") < 0);
    }

    @Property
    void identicalVersionsAreEqual(@ForAll @IntRange(min = 0, max = 99) int major,
                                   @ForAll @IntRange(min = 0, max = 99) int minor,
                                   @ForAll @IntRange(min = 0, max = 99) int patch) {
        String version = major + "." + minor + "." + patch;
        assertEquals(0, VersionComparator.compare(version, version));
    }

    @Property
    void comparisonIsAntisymmetric(@ForAll("versions") String a, @ForAll("versions") String b) {
        assertEquals(Integer.signum(VersionComparator.compare(a, b)),
                -Integer.signum(VersionComparator.compare(b, a)));
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> versions() {
        return net.jqwik.api.Arbitraries.integers().between(0, 50)
                .flatMap(major -> net.jqwik.api.Arbitraries.integers().between(0, 50)
                        .flatMap(minor -> net.jqwik.api.Arbitraries.integers().between(0, 50)
                                .map(patch -> major + "." + minor + "." + patch)));
    }
}
