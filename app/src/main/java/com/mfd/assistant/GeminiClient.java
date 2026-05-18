package com.mfd.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MFD - Mehmet Fatih DURSUN
 * Gemini API istemcisi v4 — düzeltilmiş history yönetimi,
 * functionCall history uyumu, hata yönetimi.
 */
public class GeminiClient {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash-preview-05-20";
    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/";

    /** Max history turns (her turn = 1 user + 1 model) */
    private static final int MAX_TURNS = 20;

    public interface Callback {
        void onResponse(String text, JSONArray toolCalls);
        void onError(String error);
    }

    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final JSONArray toolDefs;
    // History is synchronized since we use a single-thread executor
    private final JSONArray history = new JSONArray();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private float temperature = 0.7f;
    private boolean useThinking = false;

    public GeminiClient(String apiKey, String model, String systemPrompt, JSONArray toolDefs) {
        this.apiKey       = apiKey;
        this.model        = (model != null && !model.isEmpty()) ? model : DEFAULT_MODEL;
        this.systemPrompt = systemPrompt;
        this.toolDefs     = toolDefs;
    }

    public GeminiClient(String apiKey, String systemPrompt, JSONArray toolDefs) {
        this(apiKey, DEFAULT_MODEL, systemPrompt, toolDefs);
    }

    public void setTemperature(float t) { this.temperature = t; }
    public void setUseThinking(boolean v) { this.useThinking = v; }

    public void sendMessage(String userText, Callback cb) {
        exec.execute(() -> {
            try {
                addUserTurn(new JSONObject().put("text", userText));
                doCall(cb);
            } catch (Exception e) {
                cb.onError("Bağlantı hatası: " + e.getMessage());
            }
        });
    }

    public void sendToolResult(String name, String result, Callback cb) {
        exec.execute(() -> {
            try {
                // Tool result must be sent as user turn with functionResponse
                JSONObject fr = new JSONObject()
                    .put("functionResponse", new JSONObject()
                        .put("name", name)
                        .put("response", new JSONObject().put("result", result)));
                addUserTurn(fr);
                doCall(cb);
            } catch (Exception e) {
                cb.onError("Tool yanıt hatası: " + e.getMessage());
            }
        });
    }

    private void addUserTurn(JSONObject part) throws Exception {
        JSONObject turn = new JSONObject();
        turn.put("role", "user");
        turn.put("parts", new JSONArray().put(part));
        history.put(turn);
        trimHistory();
    }

    /** Remove oldest turns while keeping history valid (user/model alternation). */
    private void trimHistory() {
        int maxLen = MAX_TURNS * 2;
        while (history.length() > maxLen) {
            try { history.remove(0); } catch (Exception ignored) { break; }
        }
        // Ensure first entry is a user turn (required by Gemini API)
        while (history.length() > 0) {
            try {
                JSONObject first = history.getJSONObject(0);
                if ("user".equals(first.optString("role"))) break;
                history.remove(0);
            } catch (Exception ignored) { break; }
        }
    }

    private void doCall(Callback cb) throws Exception {
        String urlStr = BASE_URL + model + ":generateContent?key=" + apiKey;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        JSONObject body = new JSONObject();

        // System instruction
        body.put("systemInstruction", new JSONObject()
            .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))));

        // Contents (history)
        body.put("contents", history);

        // Tools
        if (toolDefs != null && toolDefs.length() > 0) {
            body.put("tools", new JSONArray()
                .put(new JSONObject().put("functionDeclarations", toolDefs)));
            // Allow model to decide when to call tools
            body.put("toolConfig", new JSONObject()
                .put("functionCallingConfig", new JSONObject().put("mode", "AUTO")));
        }

        // Generation config
        JSONObject gen = new JSONObject();
        gen.put("temperature", temperature);
        gen.put("maxOutputTokens", 1500);
        if (model.contains("2.5") && !useThinking) {
            gen.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
        }
        body.put("generationConfig", gen);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();

        if (code != 200) {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String msg = sb.toString();
            try {
                JSONObject ej = new JSONObject(msg);
                if (ej.has("error"))
                    msg = ej.getJSONObject("error").optString("message", msg);
            } catch (Exception ignored) {}
            if (msg.length() > 300) msg = msg.substring(0, 300);
            cb.onError("API " + code + ": " + msg);
            return;
        }

        BufferedReader br = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        JSONObject resp = new JSONObject(sb.toString());
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            // Check for prompt feedback / block reason
            JSONObject pf = resp.optJSONObject("promptFeedback");
            String blockReason = pf != null ? pf.optString("blockReason", "") : "";
            if (!blockReason.isEmpty()) {
                cb.onError("İçerik engellendi: " + blockReason);
            } else {
                cb.onError("Model boş yanıt döndürdü.");
            }
            return;
        }

        JSONObject candidate = candidates.getJSONObject(0);

        // Check finish reason
        String finishReason = candidate.optString("finishReason", "");
        if ("SAFETY".equals(finishReason)) {
            cb.onError("İçerik güvenlik filtresi engelledi.");
            return;
        }

        JSONObject content = candidate.optJSONObject("content");
        if (content == null) {
            cb.onError("Yanıt içeriği yok. finishReason=" + finishReason);
            return;
        }

        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            cb.onError("Yanıt parçaları yok.");
            return;
        }

        StringBuilder textOut = new StringBuilder();
        JSONArray toolCalls = new JSONArray();
        JSONArray modelParts = new JSONArray();

        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.has("text")) {
                String t = part.getString("text").trim();
                // Skip "thinking" parts (they start with <thinking> in some models)
                if (!t.isEmpty()) {
                    textOut.append(t);
                    modelParts.put(new JSONObject().put("text", t.isEmpty() ? "." : t));
                }
            } else if (part.has("functionCall")) {
                JSONObject fc = part.getJSONObject("functionCall");
                toolCalls.put(fc);
                // Keep original functionCall part in history
                modelParts.put(part);
            }
        }

        // Save model turn to history
        JSONObject modelTurn = new JSONObject();
        modelTurn.put("role", "model");
        if (modelParts.length() == 0) {
            modelParts.put(new JSONObject().put("text", "."));
        }
        modelTurn.put("parts", modelParts);
        history.put(modelTurn);

        cb.onResponse(textOut.toString().trim(), toolCalls);
    }

    public void clearHistory() {
        while (history.length() > 0) {
            try { history.remove(0); } catch (Exception ignored) { break; }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SYSTEM PROMPT
    // ─────────────────────────────────────────────────────────────────────
    public static String buildSystemPrompt(String userName, String memStr) {
        String now = new SimpleDateFormat("dd MMMM yyyy, EEEE HH:mm",
            new Locale("tr")).format(new Date());
        String name = (userName != null && !userName.isEmpty()) ? userName : "kullanıcı";
        return "Sen MFD'sin (Multi-Function Device), " + name + " için kişisel Android yapay zekâ asistanısın.\n"
            + "Geliştirici: Mehmet Fatih DURSUN\n"
            + "Şu an: " + now + "\n"
            + (memStr != null && !memStr.isEmpty() ? memStr + "\n" : "")
            + "Kurallar:\n"
            + "1) Türkçe konuş, kısa ve net cevap ver — sesli yanıt olduğu için gereksiz uzun cümle kurma.\n"
            + "2) Bir görev varsa HEMEN uygun aracı çağır; açıklama yapmadan önce aracı çalıştır.\n"
            + "3) Mesaj/arama/etkinlik gibi işlemlerde onay sistemi otomatik devreye girer; tekrar sorma.\n"
            + "4) Bildirim geldiğinde auto_reply_suggest aracını kullan.\n"
            + "5) Belirsiz isteklerde mantıklı varsayım yap; kişi/tarih/saat netse direkt aracı çağır.\n"
            + "6) play_media için provider=youtube veya provider=spotify kullan.\n"
            + "7) Selamlaşma/genel sohbette araç çağırma; kısa ve sıcak yanıt ver.\n";
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOOL DECLARATIONS
    // ─────────────────────────────────────────────────────────────────────
    public static JSONArray buildToolDeclarations() {
        try {
            JSONArray t = new JSONArray();
            t.put(tool("open_app","Yüklü bir uygulamayı açar",
                new String[]{"app_name"}, new String[]{"STRING"},
                new String[]{"Uygulama adı (örn. spotify, instagram, kamera)"},
                new String[]{"app_name"}));
            t.put(tool("sys_info","Sistem bilgisi (pil, ram, disk, saat, tarih, ağ)",
                new String[]{"query"}, new String[]{"STRING"},
                new String[]{"battery|ram|disk|time|date|network|all"},
                new String[]{"query"}));
            t.put(tool("get_weather","Hava durumu getirir",
                new String[]{"location"}, new String[]{"STRING"},
                new String[]{"Şehir adı — boşsa kayıtlı konum kullanılır"},
                new String[]{}));
            t.put(tool("get_calendar_events","Yaklaşan takvim etkinliklerini listeler",
                new String[]{"query","limit"}, new String[]{"STRING","INTEGER"},
                new String[]{"today|tomorrow|week|all","Maks. sayı (varsayılan 6)"},
                new String[]{"query"}));
            t.put(tool("add_calendar_event","Takvime etkinlik ekler",
                new String[]{"title","start_iso","end_iso","notes","location","all_day"},
                new String[]{"STRING","STRING","STRING","STRING","STRING","BOOLEAN"},
                new String[]{"Başlık","ISO başlangıç (yyyy-MM-ddTHH:mm)","ISO bitiş","Not","Konum","Tüm gün mü"},
                new String[]{"title","start_iso"}));
            t.put(tool("delete_calendar_event","Takvimden etkinlik siler",
                new String[]{"title"}, new String[]{"STRING"},
                new String[]{"Silinecek etkinlik başlığı"},
                new String[]{"title"}));
            t.put(tool("add_reminder","Anımsatıcı + alarm kurar",
                new String[]{"title","due_iso","notes"},
                new String[]{"STRING","STRING","STRING"},
                new String[]{"Başlık","ISO tarih-saat","Not"},
                new String[]{"title"}));
            t.put(tool("get_reminders","Anımsatıcıları listeler",
                new String[]{"query"}, new String[]{"STRING"},
                new String[]{"all|today"},
                new String[]{"query"}));
            t.put(tool("browser_control","Tarayıcıda aç, Google/YouTube'da ara",
                new String[]{"action","url","query"},
                new String[]{"STRING","STRING","STRING"},
                new String[]{"open_url|search|play_youtube","URL","Sorgu"},
                new String[]{"action"}));
            t.put(tool("play_media","Müzik/video aç (YouTube/Spotify)",
                new String[]{"query","provider"},
                new String[]{"STRING","STRING"},
                new String[]{"Arama metni","youtube|spotify|auto"},
                new String[]{"query"}));
            t.put(tool("send_whatsapp","WhatsApp mesajı gönder — onay sistemi devreye girer",
                new String[]{"phone_or_name","message"},
                new String[]{"STRING","STRING"},
                new String[]{"Numara veya kişi adı","Mesaj metni"},
                new String[]{"phone_or_name","message"}));
            t.put(tool("send_sms","SMS gönder",
                new String[]{"phone","message"},
                new String[]{"STRING","STRING"},
                new String[]{"Numara","Mesaj"},
                new String[]{"phone","message"}));
            t.put(tool("call_phone","Telefon araması yap",
                new String[]{"phone_or_name"}, new String[]{"STRING"},
                new String[]{"Numara veya kişi adı"},
                new String[]{"phone_or_name"}));
            t.put(tool("send_email","E-posta hazırla ve gönderme uygulamasını aç",
                new String[]{"to","subject","body"},
                new String[]{"STRING","STRING","STRING"},
                new String[]{"Alıcı e-posta","Konu","İçerik"},
                new String[]{"to"}));
            t.put(tool("set_alarm","Belirli saatte alarm kurar",
                new String[]{"hour","minute","message"},
                new String[]{"INTEGER","INTEGER","STRING"},
                new String[]{"0-23","0-59","Alarm etiketi"},
                new String[]{"hour","minute"}));
            t.put(tool("start_timer","Geri sayım (sayaç) başlatır",
                new String[]{"seconds","message"},
                new String[]{"INTEGER","STRING"},
                new String[]{"Saniye sayısı","Sayaç etiketi"},
                new String[]{"seconds"}));
            t.put(tool("toggle_flashlight","Cep feneri açar/kapatır",
                new String[]{"state"}, new String[]{"STRING"},
                new String[]{"on|off"},
                new String[]{"state"}));
            t.put(tool("set_volume","Ses seviyesini ayarlar",
                new String[]{"stream","percent"},
                new String[]{"STRING","INTEGER"},
                new String[]{"music|ring|alarm|call|notif","0-100 yüzde"},
                new String[]{"percent"}));
            t.put(tool("open_system_setting","Sistem ayar sayfası açar",
                new String[]{"section"}, new String[]{"STRING"},
                new String[]{"wifi|bluetooth|sound|display|battery|airplane|location|data"},
                new String[]{"section"}));
            t.put(tool("navigate","Google Haritalar navigasyon başlatır",
                new String[]{"destination"}, new String[]{"STRING"},
                new String[]{"Hedef adres veya yer adı"},
                new String[]{"destination"}));
            t.put(tool("get_currency","Döviz/para kuru bilgisi",
                new String[]{"base","target"},
                new String[]{"STRING","STRING"},
                new String[]{"Kaynak para (USD/EUR/GBP)","Hedef para (TRY/USD)"},
                new String[]{"target"}));
            t.put(tool("save_memory","Kalıcı hafızaya kaydet",
                new String[]{"category","key","value"},
                new String[]{"STRING","STRING","STRING"},
                new String[]{"Kategori (tercihler/kişiler/notlar)","Anahtar","Değer"},
                new String[]{"category","key","value"}));
            t.put(tool("delete_memory","Hafızadan sil",
                new String[]{"category","key","match_text"},
                new String[]{"STRING","STRING","STRING"},
                new String[]{"Kategori","Anahtar","Metin eşleşmesi"},
                new String[]{}));
            t.put(tool("auto_reply_suggest","Bildirim için otomatik cevap önerisi (iç araç)",
                new String[]{"app","sender","message","suggestion"},
                new String[]{"STRING","STRING","STRING","STRING"},
                new String[]{"WhatsApp|Telegram|SMS","Gönderen kişi","Gelen mesaj","Önerilen cevap metni"},
                new String[]{"sender","message","suggestion"}));
            return t;
        } catch (Exception e) { return new JSONArray(); }
    }

    private static JSONObject tool(String name, String desc,
            String[] params, String[] types, String[] descs, String[] required) throws Exception {
        JSONObject t = new JSONObject();
        t.put("name", name);
        t.put("description", desc);
        JSONObject props = new JSONObject();
        for (int i = 0; i < params.length; i++) {
            String typeStr = (i < types.length) ? types[i] : "STRING";
            JSONObject p = new JSONObject();
            p.put("type", typeStr);
            if (i < descs.length) p.put("description", descs[i]);
            props.put(params[i], p);
        }
        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        t.put("parameters", new JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", req));
        return t;
    }
}
