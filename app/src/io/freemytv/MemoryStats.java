package io.freemytv;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** dumpsys uses TOTAL: on Android 9 and TOTAL PSS: on newer releases. Values are KiB. */
final class MemoryStats {
    private static final Pattern SUMMARY = Pattern.compile("(?m)^\\s*TOTAL(?: PSS)?:\\s*(\\d+)");
    private static final Pattern TABLE = Pattern.compile("(?m)^\\s*TOTAL\\s+(\\d+)\\s");
    private MemoryStats() { }
    static long pss(String output) {
        Matcher m = SUMMARY.matcher(output);
        if (m.find()) return Long.parseLong(m.group(1));
        m = TABLE.matcher(output);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }
}
