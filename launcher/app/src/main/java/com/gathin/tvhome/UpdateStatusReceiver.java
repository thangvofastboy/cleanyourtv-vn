package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/**
 * PackageInstaller's answer to the update's commit (UpdateClient#install), delivered by an explicit intent to this
 * non-exported receiver: Android's confirm screen when it asks for one, then the outcome, shown on the Launcher
 * settings row when that screen is up and as a toast otherwise.
 */
public final class UpdateStatusReceiver extends BroadcastReceiver {

    /** The open Launcher settings screen's row, or null; set and cleared on the main thread. */
    interface Listener {
        void onUpdateStatus(String text);
    }

    static Listener listener;

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    // Still waiting: the confirm screen is up now, give it the full timeout from here.
                    UpdateClient.CONFIRM_SINCE.set(Math.max(1, android.os.SystemClock.elapsedRealtime()));
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                }
            }
            status = PackageInstaller.STATUS_FAILURE;
        }
        UpdateClient.CONFIRM_SINCE.set(0);
        String text;
        if (status == PackageInstaller.STATUS_SUCCESS) {
            text = context.getString(R.string.update_latest, BuildConfig.VERSION_NAME);
        } else {
            String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            text = context.getString(R.string.update_failed, why != null && !why.isEmpty() && why.length() < 80
                    ? why : context.getString(R.string.update_err_install));
        }
        Listener l = listener;
        if (l != null) l.onUpdateStatus(text);
        else Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
}
