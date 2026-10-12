# Widget: canlı One UI renkleri

12 Ekim 2026 kaynak güncellemesi. One UI 8.5 görünümüne uyum hedeflenir; Samsung tarafından onaylanmış bir tema veya resmi 8.5 paletinin birebir kopyası değildir.

[Samsung One UI renk rehberi](https://developer.samsung.com/one-ui/color/system.html), koyu temada #3E91FF mavisini, geniş sakin yüzeyler üzerinde canlı küçük vurguları ve okunabilirliği önerir.

- Ana yüzey: koyu mavi → gerçek siyah, 28dp yuvarlak köşeler ve ince renkli kenar.
- Yenileme: 35,2dp mavi kapsül (önceki 44dp’nin %80’i), ortalanmış 22dp beyaz vektör ikon ve erişilebilir Türkçe açıklama.
- Yüksek voltaj pil göstergesi: eski dört aralık geri getirildi: %0–19 kırmızı #EF4444, %20–49 amber #FFB800, %50–79 One UI mavisi #3E91FF, %80–100 yeşil #10B981.
- 12V: %0–59 kırmızı, %60–79 amber, %80–100 yeşil; eski uyarı eşikleri korunur. Yüzdeler ve kutup işaretleri beyazdır; altlarında ayrı siyah etiket zemini bulunmaz. Batarya doluluk rengi yazıların arkasında görünür.
- Menzil: lacivert yol, mavi kenarlar, sıcak yol çizgileri; şarj sırasında mint çerçeve.
- Lastikler: mint kenarlı koyu kart, mercan uyarı rengi ve renk dışında da görülen ! işareti.
- Konum: mavi pin/halo ve lacivert radar; harita arka planının desatürasyonu korunur.

Widget paleti ayrı kaynak dosyasındadır; telefon/saat pastel OLED paleti değişmez. RemoteViews uyumlu mevcut kontrol türleri, tıklama akışları ve farklı boyut yerleşimleri korunur. Gerçek arka plan bulanıklığı eklenmez.

## Teslim durumu

Kullanıcının açık derleme isteğiyle 12 Ekim 2026 tarihinde telefon debug APK’sı başarıyla üretildi. XML sözdizimi ve KB tutarlılığı kontrol edildi. Güncel teslim APK’sı bu tasarımı ve geri getirilen batarya renk aralıklarını içerir. Emülatör/fiziksel One UI ekran kontrolü yapılmadı; kompakt/geniş widget görünümü kullanıcı tarafından cihazda değerlendirilecektir.

## Kullanıcı ekran görüntüsü sonrası düzeltme

Batarya yüzdeleri ve akü kutup işaretlerinin siyah etiketleri kaldırıldı. Yenileme düğmesi 35,2dp oldu; 22dp ikon 6,6dp eşit kenar boşluğu ile ortalanır. Fontun baseline/yan boşluk kaymasını önlemek için metin glifi yerine vektör kullanılır. Bu düzeltme kullanıcının sonraki açık derleme/yayın isteğiyle güncel telefon APK’sına alınmıştır. Fiziksel cihaz görünümü kullanıcı tarafından doğrulanacaktır.
