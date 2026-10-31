package com.mojang.realmsclient;

import java.util.Locale;

public enum Unit {
    B,
    KB,
    MB,
    GB;

    private static final int BASE_UNIT = 1024;

    public static Unit getLargest(long bytes) {
        if (bytes < 1024L) {
            return B;
        }

        try {
            int i = (int)(Math.log(bytes) / Math.log(1024.0));
            String string = String.valueOf("KMGTPE".charAt(i - 1));
            return valueOf(string + "B");
        } catch (Exception exception) {
            return GB;
        }
    }

    public static double convertTo(long bytes, Unit unit) {
        return unit == B ? bytes : bytes / Math.pow(1024.0, unit.ordinal());
    }

    public static String humanReadable(long bytes) {
        int i = 1024;
        if (bytes < 1024L) {
            return bytes + " B";
        }

        int j = (int)(Math.log(bytes) / Math.log(1024.0));
        String string = "KMGTPE".charAt(j - 1) + "";
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, j), string);
    }

    public static String humanReadable(long bytes, Unit unit) {
        return String.format(Locale.ROOT, "%." + (unit == GB ? "1" : "0") + "f %s", convertTo(bytes, unit), unit.name());
    }
}
