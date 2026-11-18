# Togg T10F Asistan & Saat (ToggCompanion)

Samsung telefonlar ve Samsung Galaxy Watch (Wear OS 3/4/5) akıllı saatler için Togg T10F araçlarına özel geliştirilmiş hafif, batarya dostu ve AMOLED siyah temalı yerel yardımcı ekosistem.

## Güncel durum — 18 Kasım 2026

Telefon sürümü **1.1** (versionCode2). Son paket şarjda yanlış “bağlı değil”
gösterimini düzelten CHRGMODE_CHARGE/metinsel durum ayrıştırmasını içerir.
Şarj gücü servisten gelirse gösterilir; ölçüm yoksa yalnız “Şarj oluyor” yazar.
Sabit11/120kWvarsayımları kaldırılmıştır.

- Yol Tarifi sayfasından doğrudan araca hedef/rota aktarımı.
- ABRP'ye kullanıcı açıp kapatabildiği1dakikalık batarya/Yolculuğum tüketim
  aktarımı. ABRP rota değişikliğini otomatik araca gönderme kaldırıldı.
- Ayarlar → Kilometre paylaşımı: kullanıcı seçtiği Drive belge sağlayıcısında
  tek JSON, başlangıç tarih/saati ve en az15dakikalık tekrar aralığı.
- Gerçek odometre, düzeltilmiş yolculuk süresi/sürüş modu, kayıtlı kart sırası,
  widget araç veri zamanı/yenilemesi ve araç-konum kapsamlı harita kaydı.
- Paylaşılabilir, hassas verileri maskeleyen uygulama günlüğü.

Son telefon paketi163test ve lint0hata/330uyarı ile doğrulandı; APK v2imzası
kontrol edildi. Gerçek şarj/Drive eşitlemesi için fiziksel doğrulama hâlâ gerekir.
Bulut sorgusu yapmak aracın yeni ölçüm yayımladığını garanti etmez.
Smart Access'te paylaşılan sertifika alınmıştır; araçta imzalı BLE oturumu ve
kilit/bagaj/e-motor kontrollerinin çalışması henüz doğrulanmamıştır.

[1.1 telefon paketi ve sürüm notları](https://github.com/boanx/ToggCompanion/releases/tag/v1.1-20261118).

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

Son saat APK'sı12Ekim2026tarihli ayrı [Wear sürümündedir](https://github.com/boanx/ToggCompanion/releases/tag/v1.0-20261012-wear).
18Kasım telefon paketlemesinde saat APK'sı yeniden derlenmedi; güncel saat
kaynaklarındaki şarj gösterimi değişiklikleri eski saat APK'sında bulunmaz.
Saat minSDK30ve telefonla aynı paket/imza gerektirir. Kurulum/eşitleme adımları
[Agent devir rehberinde](docs/AGENT_HANDOFF.md). Fiziksel saat doğrulaması manueldir.
