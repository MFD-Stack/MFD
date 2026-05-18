# MFD — Multi-Function Device v4 (Final)
**Geliştirici: Mehmet Fatih DURSUN**

MFD, Android için geliştirilmiş yapay zekâ destekli kişisel sesli asistan uygulamasıdır.
Gemini API kullanarak cihazı sesli veya yazılı komutlarla kontrol eder.

---

## 🚀 GitHub'a Yükle ve APK Üret

### 1) Projeyi GitHub'a yükleme

```bash
# Yeni bir GitHub deposu oluşturduktan sonra:
cd MFD-v4-Android
git init
git add .
git commit -m "MFD v4 - initial"
git branch -M main
git remote add origin https://github.com/<KULLANICI_ADIN>/<REPO_ADIN>.git
git push -u origin main
```

> Veya GitHub web arayüzünden **"Add file → Upload files"** ile bu klasörün TÜM içeriğini
> (gizli `.github/` klasörü dahil) deponun köküne yükleyebilirsin.

### 2) APK otomatik oluşturulur

Push işleminden sonra deponda **Actions** sekmesine geç:

- Workflow adı: **"Build MFD Android APK"**
- Tamamlandığında sayfanın altındaki **Artifacts** bölümünden indir:
  - `MFD-v4-debug-apk` → telefona kurulabilir debug APK
  - `MFD-v4-release-unsigned-apk` → imzasız release (Play Store için imzalanmalı)

İlk build yaklaşık **6-9 dakika** sürer (cache sonrası ~2 dk).

### 3) APK'yı telefona kurma

1. APK dosyasını telefona kopyala
2. **Ayarlar → Güvenlik → Bilinmeyen Kaynaklar** açık olmalı
3. Dosya yöneticisinden APK'ya dokun ve kurulumu onayla

---

## 🔑 Gemini API Anahtarı

1. [Google AI Studio](https://aistudio.google.com)'ya git
2. **"Get API key" → Create API Key** (ücretsiz)
3. Uygulamayı aç → API anahtarını, adını ve şehrini gir
4. **"API'yi Test Et"** → çalışan model otomatik bulunur → **KAYDET**

---

## 🐛 v4 Düzeltmeleri (Final Sürüm)

### Bu Sürümde Eklenen Kritik Düzeltmeler
- ✅ **Eksik renk kaynakları eklendi** (`teal_primary`, `teal_text`, `teal_mid`, `teal_dim`, `color_gold`) — build hatası giderildi
- ✅ **GitHub Actions workflow** basitleştirildi ve sağlamlaştırıldı — wrapper JAR otomatik oluşturuluyor
- ✅ **`.gitignore`** eklendi — `build/`, `.gradle/`, IDE dosyaları repo'ya yüklenmez
- ✅ **Release APK** build adımı eklendi (artifacts'ta hazır)

### v4'te Daha Önce Düzeltilenler
- GeminiClient history yönetimi (Gemini API format uyumu)
- Tool declarations tip uyumu (INTEGER/STRING/BOOLEAN)
- minSdkVersion 24'e düşürüldü (Android 7.0+)
- SmsManager API guard (Android 12+)
- TTS LANG_MISSING_DATA fallback
- SpeechRecognizer hata sessizleştirme
- NetworkCapabilities (API 23+)
- ChatLogAdapter GradientDrawable
- FloatingBubble drag/click ayrımı
- WakeWordService crash → 1 sn restart

---

## 📱 Desteklenen Komutlar (Örnekler)

| Komut | İşlem |
|---|---|
| "Yarın sabah 7'ye alarm kur" | Alarm kurulur |
| "YouTube'da Tarkan aç" | YouTube araması |
| "Ahmet'e WhatsApp mesajı yaz" | Onay sonrası WA açılır |
| "Hava durumu nasıl?" | Anlık hava (wttr.in) |
| "Feneri aç" | Cep feneri |
| "Dolar kaç lira?" | Döviz kuru (open.er-api.com) |
| "Bugünkü etkinliklerimi göster" | Takvim |
| "Toplantı başlamadan 5 dk önce hatırlat" | Timer |

---

## 🛠 Teknik Bilgiler

| Özellik | Değer |
|---|---|
| minSdkVersion | 24 (Android 7.0+) |
| targetSdkVersion | 34 (Android 14) |
| compileSdk | 34 |
| Dil | Java 17 |
| AI Modeli | Gemini 2.5 Flash (otomatik seçim) |
| TTS/STT | Android yerleşik |
| Gradle | 8.4 |
| AGP | 8.3.2 |

### Kullanılan Servisler
- **Hava**: wttr.in (anahtarsız)
- **Döviz**: open.er-api.com (anahtarsız)
- **AI**: Google Gemini API (kullanıcı anahtarı)

---

## 🔐 Gizlilik
- Konuşmalar yalnızca **kendi** Gemini API anahtarınızla Google'a iletilir
- Yerel hafıza `SharedPreferences`'ta saklanır
- Üçüncü taraf analitik/telemetri **YOKTUR**

---

## 📁 Proje Yapısı
```
MFD-v4-Android/
├── .github/workflows/build.yml   # GitHub Actions (APK üretici)
├── app/
│   ├── build.gradle              # Modül ayarları
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mfd/assistant/   # 14 Java dosyası
│       └── res/                       # Layout, drawable, values
├── build.gradle                  # Proje üst düzey
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## ❓ Build Hatası Alırsam?

1. Actions sekmesinde workflow log'una bak
2. `JAVA 17` ve `Setup Android SDK` adımlarının yeşil olduğundan emin ol
3. "Generate Gradle Wrapper" adımı başarısız olursa Actions sekmesinde **Re-run jobs** dene
4. Workflow'u manuel tetikleme: Actions → "Build MFD Android APK" → **Run workflow**

---

**MFD v4 — Mehmet Fatih DURSUN © 2026**
