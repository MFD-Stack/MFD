package com.mfd.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MFD - Mehmet Fatih DURSUN
 * İlk kurulum ekranı v4
 */
public class SetupActivity extends AppCompatActivity {

    private static final String[] MODELS = {
        "gemini-2.5-flash-preview-05-20",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash"
    };

    private TextView tvTestResult;
    private String workingModel = null;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppConfig cfg = new AppConfig(this);
        if (cfg.hasGeminiKey()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_setup);

        EditText etKey  = findViewById(R.id.etGeminiKey);
        EditText etLoc  = findViewById(R.id.etLocation);
        EditText etName = findViewById(R.id.etUserName);
        Button   btnGo  = findViewById(R.id.btnSave);
        tvTestResult    = findViewById(R.id.tvTestResult);
        Button btnTest  = findViewById(R.id.btnTest);

        etKey.setText(cfg.getGeminiKey());
        etLoc.setText(cfg.getLocation());
        etName.setText(cfg.getUserName());

        btnTest.setOnClickListener(v -> {
            String key = etKey.getText().toString().trim();
            if (key.isEmpty()) { tvTestResult.setText("Önce API anahtarını girin!"); return; }
            tvTestResult.setText("Test ediliyor…");
            findWorkingModel(key);
        });

        btnGo.setOnClickListener(v -> {
            String key  = etKey.getText().toString().trim();
            String loc  = etLoc.getText().toString().trim();
            String name = etName.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "Gemini API anahtarı gerekli!", Toast.LENGTH_SHORT).show();
                return;
            }
            cfg.saveBasics(key, loc.isEmpty() ? "Istanbul" : loc, name);
            if (workingModel != null) cfg.setWorkingModel(workingModel);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void findWorkingModel(String key) {
        exec.execute(() -> {
            for (String model : MODELS) {
                try {
                    URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent?key=" + key);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    String body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}],"
                            + "\"generationConfig\":{\"maxOutputTokens\":10}}";
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes("UTF-8"));
                    }
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        workingModel = model;
                        ui.post(() -> tvTestResult.setText("✓ Çalışan model: " + model));
                        return;
                    }
                } catch (Exception ignored) {}
            }
            ui.post(() -> tvTestResult.setText("✗ Geçerli model bulunamadı. Anahtarı kontrol edin."));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
