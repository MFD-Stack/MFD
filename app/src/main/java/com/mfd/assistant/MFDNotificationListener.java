package com.mfd.assistant;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * MFD - Mehmet Fatih DURSUN
 * Bildirim dinleyici servisi v4
 */
public class MFDNotificationListener extends NotificationListenerService {

    public interface TriConsumer { void accept(String app, String title, String text); }

    public static volatile TriConsumer callback             = null;
    private static volatile PendingIntent lastReplyIntent   = null;
    private static volatile String       lastRemoteInputKey = null;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!isWatched(pkg)) return;

        Notification notif = sbn.getNotification();
        if (notif == null) return;

        Bundle extras = notif.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = textCs != null ? textCs.toString() : "";
        if (title.isEmpty() && text.isEmpty()) return;

        // RemoteInput (hızlı cevap) çıkart
        tryExtractRemoteInput(notif);

        TriConsumer cb = callback;
        if (cb != null) cb.accept(resolveApp(pkg), title, text);
    }

    private void tryExtractRemoteInput(Notification notif) {
        try {
            Notification.Action[] actions = notif.actions;
            if (actions == null) return;
            for (Notification.Action action : actions) {
                android.app.RemoteInput[] inputs = action.getRemoteInputs();
                if (inputs != null && inputs.length > 0) {
                    lastReplyIntent   = action.actionIntent;
                    lastRemoteInputKey = inputs[0].getResultKey();
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    /** Son bildirime RemoteInput aracılığıyla cevap gönderir. */
    public static boolean replyToLast(String reply) {
        try {
            if (lastReplyIntent == null || lastRemoteInputKey == null) return false;
            android.app.RemoteInput ri =
                new android.app.RemoteInput.Builder(lastRemoteInputKey).build();
            Bundle results = new Bundle();
            results.putString(lastRemoteInputKey, reply);
            Intent fill = new Intent();
            android.app.RemoteInput.addResultsToIntent(
                new android.app.RemoteInput[]{ri}, fill, results);
            lastReplyIntent.send(null, 0, fill, null, null);
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean isWatched(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("com.whatsapp") ||
               pkg.equals("com.whatsapp.w4b") ||
               pkg.equals("org.telegram.messenger") ||
               pkg.equals("com.google.android.apps.messaging") ||
               pkg.equals("com.samsung.android.messaging") ||
               pkg.contains("sms") ||
               pkg.contains("message");
    }

    private String resolveApp(String pkg) {
        if (pkg == null) return "Bilinmeyen";
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("messaging") || pkg.contains("sms")) return "SMS";
        return pkg;
    }
}
