package app.sotto.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import app.sotto.voice.core.Settings;

/** Re-arms the bubble after a reboot when the user asked for it. */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Settings settings = new Settings(context);
        if (!settings.isStartOnBoot() || !settings.isBubble()) return;
        if (!android.provider.Settings.canDrawOverlays(context)) return;
        try {
            Intent service = new Intent(context, SottoService.class);
            service.setAction(SottoService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Throwable ignored) {
        }
    }
}
