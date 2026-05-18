package com.mfd.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

/**
 * MFD - Mehmet Fatih DURSUN
 * Ayarlar — model, lokasyon, kullanıcı, izinler, wake-word, otomatik cevap, ses.
 */
public class SettingsActivity extends AppCompatActivity {

    private AppConfig cfg;

    private EditText etKey, etLoc, etName, etWake;
    private Spinner spModel, spAutoMode, spListenLang, spTtsLang;
    private SeekBar sbRate, sbPitch, sbTemp;
    private TextView tvRate, tvPitch, tvTemp;
    private CheckBox cbWake, cbBoot, cbBubble, cbThink;
    private CheckBox cbReplyWa, cbReplyTg, cbReplySms;
    private CheckBox cbApproveWa, cbApproveSms, cbApproveCall, cbApproveCal;

    private static final String[] MODELS = {
        "gemini-2.5-flash-preview-05-20",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash"
    };
    private static final String[] MODES = { "ask", "auto", "off" };
    private static final String[] LANGS = { "tr-TR", "en-US", "en-GB", "de-DE", "fr-FR", "es-ES" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = new AppConfig(this);
        setContentView(R.layout.activity_settings);

        etKey   = findViewById(R.id.etKey);
        etLoc   = findViewById(R.id.etLoc);
        etName  = findViewById(R.id.etName);
        etWake  = findViewById(R.id.etWake);

        spModel      = findViewById(R.id.spModel);
        spAutoMode   = findViewById(R.id.spAutoMode);
        spListenLang = findViewById(R.id.spListenLang);
        spTtsLang    = findViewById(R.id.spTtsLang);

        sbRate  = findViewById(R.id.sbRate);
        sbPitch = findViewById(R.id.sbPitch);
        sbTemp  = findViewById(R.id.sbTemp);
        tvRate  = findViewById(R.id.tvRate);
        tvPitch = findViewById(R.id.tvPitch);
        tvTemp  = findViewById(R.id.tvTemp);

        cbWake   = findViewById(R.id.cbWake);
        cbBoot   = findViewById(R.id.cbBoot);
        cbBubble = findViewById(R.id.cbBubble);
        cbThink  = findViewById(R.id.cbThink);

        cbReplyWa  = findViewById(R.id.cbReplyWa);
        cbReplyTg  = findViewById(R.id.cbReplyTg);
        cbReplySms = findViewById(R.id.cbReplySms);

        cbApproveWa   = findViewById(R.id.cbApproveWa);
        cbApproveSms  = findViewById(R.id.cbApproveSms);
        cbApproveCall = findViewById(R.id.cbApproveCall);
        cbApproveCal  = findViewById(R.id.cbApproveCal);

        // Mevcut değerleri yükle
        etKey.setText(cfg.getGeminiKey());
        etLoc.setText(cfg.getLocation());
        etName.setText(cfg.getUserName());
        etWake.setText(cfg.getWakewordPhrase());

        spModel.setAdapter(adapter(MODELS));
        spAutoMode.setAdapter(adapter(MODES));
        spListenLang.setAdapter(adapter(LANGS));
        spTtsLang.setAdapter(adapter(LANGS));

        spModel.setSelection(indexOf(MODELS, cfg.getWorkingModel()));
        spAutoMode.setSelection(indexOf(MODES, cfg.getAutoReplyMode()));
        spListenLang.setSelection(indexOf(LANGS, cfg.getListenLang()));
        spTtsLang.setSelection(indexOf(LANGS, cfg.getTtsLang()));

        sbRate.setProgress(Math.round((cfg.getTtsRate()  - 0.5f) / 1.5f * 100));
        sbPitch.setProgress(Math.round((cfg.getTtsPitch() - 0.5f) / 1.5f * 100));
        sbTemp.setProgress(Math.round(cfg.getTemperature() * 100));
        updateSeekLabels();

        sbRate.setOnSeekBarChangeListener(new SimpleSeek(this::updateSeekLabels));
        sbPitch.setOnSeekBarChangeListener(new SimpleSeek(this::updateSeekLabels));
        sbTemp.setOnSeekBarChangeListener(new SimpleSeek(this::updateSeekLabels));

        cbWake.setChecked(cfg.isWakewordEnabled());
        cbBoot.setChecked(cfg.isAutostartBoot());
        cbBubble.setChecked(cfg.isBubbleEnabled());
        cbThink.setChecked(cfg.isThinkingOn());

        Set<String> replyApps = cfg.getAutoReplyApps();
        cbReplyWa.setChecked (replyApps.contains("WhatsApp"));
        cbReplyTg.setChecked (replyApps.contains("Telegram"));
        cbReplySms.setChecked(replyApps.contains("SMS"));

        Set<String> approve = cfg.getApprovalTools();
        cbApproveWa.setChecked  (approve.contains("send_whatsapp"));
        cbApproveSms.setChecked (approve.contains("send_sms"));
        cbApproveCall.setChecked(approve.contains("call_phone"));
        cbApproveCal.setChecked (approve.contains("add_calendar_event"));

        // İzin butonları
        findViewById(R.id.btnPermAll).setOnClickListener(v -> requestRuntimePerms());
        findViewById(R.id.btnPermNotif).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        findViewById(R.id.btnPermOverlay).setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this,"Overlay izni zaten verilmiş.",Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnPermBattery).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        findViewById(R.id.btnPermDnd).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnTest).setOnClickListener(v -> testKey());
    }

    private ArrayAdapter<String> adapter(String[] arr) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, arr);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private void updateSeekLabels() {
        float rate  = 0.5f + sbRate.getProgress()  / 100f * 1.5f;
        float pitch = 0.5f + sbPitch.getProgress() / 100f * 1.5f;
        float temp  = sbTemp.getProgress()  / 100f;
        tvRate.setText (String.format("Hız: %.2fx", rate));
        tvPitch.setText(String.format("Ton: %.2f",  pitch));
        tvTemp.setText (String.format("Yaratıcılık (temperature): %.2f", temp));
    }

    private void save() {
        String key  = etKey.getText().toString().trim();
        String loc  = etLoc.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String wake = etWake.getText().toString().trim().toLowerCase();
        if (key.isEmpty()) {
            Toast.makeText(this, "API anahtarı boş olamaz.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wake.isEmpty()) wake = "hey mfd";

        cfg.saveBasics(key, loc, name);
        cfg.setWorkingModel((String) spModel.getSelectedItem());
        cfg.setStr(AppConfig.K_AUTOREPLY_MODE, (String) spAutoMode.getSelectedItem());
        cfg.setStr(AppConfig.K_VOICE_LISTEN_LANG, (String) spListenLang.getSelectedItem());
        cfg.setStr(AppConfig.K_TTS_LANG, (String) spTtsLang.getSelectedItem());
        cfg.setStr(AppConfig.K_WAKEWORD_PHRASE, wake);

        float rate  = 0.5f + sbRate.getProgress()  / 100f * 1.5f;
        float pitch = 0.5f + sbPitch.getProgress() / 100f * 1.5f;
        float temp  = sbTemp.getProgress()  / 100f;
        cfg.setFloat(AppConfig.K_TTS_RATE,  rate);
        cfg.setFloat(AppConfig.K_TTS_PITCH, pitch);
        cfg.setFloat(AppConfig.K_TEMPERATURE, temp);

        cfg.setBool(AppConfig.K_WAKEWORD_ENABLED, cbWake.isChecked());
        cfg.setBool(AppConfig.K_AUTOSTART_BOOT,   cbBoot.isChecked());
        cfg.setBool(AppConfig.K_BUBBLE_ENABLED,   cbBubble.isChecked());
        cfg.setBool(AppConfig.K_USE_THINKING,     cbThink.isChecked());

        Set<String> reply = new HashSet<>();
        if (cbReplyWa.isChecked())  reply.add("WhatsApp");
        if (cbReplyTg.isChecked())  reply.add("Telegram");
        if (cbReplySms.isChecked()) reply.add("SMS");
        cfg.setSet(AppConfig.K_AUTOREPLY_APPS, reply);
        cfg.setSet(AppConfig.K_NOTIF_LISTEN_APPS, reply);

        Set<String> approve = new HashSet<>();
        if (cbApproveWa.isChecked())   approve.add("send_whatsapp");
        if (cbApproveSms.isChecked())  approve.add("send_sms");
        if (cbApproveCall.isChecked()) approve.add("call_phone");
        if (cbApproveCal.isChecked())  { approve.add("add_calendar_event"); approve.add("delete_calendar_event"); }
        approve.add("send_email");
        cfg.setSet(AppConfig.K_APPROVAL_TOOLS, approve);

        // Wake-word servisini güncelle
        Intent svc = new Intent(this, WakeWordService.class);
        if (cbWake.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
            else startService(svc);
        } else {
            stopService(svc);
        }

        // Bubble servisini güncelle
        Intent bub = new Intent(this, FloatingBubbleService.class);
        if (cbBubble.isChecked() && Settings.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(bub);
            else startService(bub);
        } else {
            stopService(bub);
        }

        Toast.makeText(this, "Ayarlar kaydedildi.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void testKey() {
        Toast.makeText(this, "Bağlantı testi başlatıldı...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                        + spModel.getSelectedItem() + ":generateContent?key="
                        + etKey.getText().toString().trim());
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type","application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                String body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"ping\"}]}],"
                            + "\"generationConfig\":{\"maxOutputTokens\":10}}";
                c.getOutputStream().write(body.getBytes("UTF-8"));
                int code = c.getResponseCode();
                runOnUiThread(() -> Toast.makeText(this,
                        code == 200 ? "✅ Bağlantı OK" : "❌ HATA " + code,
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void requestRuntimePerms() {
        String[] perms = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        java.util.List<String> need = new java.util.ArrayList<>();
        for (String p : perms)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                need.add(p);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (need.isEmpty())
            Toast.makeText(this, "Tüm izinler verilmiş ✅", Toast.LENGTH_SHORT).show();
        else
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 200);
    }

    // ─────────────────────────────────────────────────────────────────────
    private interface SimpleAction { void run(); }
    private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final SimpleAction action;
        SimpleSeek(SimpleAction a) { action = a; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean u) { action.run(); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
