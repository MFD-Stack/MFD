package com.mfd.assistant;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * MFD - Mehmet Fatih DURSUN
 * Android Actions v4 — tam sistem kontrolü
 */
public class AndroidActions {

    private final Context ctx;

    public AndroidActions(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    // ── SYS INFO ──────────────────────────────────────────────────────────
    public String sysInfo(String query) {
        if (query == null) query = "all";
        query = query.toLowerCase().trim();
        List<String> r = new ArrayList<>();
        if (query.contains("battery") || query.contains("pil") || query.equals("all")) r.add(getBattery());
        if (query.contains("ram") || query.contains("bellek") || query.equals("all"))   r.add(getRam());
        if (query.contains("disk") || query.contains("storage") || query.equals("all")) r.add(getDisk());
        if (query.contains("time") || query.contains("saat") || query.equals("all"))
            r.add("Saat: " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        if (query.contains("date") || query.contains("tarih") || query.equals("all"))
            r.add("Tarih: " + new SimpleDateFormat("dd MMMM yyyy, EEEE", new Locale("tr")).format(new Date()));
        if (query.contains("network") || query.contains("ağ") || query.equals("all")) r.add(getNetwork());
        if (r.isEmpty()) r.add("Bilinmeyen sorgu: " + query);
        StringBuilder sb = new StringBuilder();
        for (String s : r) sb.append(s).append("\n");
        return sb.toString().trim();
    }

    private String getBattery() {
        try {
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return "Pil: —";
            int lvl    = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale  = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean ch = status == BatteryManager.BATTERY_STATUS_CHARGING
                      || status == BatteryManager.BATTERY_STATUS_FULL;
            return String.format(Locale.getDefault(), "Pil: %.0f%% (%s)",
                    (lvl / (float) scale) * 100, ch ? "Şarj oluyor" : "Pilde");
        } catch (Exception e) { return "Pil: bilgi alınamadı"; }
    }

    private String getRam() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                ctx.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long total = mi.totalMem  / (1024*1024);
            long avail = mi.availMem  / (1024*1024);
            return String.format(Locale.getDefault(), "RAM: %dMB / %dMB kullanımda", total-avail, total);
        } catch (Exception e) { return "RAM: bilgi alınamadı"; }
    }

    private String getDisk() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            long bs    = s.getBlockSizeLong();
            long total = s.getBlockCountLong() * bs / (1024L*1024*1024);
            long avail = s.getAvailableBlocksLong() * bs / (1024L*1024*1024);
            return String.format(Locale.getDefault(), "Disk: %dGB boş / %dGB toplam", avail, total);
        } catch (Exception e) { return "Disk: bilgi alınamadı"; }
    }

    private String getNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network net = cm.getActiveNetwork();
                if (net == null) return "Ağ: bağlantı yok";
                NetworkCapabilities nc = cm.getNetworkCapabilities(net);
                if (nc == null) return "Ağ: bağlantı yok";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Ağ: Wi-Fi bağlı";
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Ağ: Mobil veri bağlı";
                return "Ağ: bağlı";
            } else {
                @SuppressWarnings("deprecation")
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) return "Ağ: " + ni.getTypeName() + " bağlı";
            }
        } catch (Exception ignored) {}
        return "Ağ: bağlantı yok";
    }

    // ── WEATHER ───────────────────────────────────────────────────────────
    public String getWeather(String location) {
        if (location == null || location.isEmpty())
            location = new AppConfig(ctx).getLocation();
        try {
            URL url = new URL("https://wttr.in/" + Uri.encode(location) + "?format=j1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MFD-Android/4.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = br.readLine()) != null) sb.append(l); br.close();
            org.json.JSONObject j = new org.json.JSONObject(sb.toString());
            org.json.JSONObject cur = j.getJSONArray("current_condition").getJSONObject(0);
            String temp = cur.optString("temp_C", "?");
            String feel = cur.optString("FeelsLikeC", "?");
            String desc = cur.getJSONArray("weatherDesc").getJSONObject(0).optString("value", "");
            String hum  = cur.optString("humidity", "?");
            return location + ": " + temp + "°C, " + desc.toLowerCase()
                    + ", hissedilen " + feel + "°C, nem %" + hum;
        } catch (Exception e) { return "Hava durumu alınamadı: " + e.getMessage(); }
    }

    // ── CALENDAR ──────────────────────────────────────────────────────────
    public String getCalendarEvents(String query, int limit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return "Takvim izni gerekiyor.";
        try {
            long now = System.currentTimeMillis();
            long start = now, end;
            String q = query == null ? "" : query.toLowerCase();
            if (q.contains("bugün") || q.contains("today")) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
                end = c.getTimeInMillis();
            } else if (q.contains("yarın") || q.contains("tomorrow")) {
                Calendar c = Calendar.getInstance();
                c.add(Calendar.DAY_OF_YEAR, 1);
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
                start = c.getTimeInMillis();
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
                end = c.getTimeInMillis();
            } else if (q.contains("hafta") || q.contains("week")) {
                end = now + 7L * 24 * 3600 * 1000;
            } else {
                end = now + 30L * 24 * 3600 * 1000;
            }
            Uri.Builder b = Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, start);
            ContentUris.appendId(b, end);
            Cursor c = ctx.getContentResolver().query(b.build(),
                    new String[]{Instances.TITLE, Instances.BEGIN, Instances.EVENT_LOCATION},
                    null, null, Instances.START_DAY + " ASC");
            if (c == null) return "Takvim alınamadı.";
            List<String> ev = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM HH:mm", new Locale("tr"));
            while (c.moveToNext() && ev.size() < limit) {
                String entry = sdf.format(new Date(c.getLong(1))) + " — " + c.getString(0);
                String loc = c.getString(2);
                if (loc != null && !loc.isEmpty()) entry += " (" + loc + ")";
                ev.add(entry);
            }
            c.close();
            if (ev.isEmpty()) return "Etkinlik bulunamadı.";
            return "Takvim:\n" + android.text.TextUtils.join("\n", ev);
        } catch (Exception e) { return "Takvim hatası: " + e.getMessage(); }
    }

    public String addCalendarEvent(String title, String startIso, String endIso,
                                    String notes, String location, boolean allDay) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return "Takvim yazma izni gerekiyor.";
        try {
            long calId = getFirstCalendarId();
            if (calId < 0) return "Takvim hesabı bulunamadı.";
            long sMs = parseIso(startIso);
            long eMs = (endIso != null && !endIso.isEmpty()) ? parseIso(endIso) : sMs + 3600000L;
            ContentValues v = new ContentValues();
            v.put(Events.CALENDAR_ID, calId);
            v.put(Events.TITLE, title);
            v.put(Events.DTSTART, sMs);
            v.put(Events.DTEND, eMs);
            v.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            if (notes != null && !notes.isEmpty()) v.put(Events.DESCRIPTION, notes);
            if (location != null && !location.isEmpty()) v.put(Events.EVENT_LOCATION, location);
            if (allDay) v.put(Events.ALL_DAY, 1);
            Uri uri = ctx.getContentResolver().insert(Events.CONTENT_URI, v);
            return uri != null ? "✓ Etkinlik eklendi: " + title : "Etkinlik eklenemedi.";
        } catch (Exception e) { return "Takvim ekleme hatası: " + e.getMessage(); }
    }

    public String deleteCalendarEvent(String title) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return "Takvim izni gerekiyor.";
        try {
            String[] proj = {Events._ID, Events.TITLE};
            Cursor c = ctx.getContentResolver().query(Events.CONTENT_URI, proj,
                    Events.TITLE + " LIKE ?", new String[]{"%" + title + "%"}, null);
            if (c == null || !c.moveToFirst()) { if (c!=null) c.close(); return "Etkinlik bulunamadı: " + title; }
            long id = c.getLong(0); c.close();
            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, id);
            int n = ctx.getContentResolver().delete(uri, null, null);
            return n > 0 ? "✓ Etkinlik silindi: " + title : "Silinemedi.";
        } catch (Exception e) { return "Silme hatası: " + e.getMessage(); }
    }

    public String getReminders(String query, int limit) {
        // Android'de yerleşik "reminder" API olmadığından takvimde "reminder" kategorisindeki etkinlikleri döndür
        return getCalendarEvents(query, limit);
    }

    public String addReminder(String title, String dueIso, String notes) {
        // Alarm olarak kur
        long dueMs = parseIso(dueIso);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dueMs);
        return setAlarm(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                title + (notes != null && !notes.isEmpty() ? " — " + notes : ""));
    }

    private long getFirstCalendarId() {
        try {
            Cursor c = ctx.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID},
                    CalendarContract.Calendars.VISIBLE + "=1", null,
                    CalendarContract.Calendars._ID + " ASC");
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0); c.close(); return id;
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
        return -1;
    }

    // ── MEDIA ─────────────────────────────────────────────────────────────
    public String playMedia(String query, String provider) {
        if (query == null || query.isEmpty()) return "Aranacak içerik belirtilmedi.";
        String p = provider == null ? "auto" : provider.toLowerCase();
        try {
            if (p.contains("spotify")) {
                // Spotify arama intent
                Intent si = new Intent(Intent.ACTION_SEARCH);
                si.setPackage("com.spotify.music");
                si.putExtra("query", query);
                si.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ctx.startActivity(si); return "Spotify'da aranıyor: " + query; }
                catch (Exception ignored) {}
            }
            // YouTube (varsayılan)
            Uri yt = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
            Intent intent = new Intent(Intent.ACTION_VIEW, yt);
            intent.setPackage("com.google.android.youtube");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(intent); return "YouTube'da aranıyor: " + query; }
            catch (Exception ignored) {}
            // Fallback: tarayıcıda
            intent = new Intent(Intent.ACTION_VIEW, yt);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return "YouTube (web) açıldı: " + query;
        } catch (Exception e) { return "Medya açılamadı: " + e.getMessage(); }
    }

    public String browserControl(String action, String url, String query) {
        try {
            if ("play_youtube".equals(action) || "youtube".equals(action)) {
                return playMedia(query != null ? query : url, "youtube");
            }
            Uri uri;
            if ("search".equals(action)) {
                uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query != null ? query : ""));
            } else {
                uri = Uri.parse(url != null && !url.isEmpty() ? url : "https://www.google.com");
            }
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "Tarayıcı açıldı.";
        } catch (Exception e) { return "Tarayıcı açılamadı: " + e.getMessage(); }
    }

    // ── WHATSAPP ──────────────────────────────────────────────────────────
    public String sendWhatsApp(String phoneOrName, String message) {
        try {
            String phone = phoneOrName == null ? "" : phoneOrName.replaceAll("[^0-9+]", "");
            if (phone.isEmpty() && phoneOrName != null) {
                String found = lookupContactNumber(phoneOrName);
                if (found != null) phone = found.replaceAll("[^0-9+]", "");
            }
            Uri uri;
            if (!phone.isEmpty()) {
                uri = Uri.parse("https://api.whatsapp.com/send?phone=" + phone
                        + "&text=" + Uri.encode(message));
            } else {
                // Kişi adıyla direkt WA share intent
                uri = Uri.parse("whatsapp://send?text=" + Uri.encode(message));
            }
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage("com.whatsapp");
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "WhatsApp açıldı — " + (phone.isEmpty() ? phoneOrName : phone);
        } catch (Exception e) {
            // Fallback: share sheet
            try {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("text/plain");
                s.putExtra(Intent.EXTRA_TEXT, message);
                s.setPackage("com.whatsapp");
                s.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(s);
                return "WhatsApp paylaşma açıldı.";
            } catch (Exception ex) { return "WhatsApp açılamadı: " + e.getMessage(); }
        }
    }

    // ── SMS ───────────────────────────────────────────────────────────────
    public String sendSms(String phone, String message) {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                SmsManager sm;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    sm = ctx.getSystemService(SmsManager.class);
                } else {
                    @SuppressWarnings("deprecation")
                    SmsManager d = SmsManager.getDefault();
                    sm = d;
                }
                if (sm != null) {
                    sm.sendTextMessage(phone, null, message, null, null);
                    return "SMS gönderildi: " + phone;
                }
            }
            // Fallback: SMS uygulaması
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
            intent.putExtra("sms_body", message);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return "SMS uygulaması açıldı.";
        } catch (Exception e) { return "SMS gönderilemedi: " + e.getMessage(); }
    }

    // ── CALL ──────────────────────────────────────────────────────────────
    public String callPhone(String phoneOrName) {
        try {
            String phone = phoneOrName == null ? "" : phoneOrName.replaceAll("[^0-9+]", "");
            if (phone.isEmpty() && phoneOrName != null) {
                String found = lookupContactNumber(phoneOrName);
                if (found != null) phone = found.replaceAll("[^0-9+]", "");
            }
            if (phone.isEmpty()) return "Numara bulunamadı: " + phoneOrName;
            Intent intent;
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED) {
                intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
            } else {
                intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return "Arama başlatıldı: " + phone;
        } catch (Exception e) { return "Arama başarısız: " + e.getMessage(); }
    }

    // ── EMAIL ─────────────────────────────────────────────────────────────
    public String sendEmail(String to, String subject, String body) {
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
            if (to != null && !to.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
            if (subject != null) i.putExtra(Intent.EXTRA_SUBJECT, subject);
            if (body != null)    i.putExtra(Intent.EXTRA_TEXT, body);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "E-posta hazırlandı: " + to;
        } catch (Exception e) { return "E-posta açılamadı: " + e.getMessage(); }
    }

    // ── OPEN APP ──────────────────────────────────────────────────────────
    public String openApp(String name) {
        if (name == null || name.isEmpty()) return "Uygulama adı belirtilmedi.";
        String pkg = resolvePackage(name.toLowerCase());
        if (pkg != null) {
            Intent li = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (li != null) {
                li.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(li);
                return name + " açıldı.";
            }
        }
        // Kurulu uygulamalarda ada göre ara
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(main, 0);
            String lower = name.toLowerCase();
            for (android.content.pm.ResolveInfo ri : apps) {
                String label = ri.loadLabel(pm).toString().toLowerCase();
                if (label.contains(lower)) {
                    Intent li = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                    if (li != null) {
                        li.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(li);
                        return ri.loadLabel(pm) + " açıldı.";
                    }
                }
            }
        } catch (Exception ignored) {}
        // Play Store'da ara
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=" + Uri.encode(name)));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return name + " Play Store'da aranıyor.";
        } catch (Exception e) { return name + " bulunamadı."; }
    }

    private String resolvePackage(String n) {
        if (n.contains("spotify"))   return "com.spotify.music";
        if (n.contains("youtube"))   return "com.google.android.youtube";
        if (n.contains("chrome"))    return "com.android.chrome";
        if (n.contains("whatsapp"))  return "com.whatsapp";
        if (n.contains("instagram")) return "com.instagram.android";
        if (n.contains("twitter") || n.equals("x")) return "com.twitter.android";
        if (n.contains("telegram"))  return "org.telegram.messenger";
        if (n.contains("gmail"))     return "com.google.android.gm";
        if (n.contains("harita") || n.contains("maps")) return "com.google.android.apps.maps";
        if (n.contains("ayarlar") || n.contains("settings")) return "com.android.settings";
        if (n.contains("telefon") || n.contains("phone")) return "com.android.dialer";
        if (n.contains("hesap") || n.contains("calculator")) return "com.android.calculator2";
        if (n.contains("saat") || n.contains("clock")) return "com.android.deskclock";
        if (n.contains("netflix"))   return "com.netflix.mediaclient";
        if (n.contains("tiktok"))    return "com.zhiliaoapp.musically";
        if (n.contains("discord"))   return "com.discord";
        if (n.contains("kamera") || n.contains("camera")) return null; // system camera
        if (n.contains("müzik") || n.contains("music")) return "com.google.android.music";
        if (n.contains("dosya") || n.contains("files")) return "com.google.android.apps.nbu.files";
        if (n.contains("takvim") || n.contains("calendar")) return "com.google.android.calendar";
        if (n.contains("fotoğraf") || n.contains("gallery")) return "com.google.android.apps.photos";
        return null;
    }

    // ── FLASHLIGHT ────────────────────────────────────────────────────────
    public String toggleFlashlight(String state) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = cm.getCameraIdList();
            for (String id : ids) {
                Boolean has = cm.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(has)) {
                    boolean on = state == null || !state.toLowerCase().startsWith("k")
                              && !state.equalsIgnoreCase("off");
                    cm.setTorchMode(id, on);
                    return on ? "Fener açıldı." : "Fener kapatıldı.";
                }
            }
            return "Bu cihazda fener bulunamadı.";
        } catch (CameraAccessException e) {
            return "Fener hatası: " + e.getMessage();
        } catch (Exception e) {
            return "Fener açılamadı: " + e.getMessage();
        }
    }

    // ── VOLUME ────────────────────────────────────────────────────────────
    public String setVolume(String stream, int percent) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int s = AudioManager.STREAM_MUSIC;
            if (stream != null) {
                stream = stream.toLowerCase();
                if (stream.contains("ring") || stream.contains("zil"))    s = AudioManager.STREAM_RING;
                else if (stream.contains("alarm"))                         s = AudioManager.STREAM_ALARM;
                else if (stream.contains("call") || stream.contains("ara")) s = AudioManager.STREAM_VOICE_CALL;
                else if (stream.contains("notif") || stream.contains("bildirim")) s = AudioManager.STREAM_NOTIFICATION;
            }
            int max = am.getStreamMaxVolume(s);
            int val = Math.max(0, Math.min(100, percent)) * max / 100;
            am.setStreamVolume(s, val, AudioManager.FLAG_SHOW_UI);
            return "Ses ayarlandı: %" + percent;
        } catch (SecurityException e) {
            return "Ses ayarı için Rahatsız Etme erişimi gerekiyor.";
        } catch (Exception e) { return "Ses hatası: " + e.getMessage(); }
    }

    // ── SYSTEM SETTINGS ───────────────────────────────────────────────────
    public String openSystemSetting(String section) {
        String action = Settings.ACTION_SETTINGS;
        if (section != null) {
            section = section.toLowerCase();
            if (section.contains("wifi"))          action = Settings.ACTION_WIFI_SETTINGS;
            else if (section.contains("bluetooth")) action = Settings.ACTION_BLUETOOTH_SETTINGS;
            else if (section.contains("ekran") || section.contains("display"))
                                                    action = Settings.ACTION_DISPLAY_SETTINGS;
            else if (section.contains("ses") || section.contains("sound"))
                                                    action = Settings.ACTION_SOUND_SETTINGS;
            else if (section.contains("data") || section.contains("mobil"))
                                                    action = Settings.ACTION_DATA_ROAMING_SETTINGS;
            else if (section.contains("pil") || section.contains("battery"))
                                                    action = Intent.ACTION_POWER_USAGE_SUMMARY;
            else if (section.contains("uçak") || section.contains("airplane"))
                                                    action = Settings.ACTION_AIRPLANE_MODE_SETTINGS;
            else if (section.contains("konum") || section.contains("location"))
                                                    action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
            else if (section.contains("güvenlik") || section.contains("security"))
                                                    action = Settings.ACTION_SECURITY_SETTINGS;
            else if (section.contains("uygulama") || section.contains("app"))
                                                    action = Settings.ACTION_APPLICATION_SETTINGS;
        }
        try {
            Intent i = new Intent(action);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "Ayar açıldı: " + section;
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return "Genel ayarlar açıldı.";
            } catch (Exception ex) { return "Ayarlar açılamadı."; }
        }
    }

    // ── ALARM / TIMER ─────────────────────────────────────────────────────
    public String setAlarm(int hour, int minute, String message) {
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
            i.putExtra(AlarmClock.EXTRA_HOUR, hour);
            i.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            if (message != null && !message.isEmpty())
                i.putExtra(AlarmClock.EXTRA_MESSAGE, message);
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return String.format(Locale.getDefault(), "✓ Alarm kuruldu: %02d:%02d", hour, minute);
        } catch (Exception e) { return "Alarm kurulamadı: " + e.getMessage(); }
    }

    public String startTimer(int seconds, String message) {
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
            i.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
            if (message != null && !message.isEmpty())
                i.putExtra(AlarmClock.EXTRA_MESSAGE, message);
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "✓ Sayaç başlatıldı: " + seconds + " sn";
        } catch (Exception e) { return "Sayaç başlatılamadı: " + e.getMessage(); }
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────
    public String navigate(String destination) {
        try {
            Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(destination));
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage("com.google.android.apps.maps");
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return "Navigasyon başlatıldı: " + destination;
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination)));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return "Navigasyon açıldı (web): " + destination;
            } catch (Exception ex) { return "Navigasyon açılamadı."; }
        }
    }

    // ── CURRENCY ──────────────────────────────────────────────────────────
    public String getCurrency(String base, String target) {
        try {
            if (base == null || base.isEmpty()) base = "USD";
            if (target == null || target.isEmpty()) target = "TRY";
            URL url = new URL("https://open.er-api.com/v6/latest/" + base.toUpperCase());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = br.readLine()) != null) sb.append(l); br.close();
            JSONObject j = new JSONObject(sb.toString());
            double v = j.getJSONObject("rates").optDouble(target.toUpperCase(), 0);
            if (v <= 0) return target + " kuru bulunamadı.";
            return String.format(Locale.getDefault(), "1 %s = %.4f %s",
                    base.toUpperCase(), v, target.toUpperCase());
        } catch (Exception e) { return "Kur alınamadı: " + e.getMessage(); }
    }

    // ── CONTACT LOOKUP ────────────────────────────────────────────────────
    String lookupContactNumber(String name) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return null;
        try {
            Cursor c = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                                 ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"}, null);
            if (c != null && c.moveToFirst()) {
                String num = c.getString(0); c.close(); return num;
            }
            if (c != null) c.close();
        } catch (Exception ignored) {}
        return null;
    }

    // ── HELPERS ───────────────────────────────────────────────────────────
    private long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return System.currentTimeMillis();
        String[] fmts = {
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        };
        for (String f : fmts) {
            try {
                SimpleDateFormat s = new SimpleDateFormat(f, Locale.getDefault());
                s.setTimeZone(TimeZone.getDefault());
                Date d = s.parse(iso);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return System.currentTimeMillis();
    }
}
