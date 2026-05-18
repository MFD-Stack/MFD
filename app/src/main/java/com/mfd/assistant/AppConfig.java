package com.mfd.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * MFD - Mehmet Fatih DURSUN
 * Tüm kullanıcı ayarları tek noktadan.
 */
public class AppConfig {
    private static final String PREF = "mfd_config";

    public static final String K_GEMINI_KEY     = "gemini_key";
    public static final String K_WORKING_MODEL  = "working_model";
    public static final String K_LOCATION       = "location";
    public static final String K_USER_NAME      = "user_name";

    // Davranış ayarları
    public static final String K_WAKEWORD_ENABLED   = "wakeword_enabled";
    public static final String K_WAKEWORD_PHRASE    = "wakeword_phrase";
    public static final String K_AUTOSTART_BOOT     = "autostart_boot";
    public static final String K_BUBBLE_ENABLED     = "bubble_enabled";
    public static final String K_AUTOREPLY_MODE     = "autoreply_mode"; // off | ask | auto
    public static final String K_AUTOREPLY_APPS     = "autoreply_apps"; // string set
    public static final String K_NOTIF_LISTEN_APPS  = "notif_listen_apps";
    public static final String K_TTS_LANG           = "tts_lang"; // tr-TR
    public static final String K_TTS_RATE           = "tts_rate"; // float 0.5-2.0
    public static final String K_TTS_PITCH          = "tts_pitch";
    public static final String K_VOICE_LISTEN_LANG  = "voice_listen_lang";
    public static final String K_TEMPERATURE        = "temperature";
    public static final String K_USE_THINKING       = "use_thinking";
    public static final String K_APPROVAL_TOOLS     = "approval_tools";

    public static final String DEFAULT_MODEL = "gemini-2.5-flash-preview-05-20";

    private final SharedPreferences p;

    public AppConfig(Context ctx) {
        p = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ── Temel ──────────────────────────────────────────────────────────────
    public String getGeminiKey()    { return p.getString(K_GEMINI_KEY, ""); }
    public boolean hasGeminiKey()   { return !getGeminiKey().isEmpty(); }
    public String getLocation()     { return p.getString(K_LOCATION, "Istanbul"); }
    public String getUserName()     { return p.getString(K_USER_NAME, ""); }
    public String getWorkingModel() { return p.getString(K_WORKING_MODEL, DEFAULT_MODEL); }

    // ── Wake-word ──────────────────────────────────────────────────────────
    public boolean isWakewordEnabled() { return p.getBoolean(K_WAKEWORD_ENABLED, false); }
    public String  getWakewordPhrase() { return p.getString(K_WAKEWORD_PHRASE, "hey mfd"); }
    public boolean isAutostartBoot()   { return p.getBoolean(K_AUTOSTART_BOOT, false); }

    // ── Floating bubble ───────────────────────────────────────────────────
    public boolean isBubbleEnabled()   { return p.getBoolean(K_BUBBLE_ENABLED, false); }

    // ── Otomatik cevap ─────────────────────────────────────────────────────
    /** off = hiç cevap önerme | ask = öneri kullanıcıya sor | auto = otomatik gönder */
    public String getAutoReplyMode()   { return p.getString(K_AUTOREPLY_MODE, "ask"); }
    public Set<String> getAutoReplyApps() {
        Set<String> def = new HashSet<>();
        def.add("WhatsApp"); def.add("Telegram");
        return p.getStringSet(K_AUTOREPLY_APPS, def);
    }
    public Set<String> getNotifListenApps() {
        Set<String> def = new HashSet<>();
        def.add("WhatsApp"); def.add("Telegram"); def.add("SMS");
        return p.getStringSet(K_NOTIF_LISTEN_APPS, def);
    }

    // ── Konuşma & ses ──────────────────────────────────────────────────────
    public String getTtsLang()   { return p.getString(K_TTS_LANG,  "tr-TR"); }
    public float  getTtsRate()   { return p.getFloat (K_TTS_RATE,  1.0f); }
    public float  getTtsPitch()  { return p.getFloat (K_TTS_PITCH, 1.0f); }
    public String getListenLang(){ return p.getString(K_VOICE_LISTEN_LANG, "tr-TR"); }

    // ── Model davranışı ────────────────────────────────────────────────────
    public float   getTemperature() { return p.getFloat(K_TEMPERATURE, 0.7f); }
    public boolean isThinkingOn()   { return p.getBoolean(K_USE_THINKING, false); }

    // ── Onay isteyecek araçlar ─────────────────────────────────────────────
    public Set<String> getApprovalTools() {
        Set<String> def = new HashSet<>();
        def.add("send_whatsapp");
        def.add("send_sms");
        def.add("call_phone");
        def.add("add_calendar_event");
        def.add("delete_calendar_event");
        def.add("send_email");
        return p.getStringSet(K_APPROVAL_TOOLS, def);
    }

    // ── Yazıcılar ──────────────────────────────────────────────────────────
    public SharedPreferences.Editor edit() { return p.edit(); }

    public void saveBasics(String key, String location, String userName) {
        edit()
            .putString(K_GEMINI_KEY, key.trim())
            .putString(K_LOCATION, location.isEmpty() ? "Istanbul" : location)
            .putString(K_USER_NAME, userName)
            .apply();
    }

    public void setWorkingModel(String m) {
        edit().putString(K_WORKING_MODEL, m).apply();
    }

    public void setBool(String key, boolean v) { edit().putBoolean(key, v).apply(); }
    public void setStr (String key, String  v) { edit().putString (key, v).apply(); }
    public void setFloat(String key, float  v) { edit().putFloat  (key, v).apply(); }
    public void setSet (String key, Set<String> v) { edit().putStringSet(key, v).apply(); }
}
