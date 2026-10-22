package io.freemytv;

public final class DebloatPolicyTest {
    public static void main(String[] args) {
        check(DebloatPolicy.Action.DISABLE, true, false, true, true, false, false);
        check(DebloatPolicy.Action.NONE, true, false, true, true, true, false); // pre-disabled: don't adopt
        check(DebloatPolicy.Action.NONE, true, false, true, false, true, false); // don't restore another owner's app
        check(DebloatPolicy.Action.ENABLE, true, false, true, false, true, true);
        check(DebloatPolicy.Action.FORGET, true, false, false, false, true, true);
        check(DebloatPolicy.Action.FORGET, true, false, true, false, false, true);
        check(DebloatPolicy.Action.NONE, true, true, true, true, false, false);
        check(DebloatPolicy.Action.NONE, false, false, true, true, false, false);
        check(DebloatPolicy.Action.DISABLE, true, false, true, true, false, true); // external re-enable
        System.out.println("Debloat policy: 9 cases passed");
    }
    private static void check(DebloatPolicy.Action expected, boolean allow, boolean protect, boolean present,
                              boolean selected, boolean disabled, boolean owned) {
        DebloatPolicy.Action actual = DebloatPolicy.action(allow, protect, present, selected, disabled, owned);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }
}
