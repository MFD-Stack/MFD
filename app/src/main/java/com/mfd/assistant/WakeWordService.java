package com.mfd.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

/**
 * MFD - Mehmet Fatih DURSUN
 * Wake-word arka plan servisi v4
 * Uyku sözcüğü algılandığında MainActivity'yi uyarır.
 */
public class WakeWordService extends Service {

    public static final String ACTION_WAKE       = "com.mfd.assistant.ACTION_WAKE";
    public static final String EXTRA_TRANSCRIPT  = "transcript";
    private static final String CHANNEL_ID       = "mfd_wake";
    private static final int    NOTIF_ID         = 1001;

    private SpeechRecognizer sr;
    private AppConfig cfg;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        cfg = new AppConfig(this);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            startListening();
        }
        return START_STICKY;
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return;
        }
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new WakeListener());

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, cfg.getListenLang());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try { sr.startListening(i); } catch (Exception ignored) { stopSelf(); }
    }

    private void restart() {
        if (!running) return;
        try {
            if (sr != null) { sr.destroy(); sr = null; }
            // 1 saniye bekle, sonra yeniden başlat (pil dostu)
            new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::startListening, 1000);
        } catch (Exception ignored) {}
    }

    private class WakeListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle p) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float v) {}
        @Override public void onBufferReceived(byte[] b) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onError(int e) { restart(); }
        @Override public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String transcript = matches.get(0).toLowerCase();
                String phrase     = cfg.getWakewordPhrase().toLowerCase().trim();
                if (transcript.contains(phrase)) {
                    // Wake-word algılandı → MainActivity'ye ilet
                    Intent wake = new Intent(WakeWordService.this, MainActivity.class);
                    wake.setAction(ACTION_WAKE);
                    wake.putExtra(EXTRA_TRANSCRIPT, transcript.replace(phrase, "").trim());
                    wake.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(wake);
                }
            }
            restart();
        }
        @Override public void onPartialResults(Bundle b) {}
        @Override public void onEvent(int t, Bundle b) {}
    }

    @Override
    public void onDestroy() {
        running = false;
        if (sr != null) { sr.destroy(); sr = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "MFD Wake-Word",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("MFD uyku sözcüğü dinleme servisi");
            ch.setSound(null, null);
            ch.enableVibration(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MFD Aktif")
                .setContentText("Uyku sözcüğü bekleniyor: " + cfg.getWakewordPhrase())
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }
}
