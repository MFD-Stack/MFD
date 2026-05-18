package com.mfd.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MFD — Multi-Function Device
 * Ana Ekran v4
 * Geliştirici: Mehmet Fatih DURSUN
 */
public class MainActivity extends AppCompatActivity {

    private static final int RC_PERMS = 100;
    private static final int RC_OVERLAY = 101;
    private static final int RC_NOTIF_LISTEN = 102;

    // UI
    private OrbView orbView;
    private TextView tvStatus, tvTime, tvBattery, tvWeather, tvGreeting;
    private View statusDot;
    private FloatingActionButton btnMic;
    private ImageButton btnSend, btnSettings;
    private android.widget.EditText etInput;
    private RecyclerView chatLog;
    private LinearLayout quickCards;
    private ChatLogAdapter adapter;

    // Core
    private AppConfig cfg;
    private MemoryManager memory;
    private AndroidActions actions;
    private GeminiClient gemini;
    private TextToSpeech tts;
    private SpeechRecognizer sr;

    private boolean isListening = false;
    private boolean ttsReady    = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Timer clockTimer;

    // Quick command suggestions
    private static final String[][] QUICK_CMDS = {
        {"🌤 Hava", "Hava durumu nasıl?"},
        {"🔋 Pil",  "Pil durumu nedir?"},
        {"📅 Takvim","Bugün hangi etkinliklerim var?"},
        {"🎵 Müzik","YouTube'da müzik aç"},
        {"⏰ Alarm", "Sabah 7'ye alarm kur"},
        {"💱 Dolar", "Dolar TL kuru nedir?"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cfg = new AppConfig(this);
        // Gemini API anahtarı yoksa kurulum ekranına yönlendir
        if (!cfg.hasGeminiKey()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        bindViews();
        setupRecyclerView();
        initCoreComponents();
        initTTS();
        initSpeechRecognizer();
        setupListeners();
        setupNotificationCallback();
        setupQuickCards();
        startClock();
        requestPermissions();
        startBackgroundServicesIfEnabled();

        setState(OrbView.State.LISTENING);
        log(ChatLogAdapter.sys("MFD v4 hazır. Mehmet Fatih DURSUN tarafından geliştirildi."));
        loadInfoCards();
        handleWakeIntent(getIntent());
    }

    private void bindViews() {
        orbView     = findViewById(R.id.orbView);
        tvStatus    = findViewById(R.id.tvStatus);
        tvTime      = findViewById(R.id.tvTime);
        tvBattery   = findViewById(R.id.tvBattery);
        tvWeather   = findViewById(R.id.tvWeather);
        tvGreeting  = findViewById(R.id.tvGreeting);
        statusDot   = findViewById(R.id.statusDot);
        btnMic      = findViewById(R.id.btnMic);
        btnSend     = findViewById(R.id.btnSend);
        btnSettings = findViewById(R.id.btnSettings);
        etInput     = findViewById(R.id.etInput);
        chatLog     = findViewById(R.id.chatLog);
        quickCards  = findViewById(R.id.quickCards);
    }

    private void setupRecyclerView() {
        adapter = new ChatLogAdapter();
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        chatLog.setLayoutManager(llm);
        chatLog.setAdapter(adapter);
    }

    private void initCoreComponents() {
        String name = cfg.getUserName();
        tvGreeting.setText(name.isEmpty() ? "Merhaba" : "Merhaba, " + name);

        memory  = new MemoryManager(this);
        actions = new AndroidActions(this);
        rebuildGemini();
    }

    private void rebuildGemini() {
        gemini = new GeminiClient(
                cfg.getGeminiKey(),
                cfg.getWorkingModel(),
                GeminiClient.buildSystemPrompt(cfg.getUserName(), memory.toPromptString()),
                GeminiClient.buildToolDeclarations());
        gemini.setTemperature(cfg.getTemperature());
        gemini.setUseThinking(cfg.isThinkingOn());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ayarlar değişmiş olabilir — Gemini'yi yenile
        rebuildGemini();
        String name = cfg.getUserName();
        tvGreeting.setText(name.isEmpty() ? "Merhaba" : "Merhaba, " + name);
    }

    // ── TTS ──────────────────────────────────────────────────────────────

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                configureTTS();
                ttsReady = true;
            }
        });
    }

    private void configureTTS() {
        String lang = cfg.getTtsLang();
        String[] parts = lang.split("-");
        Locale loc = parts.length >= 2 ? new Locale(parts[0], parts[1]) : new Locale(lang);
        int r = tts.setLanguage(loc);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        tts.setSpeechRate(cfg.getTtsRate());
        tts.setPitch(cfg.getTtsPitch());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { ui.post(() -> setState(OrbView.State.SPEAKING)); }
            @Override public void onDone(String id)  { ui.post(() -> setState(OrbView.State.LISTENING)); }
            @Override public void onError(String id) { ui.post(() -> setState(OrbView.State.LISTENING)); }
        });
    }

    private void speak(String text) {
        if (tts == null || !ttsReady || TextUtils.isEmpty(text)) return;
        String uid = "mfd_" + System.currentTimeMillis();
        Bundle p = new Bundle();
        p.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, p, uid);
    }

    // ── SPEECH RECOGNIZER ────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new MFDRecognitionListener());
        }
    }

    // ── LISTENERS ────────────────────────────────────────────────────────

    private void setupListeners() {
        btnMic.setOnClickListener(v -> toggleMic());
        btnSend.setOnClickListener(v -> submitText());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        etInput.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitText(); return true;
            }
            return false;
        });
    }

    private void setupQuickCards() {
        if (quickCards == null) return;
        for (String[] cmd : QUICK_CMDS) {
            TextView card = new TextView(this);
            card.setText(cmd[0]);
            card.setTextColor(Color.parseColor("#F0F0F5"));
            card.setTextSize(12f);
            card.setBackgroundColor(Color.parseColor("#1A1A1F"));
            int pd = dp(12); int pdV = dp(7);
            card.setPadding(pd, pdV, pd, pdV);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> process(cmd[1]));
            // Rounded corner background
            card.setBackground(getResources().getDrawable(R.drawable.card_bg, getTheme()));
            quickCards.addView(card);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // ── MIC ──────────────────────────────────────────────────────────────

    private void toggleMic() {
        if (isListening) {
            isListening = false;
            if (sr != null) sr.stopListening();
            setState(OrbView.State.LISTENING);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mikrofon izni gerekiyor.", Toast.LENGTH_SHORT).show();
            requestPermissions();
            return;
        }
        if (sr == null) {
            Toast.makeText(this, "Ses tanıma bu cihazda desteklenmiyor.", Toast.LENGTH_SHORT).show();
            return;
        }
        isListening = true;
        setState(OrbView.State.LISTENING);
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, cfg.getListenLang());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            sr.startListening(i);
        } catch (Exception e) {
            isListening = false;
            Toast.makeText(this, "Ses tanıma başlatılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void submitText() {
        if (etInput.getText() == null) return;
        String t = etInput.getText().toString().trim();
        if (t.isEmpty()) return;
        etInput.setText("");
        process(t);
    }

    // ── PIPELINE ─────────────────────────────────────────────────────────

    private void process(String text) {
        log(ChatLogAdapter.user(text));
        setState(OrbView.State.THINKING);
        gemini.sendMessage(text, new GeminiClient.Callback() {
            @Override public void onResponse(String resp, JSONArray tools) {
                ui.post(() -> handleResponse(resp, tools));
            }
            @Override public void onError(String err) {
                ui.post(() -> {
                    log(ChatLogAdapter.err(err));
                    setState(OrbView.State.ERROR);
                    ui.postDelayed(() -> setState(OrbView.State.LISTENING), 3000);
                });
            }
        });
    }

    private void handleResponse(String text, JSONArray tools) {
        if (tools != null && tools.length() > 0) {
            runTools(tools);
            return;
        }
        if (!TextUtils.isEmpty(text)) {
            log(ChatLogAdapter.mfd(text));
            speak(text);
        }
        setState(OrbView.State.LISTENING);
    }

    private void runTools(JSONArray tools) {
        setState(OrbView.State.THINKING);
        new Thread(() -> {
            try {
                JSONObject call = tools.getJSONObject(0);
                String name = call.optString("name");
                // Gemini returns args inside "args" key
                JSONObject args = call.optJSONObject("args");
                if (args == null) args = new JSONObject();

                // Special flow: auto_reply_suggest
                if ("auto_reply_suggest".equals(name)) {
                    final JSONObject fa = args;
                    ui.post(() -> handleAutoReplySuggest(fa));
                    return;
                }

                // Check if this tool needs user approval
                if (needsApproval(name)) {
                    String preview = buildApprovalMessage(name, args);
                    final String fn = name;
                    final JSONObject fa = args;
                    ui.post(() -> showApprovalDialog(preview,
                            () -> new Thread(() -> executeAndContinue(fn, fa)).start()));
                    return;
                }
                executeAndContinue(name, args);
            } catch (Exception e) {
                ui.post(() -> {
                    log(ChatLogAdapter.err("Tool hatası: " + e.getMessage()));
                    setState(OrbView.State.LISTENING);
                });
            }
        }).start();
    }

    private boolean needsApproval(String name) {
        Set<String> set = cfg.getApprovalTools();
        return set.contains(name);
    }

    private String buildApprovalMessage(String name, JSONObject args) {
        try {
            switch (name) {
                case "send_whatsapp":
                    return "WhatsApp mesajı gönderilsin mi?\nKişi: " + args.optString("phone_or_name")
                            + "\nMesaj: " + args.optString("message");
                case "send_sms":
                    return "SMS gönderilsin mi?\nNumara: " + args.optString("phone")
                            + "\nMesaj: " + args.optString("message");
                case "call_phone":
                    return "Arama yapılsın mı?\nKişi: " + args.optString("phone_or_name");
                case "send_email":
                    return "E-posta gönderilsin mi?\nKime: " + args.optString("to")
                            + "\nKonu: " + args.optString("subject");
                case "add_calendar_event":
                    return "Takvime eklensin mi?\nEtkinlik: " + args.optString("title")
                            + "\nTarih: " + args.optString("start_iso");
                case "delete_calendar_event":
                    return "Takvimden silinsin mi?\nEtkinlik: " + args.optString("title");
                default: return name + " çalıştırılsın mı?";
            }
        } catch (Exception e) { return name + " çalıştırılsın mı?"; }
    }

    private void showApprovalDialog(String msg, Runnable onYes) {
        new AlertDialog.Builder(this)
                .setTitle("Onay Gerekiyor")
                .setMessage(msg)
                .setPositiveButton("Evet, Yap", (d, w) -> onYes.run())
                .setNegativeButton("Hayır, İptal", (d, w) -> {
                    log(ChatLogAdapter.sys("İşlem iptal edildi."));
                    setState(OrbView.State.LISTENING);
                })
                .setCancelable(false)
                .show();
    }

    private void executeAndContinue(String name, JSONObject args) {
        String result = executeTool(name, args);
        ui.post(() -> log(ChatLogAdapter.tool(name, result)));

        gemini.sendToolResult(name, result, new GeminiClient.Callback() {
            @Override public void onResponse(String resp, JSONArray next) {
                ui.post(() -> {
                    if (next != null && next.length() > 0) { runTools(next); return; }
                    if (!TextUtils.isEmpty(resp)) { log(ChatLogAdapter.mfd(resp)); speak(resp); }
                    setState(OrbView.State.LISTENING);
                });
            }
            @Override public void onError(String err) {
                ui.post(() -> {
                    log(ChatLogAdapter.err(err));
                    setState(OrbView.State.ERROR);
                    ui.postDelayed(() -> setState(OrbView.State.LISTENING), 3000);
                });
            }
        });
    }

    private String executeTool(String name, JSONObject a) {
        try {
            switch (name) {
                case "open_app":            return actions.openApp(a.optString("app_name",""));
                case "sys_info":            return actions.sysInfo(a.optString("query","all"));
                case "get_weather":         return actions.getWeather(a.optString("location",""));
                case "get_calendar_events": return actions.getCalendarEvents(a.optString("query","today"),a.optInt("limit",6));
                case "add_calendar_event":  return actions.addCalendarEvent(
                        a.optString("title",""), a.optString("start_iso",""), a.optString("end_iso",""),
                        a.optString("notes",""), a.optString("location",""), a.optBoolean("all_day",false));
                case "delete_calendar_event": return actions.deleteCalendarEvent(a.optString("title",""));
                case "get_reminders":       return actions.getReminders(a.optString("query","all"),a.optInt("limit",8));
                case "add_reminder":        return actions.addReminder(a.optString("title",""),a.optString("due_iso",""),a.optString("notes",""));
                case "browser_control":     return actions.browserControl(a.optString("action","search"),a.optString("url",""),a.optString("query",""));
                case "play_media":          return actions.playMedia(a.optString("query",""),a.optString("provider","auto"));
                case "send_whatsapp":       return actions.sendWhatsApp(a.optString("phone_or_name",""),a.optString("message",""));
                case "send_sms":            return actions.sendSms(a.optString("phone",""),a.optString("message",""));
                case "call_phone":          return actions.callPhone(a.optString("phone_or_name",""));
                case "send_email":          return actions.sendEmail(a.optString("to",""),a.optString("subject",""),a.optString("body",""));
                case "set_alarm":           return actions.setAlarm(a.optInt("hour",7),a.optInt("minute",0),a.optString("message",""));
                case "start_timer":         return actions.startTimer(a.optInt("seconds",60),a.optString("message",""));
                case "toggle_flashlight":   return actions.toggleFlashlight(a.optString("state","on"));
                case "set_volume":          return actions.setVolume(a.optString("stream","music"),a.optInt("percent",50));
                case "open_system_setting": return actions.openSystemSetting(a.optString("section",""));
                case "navigate":            return actions.navigate(a.optString("destination",""));
                case "get_currency":        return actions.getCurrency(a.optString("base","USD"),a.optString("target","TRY"));
                case "save_memory":
                    memory.save(a.optString("category","notlar"),a.optString("key","öğe"),a.optString("value",""));
                    return "✓ Hafızaya kaydedildi.";
                case "delete_memory":       return memory.delete(a.optString("category",""),a.optString("key",""),a.optString("match_text",""));
                default:                    return "Bilinmeyen araç: " + name;
            }
        } catch (Exception e) { return "Araç hatası: " + e.getMessage(); }
    }

    // ── AUTO REPLY ───────────────────────────────────────────────────────

    private void handleAutoReplySuggest(JSONObject a) {
        final String app        = a.optString("app", "");
        final String sender     = a.optString("sender", "");
        final String message    = a.optString("message", "");
        final String suggestion = a.optString("suggestion", "");
        final String mode       = cfg.getAutoReplyMode();

        log(ChatLogAdapter.sys("💡 Öneri (" + app + " · " + sender + "): " + suggestion));

        if ("off".equals(mode)) { finalizeAutoReply("mode_off"); return; }
        if ("auto".equals(mode)) { doAutoReply(sender, suggestion); return; }

        new AlertDialog.Builder(this)
                .setTitle("Cevap önerisi — " + sender)
                .setMessage("Gelen: " + message + "\n\nCevap: " + suggestion)
                .setPositiveButton("Gönder", (d, w) -> doAutoReply(sender, suggestion))
                .setNeutralButton("Düzenle", (d, w) -> showEditReplyDialog(sender, suggestion))
                .setNegativeButton("İptal", (d, w) -> {
                    log(ChatLogAdapter.sys("Cevap iptal edildi."));
                    finalizeAutoReply("cancelled_by_user");
                }).show();
    }

    private void finalizeAutoReply(String status) {
        gemini.sendToolResult("auto_reply_suggest", status, new GeminiClient.Callback() {
            @Override public void onResponse(String resp, JSONArray next) {
                ui.post(() -> {
                    if (resp != null && !resp.isEmpty()) log(ChatLogAdapter.mfd(resp));
                    setState(OrbView.State.LISTENING);
                });
            }
            @Override public void onError(String err) {
                ui.post(() -> { gemini.clearHistory(); setState(OrbView.State.LISTENING); });
            }
        });
    }

    private void showEditReplyDialog(String sender, String initial) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(initial);
        new AlertDialog.Builder(this)
                .setTitle("Cevabı düzenle — " + sender)
                .setView(input)
                .setPositiveButton("Gönder", (d, w) -> doAutoReply(sender, input.getText().toString()))
                .setNegativeButton("İptal", null).show();
    }

    private void doAutoReply(String sender, String reply) {
        boolean ok = MFDNotificationListener.replyToLast(reply);
        String status;
        if (ok) {
            log(ChatLogAdapter.sys("✓ Cevap gönderildi (bildirim üzerinden): " + reply));
            status = "sent_via_remoteinput";
        } else {
            String res = actions.sendWhatsApp(sender, reply);
            log(ChatLogAdapter.tool("send_whatsapp", res));
            status = "sent_via_fallback: " + res;
        }
        finalizeAutoReply(status);
    }

    // ── NOTIFICATION CALLBACK ────────────────────────────────────────────

    private void setupNotificationCallback() {
        MFDNotificationListener.callback = (app, title, text) -> ui.post(() -> {
            log(ChatLogAdapter.sys("📩 [" + app + "] " + title + ": " + text));
            String mode = cfg.getAutoReplyMode();
            Set<String> apps = cfg.getAutoReplyApps();
            if ("off".equals(mode) || !apps.contains(app)) return;
            String prompt = "BİLDİRİM: " + app + " üzerinden " + title
                    + " kişisinden gelen mesaj: \"" + text + "\". "
                    + "Bu mesaja uygun, kısa Türkçe bir cevap üret ve "
                    + "auto_reply_suggest aracını çağır. Argümanlar: "
                    + "app=\"" + app + "\", sender=\"" + title
                    + "\", message=\"" + text + "\", suggestion=<senin önerin>.";
            process(prompt);
        });
    }

    // ── WAKE INTENT ──────────────────────────────────────────────────────

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleWakeIntent(intent);
    }

    private void handleWakeIntent(Intent intent) {
        if (intent == null) return;
        if (WakeWordService.ACTION_WAKE.equals(intent.getAction())) {
            String t = intent.getStringExtra(WakeWordService.EXTRA_TRANSCRIPT);
            if (t != null && !t.isEmpty()) {
                ui.postDelayed(() -> process(t), 300);
            } else {
                ui.postDelayed(this::toggleMic, 300);
            }
        }
    }

    // ── BACKGROUND SERVICES ──────────────────────────────────────────────

    private void startBackgroundServicesIfEnabled() {
        try {
            if (cfg.isWakewordEnabled()) {
                Intent svc = new Intent(this, WakeWordService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
                else startService(svc);
            }
            if (cfg.isBubbleEnabled() && Settings.canDrawOverlays(this)) {
                Intent bub = new Intent(this, FloatingBubbleService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(bub);
                else startService(bub);
            }
        } catch (Exception ignored) {}
    }

    // ── UI HELPERS ───────────────────────────────────────────────────────

    private void setState(OrbView.State state) {
        if (orbView != null) orbView.setState(state);
        if (tvStatus == null) return;
        switch (state) {
            case LISTENING:
                tvStatus.setText("DİNLİYORUM");
                if (statusDot != null) statusDot.setBackgroundResource(R.drawable.circle_green);
                break;
            case SPEAKING:
                tvStatus.setText("KONUŞUYOR");
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#4488FF"));
                break;
            case THINKING:
                tvStatus.setText("DÜŞÜNÜYOR");
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#FFCC00"));
                break;
            case ERROR:
                tvStatus.setText("HATA");
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#FF3344"));
                break;
            default:
                tvStatus.setText("—");
                break;
        }
    }

    private void log(ChatLogAdapter.Entry e) {
        if (adapter == null) return;
        adapter.add(e);
        chatLog.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void startClock() {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        clockTimer = new Timer();
        clockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                ui.post(() -> { if (tvTime != null) tvTime.setText(fmt.format(new Date())); });
            }
        }, 0, 30000);
    }

    private void loadInfoCards() {
        new Thread(() -> {
            try {
                String batt = actions.sysInfo("battery").replace("Pil: ", "").trim();
                if (batt.length() > 18) batt = batt.substring(0, 18);
                final String b = batt;
                ui.post(() -> { if (tvBattery != null) tvBattery.setText(b); });
            } catch (Exception ignored) {}
            try {
                String w = actions.getWeather(null);
                int ci = w.indexOf(": ");
                if (ci >= 0) w = w.substring(ci + 2);
                if (w.length() > 22) w = w.substring(0, 22) + "…";
                final String wf = w;
                ui.post(() -> { if (tvWeather != null) tvWeather.setText(wf); });
            } catch (Exception ignored) {}
        }).start();
    }

    // ── PERMISSIONS ──────────────────────────────────────────────────────

    private void requestPermissions() {
        ArrayList<String> needed = new ArrayList<>();
        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
        };
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), RC_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(code, perms, grants);
        // İzinler alındıktan sonra SR'yi yeniden başlat
        if (code == RC_PERMS && sr == null) {
            initSpeechRecognizer();
        }
    }

    // ── RECOGNITION LISTENER ─────────────────────────────────────────────

    private class MFDRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle p) {}
        @Override public void onBeginningOfSpeech() { ui.post(() -> setState(OrbView.State.LISTENING)); }
        @Override public void onRmsChanged(float v)  {}
        @Override public void onBufferReceived(byte[] b) {}
        @Override public void onEndOfSpeech() {
            isListening = false;
            ui.post(() -> setState(OrbView.State.THINKING));
        }
        @Override public void onError(int e) {
            isListening = false;
            ui.post(() -> {
                setState(OrbView.State.LISTENING);
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(MainActivity.this, "Ses tanıma hatası: " + e, Toast.LENGTH_SHORT).show();
                }
            });
        }
        @Override public void onResults(Bundle results) {
            ArrayList<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (m != null && !m.isEmpty()) {
                ui.post(() -> process(m.get(0)));
            } else {
                ui.post(() -> setState(OrbView.State.LISTENING));
            }
        }
        @Override public void onPartialResults(Bundle b) {}
        @Override public void onEvent(int t, Bundle b) {}
    }

    // ── LIFECYCLE ────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MFDNotificationListener.callback = null;
        if (tts != null)       { tts.stop(); tts.shutdown(); tts = null; }
        if (sr != null)        { sr.destroy(); sr = null; }
        if (clockTimer != null){ clockTimer.cancel(); clockTimer = null; }
    }
}
