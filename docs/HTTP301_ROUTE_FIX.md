# Rota aktarımı HTTP 301 adres düzeltmesi

12 Ekim 2026. Kaynak kod düzeltildi; kullanıcı talimatı nedeniyle APK derlenmedi.

## Neden

Önceki Companion sürümü `/j/api/PlannedJourneys` yolunu `superapp-api.channels.togg.cloud` ile birleştiriyordu. Yol ve JSON şeması orijinal AOT'den doğrulanmıştı; bu yolun API alan adı yanlış seçilmişti. Kullanıcının cihazında POST isteği HTTP 301 döndürdü.

Salt okunur, token ve gövde içermeyen HEAD gözlemleri:

| Adres | Sonuç |
| --- | --- |
| `https://superapp-api.channels.togg.cloud/j/api/PlannedJourneys` | 301 → `https://superapp-api.channels.togg.cloud/home` |
| Aynı yolun `/` ile biten biçimi | 301 → `/home` |
| Superapp alan adında 8443 portu | 301 → aynı portta `/home` |
| `https://navigation-api.togg.cloud/j/api/PlannedJourneys` | 405, `Allow: POST` |
| `https://navigation-bff.lbs.togg.cloud/j/api/PlannedJourneys` | 404 |

Bu sonuçlar, 301'i otomatik takip etmenin rota API'sine ulaşmak yerine web ana sayfasına gideceğini gösterir. Yeni adresin HEAD'e 405/POST dönmesi gerçek POST kabulünü veya araç teslimini doğrulamaz.

## Orijinal APK kaynakları

- Paketlenmiş `assets/flutter_assets/assets/environment/prod.env` içindeki public `navigationApiUrl` ve `NAVIGATION_API_URL`: `https://navigation-api.togg.cloud`.
- `NAVIGATION_BFF_URL` ayrı BFF adresidir; `SUPERAPP_URL` başka servis ve 8443 portunu belirtir. API anahtarı veya diğer secret değerler okunup raporlanmadı; yalnız public URL allowlist'i incelendi.
- Raw ARM64 object-pool xref: `pp+0x7ef30` (`NAVIGATION_API_URL`) yüklemesi `0xed8a30`, getter entry `0xed8a18`. Getter, API client yapılandırmasını başlatan `0x2098a00` içinden `0x2098b88` adresinde çağrılır. Bu bağımsız kaynak, NAVIGATION_API_URL'nin yalnız kullanılmayan bir dosya alanı olmadığını gösterir.
- Daha önce doğrulanan rota API entry `0x2256014`, `/j/api/PlannedJourneys`, POST, JSON ve Bearer Authorization kullanır. Yeni hostname seçimi public config, client initialization ve HEAD method/routing gözlemlerinin birlikte değerlendirilmesine dayanır.

Tekrar incelenebilir yerel kanıtlar: `work/trumore-analysis/navigation-host-xrefs.txt`, `navigation-host-callers.txt`, `navigation-host-getter-ed8a18.asm.resolved.txt`, `navigation-api-clients-2098a00.asm.resolved.txt`, `analysis/route-host/http-head-evidence.txt` (çalışma klasörüne göre).

## Değişiklik

- `TvaApiClient.PLANNED_JOURNEYS_ENDPOINT` artık `https://navigation-api.togg.cloud/j/api/PlannedJourneys`.
- `startingPoint`, `arrivalPoint`, `waypoints` JSON'u, POST, Bearer oturumu ve hesap/VIN kontrolleri korundu. VIN veya tahmin edilmiş header/API key eklenmedi.
- Otomatik yönlendirme kapalı kalır. `/home` yanıtı ayrıca açıklanır; web sayfası HTTP 200 döndürse bile bunu rota başarısı sayacak bir fallback eklenmedi.

## Durum

Mevcut `outputs/ToggCompanion-direct-route-debug.apk` eski adresi içerir ve SHA256 değeri değiştirilmedi: `82a37cdba2434e3f465ddcbd91392eb05a1e84e47db366cdaa59d77c22cc43b8`.

APK derlenmedi, paketlenmedi veya yeniden oluşturulmadı. Otomatik JVM testi eklenmedi/çalıştırılmadı. KB ve diff kontrolleri yapıldı. Yalnız kimliksiz HEAD istekleri gönderildi; tokenlı POST, rota veya araç komutu gönderilmedi.

Kullanıcı açıkça derleme istediğinde düzeltme APK'ye alınabilir. Sonraki uygulama içi denemede gerçek POST kabulü ve araç Gez ekranına teslim ayrıca doğrulanmalıdır.
