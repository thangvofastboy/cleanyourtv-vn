package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService), and the
 * Settings pill with {@code settings_pill_panel} (this fork). Its system rows come from config.xml's
 * quick_*_targets and are left out when the TV has none of their candidates. This fork draws the rows as cards
 * (CardStyle): a chip per row in the theme's tones, the skin's or theme's accent on the focused card, and puts Themes
 * and Edit dock first:
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            View panel = findViewById(R.id.panel);
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        Config cfg;
        try {
            cfg = Config.load(this);
        } catch (RuntimeException e) {
            cfg = null;
        }
        if (cfg != null && cfg.reducedMotion) overridePendingTransition(0, 0);
        style(cfg);
        LinearLayout rows = findViewById(R.id.rows);
        boolean showInputs = cfg == null || cfg.showInputsPill;
        Intent request = getIntent();
        boolean inputsKnown = request.hasExtra(EXTRA_HAS_INPUTS);
        boolean inputsFromHome = request.getBooleanExtra(EXTRA_HAS_INPUTS, false);
        if (inputsKnown && RESOLVED.size() == SYSTEM_TARGETS.length) {
            addRows(rows, showInputs && inputsFromHome);
            return;
        }
        // The first panel of this process asks PackageManager about every candidate (and, opened by
        // HomeKeyService rather than Home, TvInputManager about inputs): off the UI thread, rows added after.
        android.content.Context app = getApplicationContext();
        Config config = cfg;
        new Thread(() -> {
            boolean inputs = showInputs && inputsKnown && inputsFromHome;
            try {
                resolveAll(app);
                inputs = showInputs && (inputsKnown ? inputsFromHome : hasInputs(app, config));
            } catch (RuntimeException e) {
                // A PackageManager or resources failure must not kill the process: the rows that did resolve,
                // and the Launcher settings row, still show.
            }
            boolean withInputs = inputs;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) addRows(rows, withInputs);
            });
        }, "quick-settings").start();
    }

    /** Set by Home: whether its Inputs menu has anything to list (the Inputs pill is shown). */
    static final String EXTRA_HAS_INPUTS = "com.gathin.tvhome.extra.HAS_INPUTS";
    /** The config.xml candidate lists of the system rows. */
    private static final int[] SYSTEM_TARGETS = {R.array.quick_sound_targets, R.array.quick_sleep_timer_targets,
            R.array.quick_bluetooth_targets, R.array.quick_network_targets, R.array.quick_display_targets,
            R.array.quick_apps_targets, R.array.quick_tv_settings_targets};
    /** Per process: what each list resolved to, NONE when nothing on this TV opens. Cleared when a resolved row
     *  fails to open, so a settings app updated under a running process is resolved again. */
    private static final java.util.Map<Integer, Intent> RESOLVED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Intent NONE = new Intent();

    private void addRows(LinearLayout rows, boolean inputs) {
        // This fork: the theme picker and edit-dock mode first, both through Home (Launcher settings rebuilds Home
        // when it closes; edit-dock mode is Home's own).
        addRow(rows, R.drawable.ic_palette, R.string.theme_label, 0, new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_LAUNCHER_SETTINGS)
                .putExtra(LauncherSettingsActivity.EXTRA_OPEN_THEMES, true));
        addRow(rows, R.drawable.ic_dock, R.string.dock_edit, 0, new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_DOCK_EDIT));
        // System rows open the first candidate from config.xml the TV can open, and are left out when none can.
        addSystemRow(rows, R.drawable.ic_sound, R.string.quick_sound, R.array.quick_sound_targets, false);
        addSystemRow(rows, R.drawable.ic_timer, R.string.quick_sleep_timer, R.array.quick_sleep_timer_targets, false);
        addSystemRow(rows, R.drawable.ic_bluetooth, R.string.quick_bluetooth, R.array.quick_bluetooth_targets, false);
        // The launcher's own Inputs menu, through Home, as the remote's Input button does (InputsRequestActivity).
        if (inputs) {
            addRow(rows, R.drawable.ic_input, R.string.quick_inputs, 0, new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_VIEW_INPUTS));
        }
        addSystemRow(rows, R.drawable.ic_wifi, R.string.quick_network, R.array.quick_network_targets, false);
        addSystemRow(rows, R.drawable.ic_picture, R.string.quick_picture, R.array.quick_display_targets, false);
        addSystemRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.array.quick_apps_targets, true);
        addSystemRow(rows, R.drawable.ic_settings, R.string.tv_settings, R.array.quick_tv_settings_targets, false);
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    /** The card look (CardStyle): chips in the theme's tones, the skin's or theme's accent on the focused card. */
    private CardStyle cards;
    /** Chip colour order: each added row takes the next tone. */
    private int rowIndex;

    private void style(Config cfg) {
        cards = CardStyle.of(this, cfg);
        View panel = findViewById(R.id.panel);
        float d = getResources().getDisplayMetrics().density;
        // Translucent dark glass with a thin rim, for every theme (no blur, no shadow).
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE01A1D24, 0xE60F1116});
        glass.setCornerRadius(20 * d);
        glass.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        panel.setBackground(glass);
    }

    /** A system row from config.xml's candidates; nothing is added when none resolves on this TV. */
    private void addSystemRow(LinearLayout rows, int icon, int title, int candidates, boolean appsHint) {
        Intent target = RESOLVED.get(candidates);
        if (target == null || target == NONE) return;
        addRow(rows, icon, title, appsHint ? R.string.quick_apps_hint : 0, new Intent(target));
    }

    /** Resolves every system row's candidates once per process; any thread. */
    private static void resolveAll(android.content.Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String self = ctx.getPackageName();
        for (int candidates : SYSTEM_TARGETS) {
            if (RESOLVED.containsKey(candidates)) continue;
            String[] list = ctx.getResources().getStringArray(candidates);
            if (candidates == R.array.quick_tv_settings_targets) {
                String[] vendor = ctx.getResources().getStringArray(R.array.settings_fallback_components);
                String[] all = new String[list.length + vendor.length];
                System.arraycopy(list, 0, all, 0, list.length);
                System.arraycopy(vendor, 0, all, list.length, vendor.length);
                list = all;
            }
            QuickTargets.Target target = QuickTargets.first(list, t -> opens(pm, self, t));
            Intent intent = target == null ? NONE : target.isComponent()
                    ? new Intent().setComponent(new ComponentName(target.pkg, target.cls))
                    : new Intent(target.action);
            RESOLVED.put(candidates, intent);
        }
    }

    /** Whether the TV lists any input (TvInputManager), so Home's Inputs menu has something to open. */
    private static boolean hasInputs(android.content.Context ctx, Config cfg) {
        if (cfg != null && (!cfg.sourcePanelService.isEmpty() || cfg.coocaaSources)) return true;
        try {
            android.media.tv.TvInputManager tim = ctx.getSystemService(android.media.tv.TvInputManager.class);
            return tim != null && !tim.getTvInputList().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The TV has the activity, enabled and open to other apps, or something real answers the action. */
    private static boolean opens(PackageManager pm, String self, QuickTargets.Target t) {
        if (t.isComponent()) {
            try {
                ActivityInfo info = pm.getActivityInfo(new ComponentName(t.pkg, t.cls), 0);
                return info.exported && info.enabled && info.applicationInfo.enabled && allowed(pm, self, info);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        ResolveInfo r = pm.resolveActivity(new Intent(t.action), PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS answers standard actions with a compliance stub that opens nothing (SystemSettings).
        return r != null && r.activityInfo != null && !r.activityInfo.name.contains("CTSDummy")
                && allowed(pm, self, r.activityInfo);
    }

    /** An activity guarded by a permission the launcher does not hold would only answer with a SecurityException. */
    private static boolean allowed(PackageManager pm, String self, ActivityInfo info) {
        return info.permission == null
                || pm.checkPermission(info.permission, self) == PackageManager.PERMISSION_GRANTED;
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_quick_row, rows, false);
        cards.chipOn(row.findViewById(R.id.icon), icon, rowIndex++);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setBackground(cards.card());   // no focus lift here: the rows fill the panel, a scaled one would clip
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                // The TV's settings screens changed since they were resolved (an update without a restart of
                // this process): the next panel asks PackageManager again.
                RESOLVED.clear();
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
