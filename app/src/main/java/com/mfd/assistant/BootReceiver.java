package com.mfd.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * MFD - Mehmet Fatih DURSUN
 * Önyükleme alıcısı — cihaz açıldığında wake-word servisini başlatır.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        AppConfig cfg = new AppConfig(context);
        if (!cfg.isAutostartBoot() || !cfg.isWakewordEnabled()) return;
        Intent svc = new Intent(context, WakeWordService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception ignored) {}
    }
}
