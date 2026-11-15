package com.gathin.tvhome;

import java.util.function.Predicate;

/**
 * The quick settings panel's system rows: each row is a list of candidates from config.xml (quick_*_targets),
 * tried in order, and the first one the TV can open is the row's target; a row with none is left out. A candidate
 * is either a settings action ({@code android.settings.SOUND_SETTINGS}) or an activity as {@code package/class}
 * (a class starting with '.' is relative to the package). Pure logic; the panel supplies the check.
 */
final class QuickTargets {

    private QuickTargets() {}

    static final class Target {
        /** The action, or null for a component. */
        final String action;
        /** The component's package and full class name, or null for an action. */
        final String pkg, cls;

        private Target(String action, String pkg, String cls) {
            this.action = action;
            this.pkg = pkg;
            this.cls = cls;
        }

        boolean isComponent() {
            return pkg != null;
        }

        @Override
        public String toString() {
            return isComponent() ? pkg + "/" + cls : action;
        }
    }

    /** One candidate, or null for a blank or malformed one. */
    static Target parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty() || v.contains(" ")) return null;
        int slash = v.indexOf('/');
        if (slash < 0) return v.contains(".") ? new Target(v, null, null) : null;
        if (slash != v.lastIndexOf('/')) return null;
        String pkg = v.substring(0, slash), cls = v.substring(slash + 1);
        if (pkg.isEmpty() || cls.isEmpty() || !pkg.contains(".")) return null;
        if (cls.startsWith(".")) cls = pkg + cls;
        if (cls.endsWith(".")) return null;
        return new Target(null, pkg, cls);
    }

    /** The first candidate that parses and that {@code available} accepts, or null: the row is then hidden. */
    static Target first(String[] candidates, Predicate<Target> available) {
        if (candidates == null) return null;
        for (String c : candidates) {
            Target t = parse(c);
            if (t == null) continue;
            try {
                if (available.test(t)) return t;
            } catch (RuntimeException ignored) {
                // a check that throws (a package the TV hides from us, say) is a candidate that is not there
            }
        }
        return null;
    }
}
