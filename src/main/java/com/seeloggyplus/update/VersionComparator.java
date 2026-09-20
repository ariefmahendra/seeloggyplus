package com.seeloggyplus.update;

/**
 * Minimal Semantic Versioning comparator.
 * Handles {@code major.minor.patch}, prerelease (e.g. {@code -beta.1}) and build metadata
 * ({@code +build}) which is ignored during comparison.
 */
public final class VersionComparator {

    private VersionComparator() {
    }

    /** @return negative if {@code a < b}, zero if equal, positive if {@code a > b}. */
    public static int compare(String a, String b) {
        int[] coreA = core(a);
        int[] coreB = core(b);
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(coreA[i], coreB[i]);
            if (c != 0) {
                return c;
            }
        }
        String preA = prerelease(a);
        String preB = prerelease(b);
        if (preA.isEmpty() && preB.isEmpty()) {
            return 0;
        }
        if (preA.isEmpty()) {
            return 1; // release is newer than prerelease
        }
        if (preB.isEmpty()) {
            return -1;
        }
        return comparePrerelease(preA, preB);
    }

    static int[] core(String version) {
        int[] result = {0, 0, 0};
        if (version == null || version.isBlank()) {
            return result;
        }
        String value = version.trim();
        int plus = value.indexOf('+');
        if (plus >= 0) {
            value = value.substring(0, plus);
        }
        int dash = value.indexOf('-');
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        String[] parts = value.split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            result[i] = parseNumber(parts[i]);
        }
        return result;
    }

    static String prerelease(String version) {
        if (version == null) {
            return "";
        }
        String value = version.trim();
        int plus = value.indexOf('+');
        if (plus >= 0) {
            value = value.substring(0, plus);
        }
        int dash = value.indexOf('-');
        return dash >= 0 ? value.substring(dash + 1) : "";
    }

    private static int parseNumber(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int comparePrerelease(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            if (i >= partsA.length) {
                return -1;
            }
            if (i >= partsB.length) {
                return 1;
            }
            String identifierA = partsA[i];
            String identifierB = partsB[i];
            boolean numericA = isNumeric(identifierA);
            boolean numericB = isNumeric(identifierB);
            if (numericA && numericB) {
                int c = Long.compare(Long.parseLong(identifierA), Long.parseLong(identifierB));
                if (c != 0) {
                    return c;
                }
            } else if (numericA) {
                return -1; // numeric identifiers have lower precedence
            } else if (numericB) {
                return 1;
            } else {
                int c = identifierA.compareTo(identifierB);
                if (c != 0) {
                    return c;
                }
            }
        }
        return 0;
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
