# Sürüş modu gösterimi — 16 Kasım 2026 kaynak düzeltmesi

16 Kasım 2026: bu kaynak düzeltmesi kilometre JSON paylaşımıyla birlikte
telefon debug1.1/kod2 paketine dahil edildi (AF306). Fiziksel Eco/Konfor
karşılaştırması henüz doğrulanmadı.

Kullanıcı araçta Eco seçiliyken Companion'da Konfor göründüğünü bildirdi. Kaynakta parser eksik sürüş modu için Comfort atıyordu; VehicleTelemetry'nin null/eksik alan kurucuları ve SQLite eksik kolon okuması da Comfort üretiyordu. MainActivity boş modu ayrıca Konfor adlandırıyordu. Bu bilinen yanlış varsayımlar kaldırıldı. Kullanıcının canlı yanıtı görülmediğinden özel vakasının kesin nedeni fallback veya eski bulut verisi olarak ayrıştırılmış değildir.

DriveMode ortak sınıfı tanınan metinsel modları kanonikleştirir: Eco/Economy,Comfort/Konfor,Sport/Spor,Individual/Bireysel;DRIVEMODE_/DRIVE_MODE_ ve enum namespace yazımları desteklenir. Eksik,SNA,unknown veya doğrulanmamış sayısal enum başka moda çevrilmez; Bilgi yok gösterilir. Kısmi metin eşleştirmesi kaldırılmıştır; örneğinDRIVEMODE_ECO_NOT_AVAILABLE artıkEco sanılmaz. Demo snapshot açık Comfort örneği korunur; canlı veri yokken gerçek Comfort üretilmez.

Parser orijinal nested driveMode.content.driveModeState.driveMode yolunu ve metinsel enum alan adlarını okur. Orijinal Trumore çıkarılmış strings.txt/libapp.so.strings.tsv içinde DRIVEMODE_COMFORT'lu iç içe JSON örneği bulundu; bu şema dayanağıdır, güncel araç modu veya sayısal enum eşlemesi kanıtı değildir. Sayısal1=Eco gibi tahmin yapılmadı.

Sürüş kartında Son alınan araç verisindeki mod açıklaması vardır; ana başlıktaki araç yayın zamanı güncellik bağlamını gösterir. Canlı sunucu Comfort döndürüyorsa araçta o anda Eco seçilmiş olması nedeniyle bu veri Eco'ya zorlanmaz. Yeni başarılı sorgu eski cache'i günceller; önceki varsayımla kaydedilmişComfort ile gerçek eskiComfort kaydını geçmişten ayırmak mümkün değildir. TRACE yerine özel uygulama günlüğü TELEMETRY DRIVE_MODE,source=CLOUD/CACHE_OR_DEMO,vehicleDataTime kaydını tutar; yeni canlı veriyle ayrım yapılabilir. Mod değiştirme komutu veya doğrudan BLE/CAN okuma eklenmedi.

Java kaynak tip kontrolü,XMLparse ve141JVM testi geçti.5yeni testnestedEco,eksik/SNA/numeric bilinmiyor,enumlabelalias ve yanlışsubstring/namespacestore davranışını kapsar. Android build/APK/kurulum/canlı araç modu doğrulaması yoktur; teslim1.1/kod2önceki pakettir.
