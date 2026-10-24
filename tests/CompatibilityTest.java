package io.freemytv;

import java.io.IOException;

public final class CompatibilityTest {
    private static int cases;
    public static void main(String[] args) throws Exception {
        eq("com.stock/.Home", HomeCommands.current(cmd -> {
            if (cmd.contains("role")) throw new AssertionError("Android 9 must not use role commands");
            return "priority=0\ncom.stock/.Home\n";
        }, 28));
        eq("", HomeCommands.current(cmd -> "android/com.android.internal.app.ResolverActivity", 28));
        eq("", HomeCommands.current(cmd -> "No activity found", 28));
        eq("", HomeCommands.current(cmd -> "Error: activity not found", 33));
        eq("com.stock/.Home", HomeCommands.current(cmd -> cmd.contains("get-role-holders") ? "com.stock\n"
                : cmd.contains("query-activities") ? "io.freemytv/.HomeActivity\ncom.stock/.Home\n"
                : "No activity found", 33));
        eq("", HomeCommands.component("com.stock/.Home;reboot"));
        eq("", HomeCommands.component("Error/nope"));
        eq("", HomeCommands.component("com.stock/.ChooserActivity"));
        eq("io.freemytv/io.freemytv.HomeActivity", HomeCommands.component("io.freemytv/io.freemytv.HomeActivity"));
        eq(true, HomeCommands.releaseOwnRole(cmd -> {
            if (cmd.contains("role")) throw new AssertionError("No role on Android 9");
            return "com.stock/.Home";
        }, 28));
        eq(false, HomeCommands.releaseOwnRole(cmd -> "No activity found", 28));
        eq(false, HomeCommands.releaseOwnRole(cmd -> "io.freemytv/.HomeActivity", 28));
        eq(12800L, MemoryStats.pss("App Summary\n TOTAL:    12800      TOTAL SWAP PSS: 0\n"));
        eq(24800L, MemoryStats.pss(" TOTAL PSS: 24800 TOTAL RSS: 30200\n"));
        eq(12800L, MemoryStats.pss("       TOTAL    12800  500  200  0  100\n"));
        eq(0L, MemoryStats.pss("No process found for: io.freemytv"));
        System.out.println("Android compatibility: " + cases + " cases passed");
    }
    private static void eq(Object expected, Object actual) {
        cases++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
}
