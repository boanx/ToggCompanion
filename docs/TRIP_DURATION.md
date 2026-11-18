# Yolculuk süresi — 16 Kasım 2026 kaynak düzeltmesi

## Son teslim — 16 Kasım 2026,1.1yolculuk süresi düzeltmesi

Kullanıcının açık APK isteğiyle süre düzeltmesi paketlendi. Sürüm1.1/kod2korundu (APK aapt2 ile doğrulandı). BUILD SUCCESSFUL in24s;136test/0hata,lint0hata/328uyarı,v2imza doğrulaması başarılı. `outputs/ToggCompanion-current-debug.apk`,4146291bayt,SHA256 `dda68e98077feb5cb8781b6bf0a614ea3cf0a43a5c712e5b4597fe36f8a3922f`. Üç yolculuk sekmesinde10a-bölme hatası ve toplam dakikaya saat ekleme riski giderilmiştir. Önceki1.1özellikleri de dahil.

Önceki paket/metadata,ara rapor ve üretilen kopya `obsolete/2026-11-16-trip-duration-package/move-manifest.json` ile arşivlendi; dışarıda tek APK var. Yeni APK sonrası başarılı telemetri yenilemesi eski cache'deki küçültülmüş süreyi günceller. Bu tur cihaz kurulumu/canlı araç doğrulaması,Wear build,push yapılmadı.


Kullanıcı Yolculuğum ekranında11km/19km-s verisi yanında3dk süresinin yanlış olduğunu bildirdi. Eski parseTripData; minutes>=10 ve hours=0olunca veya minutes>59olunca dakikayı10'a bölüyordu. Düz süre600dküstüyse bir kez daha10'a bölünüyordu. Bu büyüklük temelli tahmin30dk'yı3dk'ya dönüştürüyordu. Kullanıcının ham yanıtı veya gerçek doğru süresi görülmedi; hız/mesafeden süre türetilmedi.

Yeni ayrıştırma; duration:{hours,minutes} bileşenlerini hours*60+minutes olarak kullanır. Düz durationMinutes ve sayısal duration toplam dakika kabul edilir; yanlarında hours olsa dahi ikinci kez saat eklenmez. Düz hours/minutes bileşenleri de desteklenir. Saat-only2→120dk. Büyük sayılar ölçeklenmez;220dk→220dk,720dk→720dk. Son Şarjdan süresi eksikken varsayılan110dk üretimi0'a kaldırıldı. Yeni bir sağlayıcı birimi varsayarak saniye veya ondalık dakika dönüşümü eklenmedi. Canlı payload'da başka şema görülürse ayrıca kanıtla genişletilmelidir.

Ortak TripData düzeltmesi Güncel Sürüş,Son Şarjdan,Yolculuğum ve bu veriyi gösteren ana sayfa kartlarına uygulanır. Önceden hatalı küçültülerek kaydedilmiş cache ham süreyi içermediğinden yalnız yeni başarılı telemetri sorgusu ile düzelir; eski cache'i sayısal tahminle10'a çarpma yapılmaz.

Java kaynak tip kontrolü ve136JVM testi geçti. Üç yeni test tüm sekmeler,35/95/32dk,220/720dk,toplam dakikaya saat eklememe,saat-only ve eksik şarj süresini kapsar. Android kaynak derlemesi/APK/kurulum/canlı araç doğrulaması yapılmadı. Mevcut teslim1.1/kod2bu son kaynak düzeltmesini henüz içermez.
