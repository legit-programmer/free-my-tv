package io.freemytv;

/** Pure decision logic shared by optional package changes and JVM tests. */
final class DebloatPolicy {
    enum Action { NONE, DISABLE, ENABLE, FORGET }
    static Action action(boolean allowed, boolean protectedPackage, boolean present,
                         boolean selected, boolean disabled, boolean owned) {
        if (!allowed || protectedPackage) return Action.NONE;
        if (!present) return owned ? Action.FORGET : Action.NONE;
        if (selected) return disabled ? Action.NONE : Action.DISABLE;
        if (!owned) return Action.NONE;
        return disabled ? Action.ENABLE : Action.FORGET;
    }
}
