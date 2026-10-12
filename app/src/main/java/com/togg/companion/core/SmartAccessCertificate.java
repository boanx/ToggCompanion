package com.togg.companion.core;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;

/** Version 1 user certificate layout recovered from Trumore 2.1.25 AOT. */
public final class SmartAccessCertificate {
    public static final int VERSION_1_LENGTH = 247;
    public final String vin;
    public final String truId;
    public final long validFromMillis;
    public final long validUntilMillis;
    public final long timestampMillis;
    public final int version;
    public final int userMode;
    public final boolean isOwner;
    public final boolean canDrive;
    public final boolean canDoorOpen;
    public final boolean canTrunkOpen;
    private final byte[] rawCertificate;
    private final byte[] phonePublicKey;

    private SmartAccessCertificate(byte[] bytes, long timestamp) {
        if (bytes == null || bytes.length != VERSION_1_LENGTH || bytes[0] != 1) {
            throw new IllegalArgumentException("Smart Access sertifikası desteklenen sürüm 1 biçiminde değil.");
        }
        rawCertificate = bytes.clone();
        version = 1;
        ByteBuffer identity = ByteBuffer.wrap(bytes, 1, 16);
        truId = new UUID(identity.getLong(), identity.getLong()).toString();
        vin = SmartAccessKeyStore.normalizeVin(new String(bytes, 17, 17, StandardCharsets.US_ASCII));
        validFromMillis = decodeDate(bytes, 34);
        validUntilMillis = decodeDate(bytes, 40);
        if (validUntilMillis <= validFromMillis) throw new IllegalArgumentException("Sertifika geçerlilik aralığı hatalı.");
        isOwner = readBoolean(bytes[46]);
        userMode = bytes[47] & 255;
        canDoorOpen = readBoolean(bytes[48]);
        canTrunkOpen = readBoolean(bytes[49]);
        canDrive = readBoolean(bytes[50]);
        phonePublicKey = SmartAccessKeyStore.normalizePublicKey(Arrays.copyOfRange(bytes, 54, 119));
        timestampMillis = Math.max(0, timestamp);
    }

    public static SmartAccessCertificate parseFromServer(JSONObject response, String expectedVin,
            String expectedTruId, byte[] localPhonePublicKey) {
        if (response == null) throw new IllegalArgumentException("Sertifika yanıtı boş.");
        JSONObject body = response.optJSONObject("data");
        if (body == null) body = response;
        String vin = SmartAccessKeyStore.normalizeVin(body.optString("vin", ""));
        if (!vin.equals(SmartAccessKeyStore.normalizeVin(expectedVin))) throw new IllegalArgumentException("Sertifika VIN numarası araçla eşleşmiyor.");
        byte[] raw;
        try { raw = Base64.getDecoder().decode(body.getString("certificateBase64")); }
        catch (Exception error) { throw new IllegalArgumentException("Sertifika base64 verisi geçersiz."); }
        SmartAccessCertificate certificate = parse(raw, body.optLong("timestamp", 0));
        if (!certificate.isValidFor(expectedTruId, expectedVin, localPhonePublicKey)) {
            throw new IllegalArgumentException("Sertifika kullanıcı, araç, yerel açık anahtar veya geçerlilik tarihiyle eşleşmiyor.");
        }
        return certificate;
    }

    /** Parsing alone does not establish server origin; callers must use the authenticated client. */
    public static SmartAccessCertificate parse(byte[] bytes, long timestamp) {
        return new SmartAccessCertificate(bytes, timestamp);
    }

    public boolean isValidAt(long nowMillis) {
        return nowMillis >= validFromMillis && nowMillis < validUntilMillis;
    }

    public boolean isValidFor(String expectedTruId, String expectedVin, byte[] localPhonePublicKey) {
        try {
            return truId.equals(SmartAccessKeyStore.normalizeTruId(expectedTruId))
                    && vin.equals(SmartAccessKeyStore.normalizeVin(expectedVin))
                    && MessageDigest.isEqual(phonePublicKey, SmartAccessKeyStore.normalizePublicKey(localPhonePublicKey))
                    && isValidAt(System.currentTimeMillis());
        } catch (IllegalArgumentException error) { return false; }
    }

    public byte[] getRawCertificate() { return rawCertificate.clone(); }
    public byte[] getPhonePublicKey() { return phonePublicKey.clone(); }
    /** The SAM key is obtained and pinned by the BLE session; it is not this certificate's signature. */
    public byte[] getCarPublicKey() { return new byte[0]; }
    public byte[] getOwnerSignature() { return Arrays.copyOfRange(rawCertificate, 119, 183); }
    public byte[] getIssuerSignature() { return Arrays.copyOfRange(rawCertificate, 183, 247); }

    /** Original Android owner request defaults; authority is granted only by the server/SAM. */
    public static byte[] buildOwnerSigningData(String truId, String vin, byte[] phonePublicKey, long nowMillis) {
        String identity = SmartAccessKeyStore.normalizeTruId(truId);
        String vehicle = SmartAccessKeyStore.normalizeVin(vin);
        byte[] key = SmartAccessKeyStore.normalizePublicKey(phonePublicKey);
        byte[] result = new byte[119]; result[0] = 1;
        UUID uuid = UUID.fromString(identity);
        ByteBuffer.wrap(result, 1, 16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        System.arraycopy(vehicle.getBytes(StandardCharsets.US_ASCII), 0, result, 17, 17);
        encodeDate(result, 34, nowMillis);
        encodeDate(result, 40, Math.addExact(nowMillis, 365L * 24 * 60 * 60 * 1000));
        result[46] = 1; result[47] = 1;
        result[48] = 1; result[49] = 1; result[50] = 1;
        System.arraycopy(key, 0, result, 54, 65);
        return result;
    }

    public static byte[] appendOwnerSignature(byte[] signingData, byte[] signature) {
        if (signingData == null || signingData.length != 119 || signingData[0] != 1
                || signature == null || signature.length != 64
                || !SmartAccessKeyStore.verify(signingData, signature, Arrays.copyOfRange(signingData, 54, 119))) {
            throw new IllegalArgumentException("Kısa sertifika imzası yerel açık anahtarla doğrulanamadı.");
        }
        byte[] result = Arrays.copyOf(signingData, 183);
        System.arraycopy(signature, 0, result, 119, 64);
        return result;
    }

    public JSONObject toServerJson() {
        try {
            return new JSONObject().put("vin", vin)
                    .put("certificateBase64", Base64.getEncoder().encodeToString(rawCertificate))
                    .put("timestamp", timestampMillis);
        } catch (Exception error) { throw new IllegalStateException("Sertifika kaydı oluşturulamadı.", error); }
    }

    private static boolean readBoolean(byte value) {
        if (value != 0 && value != 1) throw new IllegalArgumentException("Sertifika yetki alanı geçersiz.");
        return value == 1;
    }

    // Original FJc: hour, minute, month, day, uint16 year little endian; DateTime.utc.
    private static long decodeDate(byte[] bytes, int offset) {
        int year = (bytes[offset + 4] & 255) | ((bytes[offset + 5] & 255) << 8);
        if (year < 1970 || year > 9999) throw new IllegalArgumentException("Sertifika tarihi geçersiz.");
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear(); calendar.setLenient(false);
        calendar.set(year, (bytes[offset + 2] & 255) - 1, bytes[offset + 3] & 255,
                bytes[offset] & 255, bytes[offset + 1] & 255, 0);
        try { return calendar.getTimeInMillis(); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Sertifika tarihi geçersiz."); }
    }

    private static void encodeDate(byte[] bytes, int offset, long millis) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(millis);
        int year = calendar.get(Calendar.YEAR);
        if (year < 1970 || year > 9999) throw new IllegalArgumentException("Sertifika tarihi geçersiz.");
        bytes[offset] = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        bytes[offset + 1] = (byte) calendar.get(Calendar.MINUTE);
        bytes[offset + 2] = (byte) (calendar.get(Calendar.MONTH) + 1);
        bytes[offset + 3] = (byte) calendar.get(Calendar.DAY_OF_MONTH);
        bytes[offset + 4] = (byte) year; bytes[offset + 5] = (byte) (year >> 8);
    }
}
