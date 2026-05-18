package com.mfd.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * MFD - Mehmet Fatih DURSUN
 * Persistent memory manager v4
 */
public class MemoryManager {

    private static final String PREF = "mfd_memory";
    private static final int MAX_ITEMS_PER_CAT = 20;

    private final SharedPreferences prefs;

    public MemoryManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void save(String category, String key, String value) {
        try {
            String raw = prefs.getString(category, "{}");
            JSONObject obj = new JSONObject(raw);

            // Enforce max limit per category
            if (!obj.has(key) && obj.length() >= MAX_ITEMS_PER_CAT) {
                // Remove oldest key
                Iterator<String> it = obj.keys();
                if (it.hasNext()) obj.remove(it.next());
            }

            obj.put(key, value);
            prefs.edit().putString(category, obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String delete(String category, String key, String matchText) {
        try {
            if (category != null && !category.isEmpty() && key != null && !key.isEmpty()) {
                String raw = prefs.getString(category, "{}");
                JSONObject obj = new JSONObject(raw);
                obj.remove(key);
                prefs.edit().putString(category, obj.toString()).apply();
                return "Silindi: " + category + "/" + key;
            }
            if (matchText != null && !matchText.isEmpty()) {
                // Search all categories for matching text
                int count = 0;
                for (String cat : prefs.getAll().keySet()) {
                    String raw = prefs.getString(cat, "{}");
                    JSONObject obj = new JSONObject(raw);
                    JSONArray toRemove = new JSONArray();
                    Iterator<String> it = obj.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (obj.optString(k).contains(matchText) || k.contains(matchText)) {
                            toRemove.put(k);
                        }
                    }
                    for (int i = 0; i < toRemove.length(); i++) {
                        obj.remove(toRemove.getString(i));
                        count++;
                    }
                    prefs.edit().putString(cat, obj.toString()).apply();
                }
                return count + " öğe silindi.";
            }
            return "Silinecek öğe belirtilmedi.";
        } catch (Exception e) { return "Silme hatası: " + e.getMessage(); }
    }

    public String toPromptString() {
        try {
            StringBuilder sb = new StringBuilder("Hafıza:\n");
            boolean hasAny = false;
            for (String cat : prefs.getAll().keySet()) {
                String raw = prefs.getString(cat, "{}");
                JSONObject obj = new JSONObject(raw);
                if (obj.length() == 0) continue;
                hasAny = true;
                sb.append("[").append(cat).append("]\n");
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    sb.append("  ").append(k).append(": ").append(obj.optString(k)).append("\n");
                }
            }
            return hasAny ? sb.toString() : "";
        } catch (Exception e) { return ""; }
    }

    /** Return memory as JSON string for export/debug. */
    public String exportJson() {
        try {
            JSONObject all = new JSONObject();
            for (String cat : prefs.getAll().keySet()) {
                all.put(cat, new JSONObject(prefs.getString(cat, "{}")));
            }
            return all.toString(2);
        } catch (Exception e) { return "{}"; }
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
