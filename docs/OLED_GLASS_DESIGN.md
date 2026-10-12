# OLED Glass arayüzü — 12 Ekim 2026

## Tasarım

- Telefon ve saat ana zemini, durum çubuğu ve gezinme çubuğu gerçek `#000000` siyah kullanır.
- Cam yüzeyler kömür grisi, saydam katmanlar, yukarıdan aşağı azalan gri ışık ve ince kenar yansımalarıyla çizilir. Kart köşeleri 28dp, iç yüzeyler 18dp ve düğmeler 22dp'dir.
- Gri palet pastel vurgularla güncellendi: mint #A8DCC8 (enerji/şarj), buz mavisi #ABCDEF (rota/seçim), lavanta #C6B6E5 (12V/anahtar), şeftali #E5CAA2 (bekleyen yetki) ve pudra #E6AFB4 (uyarı). Ana metin `#F5F5F5`, ikincil metin `#B8B8B8`, yardımcı metin `#929292` kullanır.
- Ana ekran büyük araç adı, Araç Kontrolü bölümü, yatay e-Motor kartı, tek renk kapı/bagaj çizgi ikonları ve başlıklı telemetri kartları içerir. Kart sırası ve görünürlüğü ayarları korunur. 380dp altındaki ekranlarda veya yazı ölçeği 1.25 üzerindeyken telemetri kartları tek sütuna geçer; lastik hücreleri mevcut genişliği paylaşır.
- Basılı durumdaki düğmelerde gri dalga geri bildirimi vardır. Telefon ve saatin ana eylemlerinde 48dp hedefler, kapı/bagaj ikonlarında 64dp hedefler kullanılır.
- Ayarlar, sertifika kurulum pencereleri, yerel giriş çerçevesi, saat kartları, Tile ve widget ortak paleti kullanır. Resmî Togg ID web giriş sayfasının kendi görünümü değiştirilmez.
- Harita katmanı ve widget harita görüntüsü griye çevrilir. Düşük lastik basıncı telefon kartında ve widget'ta `!` işaretiyle belirtilir; normal/uyarı, bağlantı ve yetkilendirme durumları açık metinle gösterilir.
- Önceden kaydedilmiş sistem/aydınlık tema tercihlerinden bağımsız olarak OLED görünümü uygulanır. Eski tercih kaydı silinmez, aydınlık/sistem radyo seçenekleri yeni görünümde gizlenir.

Bu sürüm gerçek arka plan bulanıklığı uygulamaz. Cam etkisi Android'in yerel drawable katmanlarıyla üretilir; ek bulanıklık kitaplığı veya devamlı animasyon kullanılmaz.

## Kurulum ve inceleme

`ToggCompanion-oled-glass-debug.apk` telefon içindir; `ToggCompanion-wear-oled-glass-debug.apk` saat içindir. Önceki debug sürümünün üzerine kurun. BLE ve rota iş mantığı bu tasarım değişikliğiyle değiştirilmedi.

Telemetri, ayarlar, BLE kurulum ekranı, batarya/lastik ayrıntıları ve saat kartlarını cihazda inceleyin. Büyük yazı ölçeğinde kesilme veya eksik dokunma alanı görürseniz ekran görüntüsünü paylaşın.

Yerel ortamda bağlı Android cihazı veya yapılandırılmış AVD yoktur; gerçek cihaz ekran görüntüsü ve görsel onay manuel incelemeyi bekler. APK derleme sonucu teslim raporunda kayıtlıdır.

## Sonraki aşama

Companion içinden “Akıllı cihazıma aktar” manuel akışı uygulanmıştır; güncel alan adıyla araç teslimi manuel doğrulanacaktır. Sonrasında 7 dakikada bir canlı telemetri → ABRP → değişen rota/duraklar ve SoC bilgisi → araç aktarımı akışını adım adım geliştireceğiz. Manuel rota akışında otomatik Trumore fallback’i kaldırılmıştır.

## Pastel güncelleme — kaynak kod

Gerçek #000000 ana zemin korunur. Batarya, sürüş ve 12V kartlarına çok hafif mint/mavi/lavanta cam tonu; ana eylemlere koyu mint yüzey ve pastel kenar; seçili sekmeye mavi kenar uygulanır. Metinler açık nötr renkte, haritalar gri kalır. Saat/Tile ve widget göstergeleri aynı pastel durum paletini kullanır. Bu güncelleme henüz APK’ye alınmadı; kullanıcı derleme isteyene kadar mevcut APK değişmez.
