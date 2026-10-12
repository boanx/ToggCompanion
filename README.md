# Togg T10F Asistan & Saat (ToggCompanion)

Samsung telefonlar ve Samsung Galaxy Watch (Wear OS 3/4/5) akıllı saatler için Togg T10F araçlarına özel geliştirilmiş hafif, batarya dostu ve AMOLED siyah temalı yerel yardımcı ekosistem.

## Güncel durum — 12 Ekim 2026

Smart Access sertifika/imzalı BLE oturumu, doğrudan rota aktarımı ve pastel OLED
Glass görünümü kaynak kodda uygulanmıştır. Rota aktarımındaki HTTP 301 için
navigasyon API alan adı düzeltilmiştir. Fiziksel araç doğrulaması manueldir.

Güncel telefon APK’sı HTTP301 düzeltmesini,4 dakikalık ABRP otomatik rota/telemetri kontrolünü ve One UI widget düzenlemesini içerir. Batarya yüzdelerinin siyah etiketleri kaldırıldı; yenileme butonu %80 boyuta küçültülüp ikon ortalandı. Android/araç canlı doğrulaması manueldir.

APK: [GitHub Releases](https://github.com/boanx/ToggCompanion/releases). Kullanım ve geliştirme: [Agent devir rehberi](docs/AGENT_HANDOFF.md). Kullanıcı açıkça istemeden yeni APK derlenmez.

Detaylar: [Güncel proje durumu](docs/PROJECT_STATUS_2026_10_12.md).

---

## ⌚ FAZ 2: Samsung Galaxy Watch (Wear OS) Akıllı Saat Uygulaması

Samsung Galaxy Watch için sıfırdan tasarlanan minimal, saf AMOLED siyah zeminli ve dairesel ekran odaklı saat modülü.

### 🎯 Odaklanılan 4 Temel Bilgi:
1. **Yüksek Voltaj Batarya & Menzil:**
   - Batarya doluluk oranı (% SoC)
   - Kalan tahmini menzil (km)
   - Şarj durumu ve şarj gücü (⚡ kW)
2. **12V Destek Aküsü Seviyesi:**
   - 12V akü durumu (% SoC)
   - Normal (Yeşil) veya kritik %60 altı için acil uyarı (Kırmızı)
3. **Lastik Basınçları Durumu:**
   - Tek bakışta anlaşılır minimal özet: `Lastikler: Normal (36 PSI)` veya `Lastik: UYARI (Düşük Basınç)`
4. **Araç Konumu & Araca Yürü (Pusula):**
   - Son park yeri adresi (Örn: Kadıköy, İstanbul)
   - Saatin GPS'ine göre kuş uçuşu mesafe ve dinamik pusula/yön oku (Örn: `240 m Kuzeydoğu ↗️`)
   - **🗺️ Araca Yürü Butonu:** Tek dokunuşla saatte kurulu Google Haritalar yaya navigasyonunu başlatır.

### 🌟 Wear OS Özellikleri:
- **Wear OS Tile (Kutucuk / Hızlı Bakış):** Uygulama listesine girmeden saat kadranından sola kaydırınca doğrudan çıkan hızlı durum kutucuğu.
- **Dairesel AMOLED Tasarım:** Galaxy Watch yuvarlak ekranına tam oturan, pikselleri kapatarak pil tüketmeyen OLED siyah arayüz.
- **Companion Bluetooth Senkronizasyonu:** Saatte oturum veya token girmeye gerek kalmaz; telefonunuz Bluetooth üzerinden verileri arka planda aktarır.
- **🔄 Dokunmatik Senkronizasyon & Haptik Titreşim:** Saat üzerinden anında telefondan canlı veri talep edilebilir.

--

## Mühendislik ve Agent Rehberi

Mimari kanallar, operasyonel öğrenimler ve katkı kuralları için [Mühendislik Öğrenimleri ve Agent Rehberi](docs/ENGINEERING_LEARNINGS_AND_AGENT_GUIDE.md) belgesine bakın.


## Güncel saat teslimi

Son kaynaklardan Wear OS debug APK’sı da derlendi; [GitHub Releases](https://github.com/boanx/ToggCompanion/releases) üzerinden saat paketini alın. Saat minSDK30 ve telefonla aynı paket/imza gerektirir. Kurulum/eşitleme adımları [Agent devir rehberinde](docs/AGENT_HANDOFF.md). Fiziksel saat doğrulaması manueldir.
