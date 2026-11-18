# Şarj gücü — 16 Kasım 2026 kaynak düzeltmesi

18 Kasım 2026 güncel teslim: AF314telefon debug1.1/kod2,CHRGMODE_CHARGE ve
metinsel şarj durumu düzeltmesini içerir;163test/lint0hata/v2imza doğrulandı.
Gerçek şarjda yanıt/araç veri zamanı karşılaştırması henüz yapılmadı.
Aşağıdaki kaynak-only ifadeler tarihsel aşamalardır.

## 18 Kasım 2026 — şarjda bağlı değil regresyonu (kaynak)

AF312–AF313: orijinal APK strings içinde CHRGMODE_CHARGE ve
CHRGMODE_STANDBY enum'ları bulundu. Önceki sıkı matcher CHARGING token'ını
ve yalnız tam CHARGE değerini kabul ederken CHRGMODE_CHARGE'ı kaçırıyordu.
CHARGE token'ı sınırlarıyla kabul ediliyor; şarj gücü varsayımı geri gelmedi.
İkinci hata: genel Boolean.parseBoolean("CHARGING"/"SNA")false döndürerek
geçerli modu geçersiz kılabiliyordu. Yalnız şarja özel strict tri-state
okuyucu eklendi: boolean/0-1, bilinen metinler ve value wrapper; bilinmeyen
metin/null/SNA/diğer sayı null döndürür. Açık false/NotCharging/standby
önceliği korunur. Geçmiş yolculuk/istasyon alanları durum sayılmaz.
Kilit/READY gibi diğer genel boolean ayrıştırmaları değiştirilmedi.
CHARGING tanısına directState=true/false/UNKNOWN eklenir.

163 JVM testi, telefon Java tip kontrolü ve XML parse geçti. Kanıt dizini:
workspace obsolete/verification/charging-state-2026-11-18. APK üretilmedi;
son AF311paketi bu kaynak düzeltmesini içermez. Gerçek kullanıcı yanıtı
alınmadığından bu iki kusurdan hangisinin araçtaki olayı tetiklediği henüz
kanıtlanmadı; bulut veri zamanı/gerçek şarj karşılaştırması yine gereklidir.

16 Kasım 2026 güncel teslim: telefon debug1.1/kod2bu düzeltmeleri ve tanıyı
içerir (AF311);158test ve lint0hata ile paketlendi. Gerçek şarj yanıtı henüz
yakalanmadı; araç şarjdayken yenileyip Ayarlar → Uygulama günlüğünü paylaş
yoluyla CHARGING kayıtlarını almak gerekir. Aşağıdaki kaynak-only ifadeler
tarihsel aşamalardır; saat APK'sı bu tur derlenmedi.

## Güncel gösterim ve ölçümün doğrulanması — AF309–AF310

Kullanıcı isteğiyle önceki Bilgi yok gösterimi yerine güç bilinmiyorsa yalnız
`Şarj oluyor`, ölçüm varsa `Şarj oluyor- 80kW` yazılır. Ondalık değer yerel
biçimde en çok iki basamakla, gereksiz sıfırlar olmadan gösterilir. Gerçek0
ölçümü `Şarj oluyor- 0kW` olur. Güç bilinmiyorsa telefon batarya sayfasındaki
ayrı güç alt kartı da gizlenir. Telefon ana kartı ve saat aynı davranır.

Yeni APK kurulunca gerçek şarj sırasında uygulamadan veriyi yenileyin;
Ayarlar → Uygulama günlüğünü paylaş ile günlüğü alın. CHARGING kaydı örneği:
`CLOUD_RESPONSE charging=true supportedFields=1 invalidFields=0 selectedField=chargePowerKw reportedKw=80.0 usedForDisplay=true vehicleDataTime=...`
Bu, gerçek HTTP200yanıtında okunan alanı ve değeri kanıtlar; istasyon/araç
anlık ekranıyla ve araç veri zamanı ile ayrıca karşılaştırılmalıdır. Ağ sorgusu
yeni olsa da bulut ölçümü eski olabilir.

`supportedFields=0 reportedKw=UNAVAILABLE`: destekleneniki alanda ölçüm yok.
`invalidFields>0`: alan var ama null/SNA/negatif/geçersiz olabilir. Bu sonuç
başka adlı bir güç sensörünün hiç bulunmadığını kanıtlamaz; böyle bir durumda
farklı alanın schema/birimi ayrıca araştırılır. Yalnız izinli alan adı, sayısal
ölçüm ve zaman kaydedilir; ham yanıt/VIN/token/konum loglanmaz. Tanı her gerçek
başarılı TVA yanıtında manuel/ABRP/widget/JSON sorguları için de çalışır.

158JVM testi/telefon kaynak-XML ve değişenWear kaynak kontrolü geçti; bu
inceltme henüz APK'ya eklenmedi. Aşağıdaki ilk düzeltme notları tarihseldir.


Kullanıcı her şarjda 11kW bildiriyor. F02 parser, ölçüm yokken AC/generic
şarjda 11kW, DC şarjda120kW üretmekteydi. Ayrıca AC etiketi gerçek güçten
bağımsız `AC Şarj (11 kW)` idi; F03 eski/null-mode constructor'ları da aynı
etiketi üretiyordu. Bunlar ölçüm değildir ve kaldırıldı (AF307).

Yeni F127 ChargingInfo, mevcut desteklenen `chargePowerKw`/`chargingPower`
alanlarını, iç içe yapıları ve bu alan altındaki `value` sarmalayıcısını okur.
Genel `power` alanı motor/12V değerini yakalayabileceği için okunmaz;
myTrip/currentTrip/lastChargeTrip/lastTripInfo/chargingStation altı taranmaz.
Sabit AC/DC güç veya kalan süre/SoC farkından tahmin üretilmez. Desteklenen
iki alan önceki sözleşmedeki gibi kW kabul edilir; W→kW birim dönüşümü veya
bilinmeyen yeni sensör alanları canlı kanıt olmadan eklenmedi. Gerçek araçta
hangi alanın yayımlandığı bu tur henüz yakalanmadı.

Eksik/negatif/NaN/Infinity/SNA güç dahili -1 ile bilinmeyendir. Gerçek0kW
ölçümü korunur. Şarj kapalıysa güç0olur. Tür metni `AC Şarj`, `DC Şarj`
veya `Şarj Oluyor` olur; marka veya güç içermez. Negatif/INACTIVE/STOPPED/
COMPLETE durumları şarj sayılmaz; açık `isCharging=false` önceliklidir.
Telefon ana kartı/batarya sayfası ve saat ekranı bilinmeyen güç için
`Bilgi yok`/`Güç bilgisi yok` gösterir. F03, F06/F07 saklama ve saat modeli/
Data Layer -1 değerini0'a çevirmeden taşır; JSON'a geçersiz NaN yazılmaz.

Eski önbellekte gerçek11ile varsayılan11ayırt edilemez; eski kayıtlar topluca
silinmez veya gerçek ölçüm sanılan11rastgele değiştirilmez. Sonraki başarılı
araç sorgusu yeni kuralla kaydı günceller. Eski saat APK'sı bilinmeyeni0'a
kısabilir; saat kaynak düzeltmesi ilerideki saat paketlemesine dahil edilmelidir.

Doğrulama: tüm telefon kaynaklarında javac tip kontrolü ve156JVM testi
(9yeni regresyon) geçti. XML parse ve KB validator geçti. Saat UI/model/
saklama/listener kaynakları SDK36ile, yalnız Tile update çağrısı için geçici
imza stub'ı kullanılarak tip kontrolünden geçti; tam Wear/Tile kontrolü değildir.
Tam Wear kaynak kontrolünde mevcut cache'de Tiles/ProtoLayout bağımlılıkları
bulunmadı. Android kaynak/DEX/APK derlemesi, kurulum, canlı AC/DC ölçümü yok.
Mevcut1.1/kod2teslim APK'sı bu kaynak değişikliğini henüz içermez (AF308).

Manuel kabul: başarılı yenileme sonrası AC farklı güçlerde/DC şarjda araç veya
istasyonun anlık değeriyle karşılaştırın; ölçüm yayımlanmıyorsa sabit11/120
yerine bilgi yok beklenir. İstasyon nominal gücü gerçek çekilen güç değildir.
