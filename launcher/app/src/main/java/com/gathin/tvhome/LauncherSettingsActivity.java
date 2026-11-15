package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    /** Result (and Home's pending action): rebuild, then start edit-dock mode, as the hold-OK menu's Edit dock. */
    static final String EXTRA_DOCK_EDIT = "com.gathin.tvhome.extra.DOCK_EDIT_FROM_SETTINGS";
    /** Open the theme picker as soon as this screen is up (the quick panel's Themes row, through Home). */
    static final String EXTRA_OPEN_THEMES = "com.gathin.tvhome.extra.OPEN_THEMES";
    private CardStyle style;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView themeChoice;
    private TextView updateChoice;
    /** The update found by the last check, offered on the next press; null before a check or when up to date. */
    private Updates.Candidate update;
    /** The bundled theme last applied from this screen ("" for the built-in config), kept for the row's label. */
    static final String BUNDLED_THEME_KEY = "bundled_theme";
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        updateChoice = findViewById(R.id.updateChoice);
        updateChoice.setOnClickListener(v -> onUpdatePressed());
        findViewById(R.id.stockLauncherChoice).setOnClickListener(v -> showStockLauncherHelp());
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        style = CardStyle.of(this, config);
        // Both always here (this fork): Home rebuilds, then starts the mode, as from the Settings pill's hold-OK menu.
        TextView reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        TextView dockEditChoice = findViewById(R.id.dockEditChoice);
        dockEditChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_DOCK_EDIT, true));
            finish();
        });
        card(dockEditChoice, R.drawable.ic_dock, ROW_DOCK_EDIT, getString(R.string.dock_edit_desc), false);
        card(reorderChoice, R.drawable.ic_swap_vert, ROW_REORDER, getString(R.string.reorder_desc), false);
        style.caption(this, findViewById(R.id.clockCaption), R.drawable.ic_clock, ROW_CLOCK);
        style.caption(this, findViewById(R.id.columnsCaption), R.drawable.ic_grid, ROW_COLUMNS);
        style.caption(this, findViewById(R.id.rowsCaption), R.drawable.ic_rows, ROW_ROWS);
        style.caption(this, findViewById(R.id.dockOpacityCaption), R.drawable.ic_opacity, ROW_OPACITY);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        TextView defaultHome = findViewById(R.id.defaultHomeChoice);
        defaultHome.setOnClickListener(v -> open(new Intent(this, HomeSettingsActivity.class)));
        TextView tvSettings = findViewById(R.id.tvSettingsChoice);
        tvSettings.setOnClickListener(v -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        card(followThemeChoice, R.drawable.ic_refresh, ROW_FOLLOW, getString(R.string.follow_theme_hint), false);
        card(defaultHome, R.drawable.ic_home, ROW_HOME, getString(R.string.home_settings_help), false);
        card(tvSettings, R.drawable.ic_settings, ROW_TV, getString(R.string.tv_settings_desc), false);
        card(findViewById(R.id.stockLauncherChoice), R.drawable.ic_exit, ROW_STOCK,
                getString(R.string.stock_return_desc), false);
        showUpdate(getString(R.string.update_version, BuildConfig.VERSION_NAME), false);
        for (TextView[] group : new TextView[][]{columnChoices, rowChoices, dockChoices, clockChoices}) {
            for (TextView pill : group) pill.setBackground(style.pill());
        }
        refresh();
        themeChoice.requestFocus();
        // The quick panel's Themes row: straight to the picker, once (not again after a recreate).
        if (state == null && getIntent().getBooleanExtra(EXTRA_OPEN_THEMES, false)) {
            getIntent().removeExtra(EXTRA_OPEN_THEMES);
            themeChoice.post(() -> {
                if (!isFinishing() && !isDestroyed()) showThemePicker();
            });
        }
    }

    /** Chip colour order of the rows, top to bottom, so neighbours take different tones. */
    private static final int ROW_THEME = 0, ROW_WALLPAPER = 1, ROW_CLOCK = 2, ROW_DOCK_EDIT = 3, ROW_REORDER = 4,
            ROW_COLUMNS = 5, ROW_ROWS = 6, ROW_OPACITY = 7, ROW_OPEN_ON_START = 8, ROW_ANIMATIONS = 9,
            ROW_ADVANCED = 10, ROW_FOLLOW = 11, ROW_HOME = 12, ROW_TV = 13, ROW_STOCK = 14, ROW_UPDATE = 15;

    /** A row as a card: the layout's text stays the title, {@code sub} goes on the line under it. */
    private void card(TextView row, int icon, int index, CharSequence sub, boolean isValue) {
        card(row, icon, index, sub, isValue, null);
    }

    private void card(TextView row, int icon, int index, CharSequence sub, boolean isValue, CharSequence badge) {
        CharSequence title = (CharSequence) row.getTag(R.id.card_title);
        if (title == null) {
            title = row.getText().toString();
            row.setTag(R.id.card_title, title);
        }
        style.row(this, row, icon, index, title, sub, isValue, badge);
    }

    /** The update row's second line; {@code available} adds the "New" badge in the theme's second colour. */
    private void showUpdate(CharSequence text, boolean available) {
        card(updateChoice, R.drawable.ic_download, ROW_UPDATE, text, true,
                available ? getString(R.string.update_new) : null);
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * The themes bundled in the APK, after the built-in config.xml. OK writes the same files tools/theme.sh pushes
     * (off the main thread), then Home rebuilds with them when this screen closes: the theme revision and the
     * settings change count both move, so Home's onResume recreates it and Config drops the on-TV choices.
     */
    private void showThemePicker() {
        java.util.Map<String, List<String>> themes = ThemeInstaller.themes(this);
        org.json.JSONObject overlay = ThemeInstaller.overlay(this);
        List<String> names = new ArrayList<>(themes.keySet());
        String[] labels = new String[names.size() + 1];
        labels[0] = getString(R.string.theme_builtin);
        for (int i = 0; i < names.size(); i++) labels[i + 1] = ThemeBundle.label(names.get(i), overlay);
        String current = choices.getString(BUNDLED_THEME_KEY, null);
        int checked = current == null ? -1 : current.isEmpty() ? 0
                : names.contains(current) ? names.indexOf(current) + 1 : -1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyTheme(which == 0 ? null : names.get(which - 1));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Writes the theme on a worker. The worker itself records the result in the settings with commit(), so Home
     * rebuilds even when this screen was closed meanwhile: on success and on a failure after the folder changed
     * (the new config.json is the first file swapped in, so the theme's name is what Home now reads), the theme's
     * name is saved and the change count moves. A failure while staging changed nothing and saves nothing.
     */
    private void applyTheme(String name) {
        if (!ThemeInstaller.BUSY.compareAndSet(false, true)) {
            Toast.makeText(this, R.string.theme_applying, Toast.LENGTH_SHORT).show();
            return;
        }
        themeChoice.setEnabled(false);
        card(themeChoice, R.drawable.ic_palette, ROW_THEME, getString(R.string.theme_applying), true);
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            ThemeInstaller.Result result;
            try {
                try {
                    result = ThemeInstaller.apply(app, name);
                } catch (RuntimeException e) {
                    // Unknown how far it got: let Home re-read whatever the folder now holds.
                    result = new ThemeInstaller.Result(name == null ? Config.FILE_NAME : name, true);
                }
                if (result.changed) {
                    SharedPreferences prefs = app.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(BUNDLED_THEME_KEY, name == null ? "" : name)
                            .putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                            .commit();
                }
            } finally {
                ThemeInstaller.BUSY.set(false);
            }
            ThemeInstaller.Result done = result;
            runOnUiThread(() -> {
                if (done.failed != null) {
                    Toast.makeText(app, getString(R.string.theme_failed, done.failed), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(app, R.string.theme_applied, Toast.LENGTH_SHORT).show();
                }
                if (isFinishing() || isDestroyed()) return;
                themeChoice.setEnabled(true);
                if (done.failed != null) {
                    refresh();
                    return;
                }
                setResult(RESULT_OK);
                finish();
            });
        }, "theme-apply").start();
    }

    /**
     * Going back to the TV's own launcher takes adb: Home stays on this launcher and nothing here changes that.
     * The dialog names the commands for this TV's stock launcher (StockLauncher) and where the steps are written.
     */
    private void showStockLauncherHelp() {
        String stock = StockLauncher.find(this, Config.load(this).stockHomeRedirect);
        StringBuilder text = new StringBuilder(getString(R.string.stock_return_intro)).append("\n\n");
        for (String command : StockLauncher.commands(stock, getPackageName())) text.append(command).append('\n');
        text.append('\n').append(getString(R.string.stock_return_notes, getPackageName()))
                .append("\n\n").append(StockLauncher.HELP_URL);
        new AlertDialog.Builder(this)
                .setTitle(R.string.stock_return_label)
                .setMessage(text.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---- in-app update (this fork): network only after this press ----------------------------------------

    /** This screen's listener, kept so onStop clears the static only when a newer screen has not replaced it. */
    private final UpdateStatusReceiver.Listener updateListener = text -> {
        update = null;
        if (updateChoice != null) showUpdate(text, false);
    };

    @Override
    protected void onStart() {
        super.onStart();
        UpdateStatusReceiver.listener = updateListener;
    }

    @Override
    protected void onStop() {
        if (UpdateStatusReceiver.listener == updateListener) UpdateStatusReceiver.listener = null;
        super.onStop();
    }

    /** Shows {@code text} on the update row if this screen is still up; any thread. */
    private void updateRow(String text) {
        updateRow(text, false);
    }

    private void updateRow(String text, boolean available) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) showUpdate(text, available);
        });
    }

    private void onUpdatePressed() {
        if (UpdateClient.confirmPending()) {
            showUpdate(getString(R.string.update_waiting_confirm), false);
            return;
        }
        if (update != null && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_unknown_sources, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException | SecurityException e) {
                showUpdate(getString(R.string.update_unknown_sources_adb), true);
            }
            return;
        }
        // One check, download or install per process: a second press, or a recreated screen, waits for it.
        if (!UpdateClient.BUSY.compareAndSet(false, true)) return;
        android.content.Context app = getApplicationContext();
        Updates.Candidate target = update;
        if (target == null) {
            showUpdate(getString(R.string.update_checking), false);
            new Thread(() -> {
                try {
                    Updates.Candidate found = UpdateClient.check();
                    runOnUiThread(() -> update = found);
                    updateRow(found == null ? app.getString(R.string.update_latest, BuildConfig.VERSION_NAME)
                            : app.getString(R.string.update_available, found.version), found != null);
                } catch (UpdateClient.Failure f) {
                    updateRow(app.getString(R.string.update_failed, app.getString(f.message)));
                } finally {
                    UpdateClient.BUSY.set(false);
                }
            }, "update-check").start();
            return;
        }
        showUpdate(getString(R.string.update_downloading, 0), false);
        new Thread(() -> {
            UpdateClient.Download apk = null;
            try {
                apk = UpdateClient.download(app, target,
                        pct -> updateRow(app.getString(R.string.update_downloading, pct)));
                updateRow(app.getString(R.string.update_installing));
                UpdateClient.install(app, apk);
                // Committed: this candidate is spent; the final status (or a new check) decides what comes next.
                runOnUiThread(() -> update = null);
            } catch (UpdateClient.Failure f) {
                runOnUiThread(() -> update = null);
                updateRow(app.getString(R.string.update_failed, app.getString(f.message)));
            } finally {
                if (apk != null) apk.file.delete();   // this worker's own file; the session holds its own copy
                UpdateClient.BUSY.set(false);
            }
        }, "update-download").start();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        card(animationsChoice, R.drawable.ic_sparkle, ROW_ANIMATIONS, getString(animations ? R.string.on : R.string.off),
                true);
        card(advancedChoice, R.drawable.ic_bolt, ROW_ADVANCED, getString(advancedEffects ? R.string.on : R.string.off),
                true);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        card(wallpaperChoice, R.drawable.ic_picture, ROW_WALLPAPER,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default), true);
        card(openOnStartChoice, R.drawable.ic_play, ROW_OPEN_ON_START, openOnStartLabel(), true);
        card(themeChoice, R.drawable.ic_palette, ROW_THEME, themeLabel(), true);
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    /** The row's current theme: the one applied here, the built-in config, or "pushed" for anything else. */
    private String themeLabel() {
        String name = choices.getString(BUNDLED_THEME_KEY, null);
        File dir = Config.dir(this);
        boolean overlay = dir != null && new File(dir, Config.FILE_NAME).isFile();
        if (name == null || (name.isEmpty() == overlay)) {
            return getString(overlay ? R.string.theme_pushed : R.string.theme_builtin);
        }
        return name.isEmpty() ? getString(R.string.theme_builtin) : ThemeBundle.label(name, ThemeInstaller.overlay(this));
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
