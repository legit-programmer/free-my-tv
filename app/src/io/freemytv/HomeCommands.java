package io.freemytv;

import java.io.IOException;

/** HOME resolution shared by old Android's package manager and newer role-based systems. */
final class HomeCommands {
    interface Shell { String run(String command) throws IOException; }
    private static final String OWN = "io.freemytv";
    private static final String RESOLVE = "cmd package resolve-activity --brief --user 0"
            + " -a android.intent.action.MAIN -c android.intent.category.HOME";
    private HomeCommands() { }

    static String current(Shell shell, int api) throws IOException {
        String resolved = component(shell.run(RESOLVE));
        if (!resolved.isEmpty()) return resolved;
        // Android 9 has no role service. Don't interpret its command error as a package name.
        if (api < 29) return "";
        String holder = shell.run("cmd role get-role-holders --user 0 android.app.role.HOME").trim();
        if (!holder.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) return "";
        String activities = shell.run("cmd package query-activities --brief --user 0"
                + " -a android.intent.action.MAIN -c android.intent.category.HOME");
        for (String line : activities.split("\n")) {
            String candidate = component(line);
            if (candidate.startsWith(holder + "/")) return candidate;
        }
        return "";
    }

    static String component(String output) {
        for (String line : output.split("\n")) {
            String value = line.trim();
            if (value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")
                    && !value.endsWith("ResolverActivity") && !value.endsWith("ChooserActivity")
                    && !value.endsWith("FallbackHome")) return value;
        }
        return "";
    }

    static boolean releaseOwnRole(Shell shell, int api) throws IOException {
        if (api >= 29) shell.run("cmd role remove-role-holder --user 0 android.app.role.HOME " + OWN);
        String home = current(shell, api);
        return !home.isEmpty() && !home.startsWith(OWN + "/");
    }
}
