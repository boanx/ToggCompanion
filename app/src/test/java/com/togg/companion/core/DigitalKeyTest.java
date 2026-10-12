package com.togg.companion.core;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class DigitalKeyTest {

    @Test
    public void testTrumoreDeepLinkParsing() {
        String deepLink = "trumore-superapp://custom/redirection?path=/keysharingInvitation&seId=SE-8F4219&token=TOKEN_SECURE_123&vin=NL1CFB0P800100522&plate=34%20TOGG%2010";
        DigitalKey key = DigitalKeyManager.parseFromInput(deepLink);

        assertNotNull("Key should not be null from deep link", key);
        assertEquals("SE-8F4219", key.seId);
        assertEquals("TOKEN_SECURE_123", key.keyToken);
        assertEquals("NL1CFB0P800100522", key.vehicleVin);
        assertEquals("34 TOGG 10", key.vehiclePlate);
        assertFalse("Invitation parsing cannot provision Smart Access", key.isValid());
        assertFalse(key.canLock);
        assertFalse(key.canStartDrive);
        assertFalse(key.isExpired());
    }

    @Test
    public void testHttpsInvitationUrlParsing() throws Exception {
        String webUrl = "https://trumore.app/keysharingInvitation?seId=SE-991122&token=TOKEN_WEB_777";
        DigitalKey key = DigitalKeyManager.parseFromInput(webUrl);

        assertNotNull(key);
        assertEquals("SE-991122", key.seId);
        assertEquals("TOKEN_WEB_777", key.keyToken);
        assertFalse(key.isValid());
        assertEquals("", key.vehicleVin);
        assertEquals("", key.vehiclePlate);
        assertEquals("Araç bilgisi bilinmiyor", key.getVehicleDisplay());

        String wrapped = "trumore-superapp://custom/redirection?target="
                + java.net.URLEncoder.encode(webUrl + "&vin=VIN%2B123&plate=06%20TEST", "UTF-8");
        DigitalKey redirected = DigitalKeyManager.parseFromUri(null, wrapped);
        assertNotNull(redirected);
        assertEquals(key.seId, redirected.seId);
        assertEquals("VIN+123", redirected.vehicleVin);
        assertEquals("06 TEST", redirected.vehiclePlate);
        for (int i = 0; i < 4; i++) {
            wrapped = "trumore-superapp://custom/redirection?target="
                    + java.net.URLEncoder.encode(wrapped, "UTF-8");
        }
        assertNull("Excessive nested redirections must be rejected", DigitalKeyManager.parseFromUri(null, wrapped));
    }

    @Test
    public void testSmsTextWithReferenceCode() {
        String smsText = "Togg T10F cihazımın dijital anahtarını sizinle paylaştım. Referans kodunuz: SE-B45901. Trumore veya Togg Companion ile eşleştirebilirsiniz.";
        DigitalKey key = DigitalKeyManager.parseFromInput(smsText);

        assertNotNull("Should detect SE- reference code in text", key);
        assertEquals("SE-B45901", key.seId);
        assertFalse(key.isValid());
        assertEquals("", key.keyToken);
        assertEquals("", key.vehicleVin);
        assertEquals("", key.vehiclePlate);
        assertTrue(key.recipientName.contains("SE-B45901"));
    }

    @Test public void arbitraryTextDoesNotBecomeAnAuthorizedKey() {
        assertNull(DigitalKeyManager.parseFromInput("abcdef123456"));
    }

    @Test public void importedJsonCannotGrantItselfPermissions() throws Exception {
        JSONObject invitation = new JSONObject().put("seId", "SE-TEST").put("keyToken", "test-token")
                .put("isActive", true).put("canLock", true).put("canStartDrive", true);
        DigitalKey key = DigitalKeyManager.parseFromInput(invitation.toString());
        assertNotNull(key);
        assertFalse(key.isValid());
        assertFalse(key.canLock);
        assertFalse(key.canStartDrive);
        assertEquals("", key.vehicleVin);
        assertEquals("", key.vehiclePlate);
        DigitalKey empty = DigitalKey.fromJson(new JSONObject());
        assertFalse(empty.isValid());
        assertFalse(empty.canUnlock);
        assertEquals(0L, empty.validUntilMillis);
        assertEquals("", empty.vehicleVin);
        assertEquals("", empty.vehiclePlate);
        assertEquals("Araç bilgisi bilinmiyor", empty.getVehicleDisplay());
        DigitalKey missingMetadata = new DigitalKey("KEY-UNKNOWN", "SE-TEST", null, null, null,
                0, 0, false, false, false, false, false, null, false, 0);
        assertEquals("", missingMetadata.vehicleVin);
        assertEquals("", missingMetadata.vehiclePlate);
    }

    @Test
    public void testJsonRoundTrip() {
        DigitalKey original = new DigitalKey(
                "KEY-CUSTOM-1",
                "SE-990011",
                "NL1TESTVIN",
                "06 TOGG 06",
                "Test Kullanıcısı",
                System.currentTimeMillis(),
                System.currentTimeMillis() + 86400000L,
                true,
                true,
                true,
                false,
                true,
                "SECRET_JWT_KEY_AUTH",
                true,
                System.currentTimeMillis()
        );

        JSONObject json = original.toJson();
        assertNotNull(json);

        DigitalKey restored = DigitalKey.fromJson(json);
        assertNotNull(restored);
        assertEquals(original.keyId, restored.keyId);
        assertEquals(original.seId, restored.seId);
        assertEquals(original.vehicleVin, restored.vehicleVin);
        assertEquals(original.vehiclePlate, restored.vehiclePlate);
        assertEquals(original.recipientName, restored.recipientName);
        assertEquals(original.canLock, restored.canLock);
        assertEquals(original.canUnlock, restored.canUnlock);
        assertEquals(original.canStartDrive, restored.canStartDrive);
        assertEquals(original.canClimate, restored.canClimate);
        assertEquals(original.canTrunk, restored.canTrunk);
        assertEquals(original.keyToken, restored.keyToken);
        assertEquals(original.isActive, restored.isActive);
        assertEquals("06 TOGG 06 (NL1TESTVIN)", restored.getVehicleDisplay());
        DigitalKey sample = DigitalKey.createSampleKey();
        assertEquals("T10F-DEV-001", sample.vehicleVin);
        assertEquals("34 TOGG 10", sample.vehiclePlate);
    }

    @Test
    public void testKeyExpiration() {
        // Expired key: validUntil in the past
        long pastTime = System.currentTimeMillis() - 100000L;
        DigitalKey expired = new DigitalKey(
                "KEY-EXPIRED", "SE-EXP01", "VIN", "PLATE", "User",
                pastTime - 100000L, pastTime,
                true, true, true, true, true, "TOKEN", true, pastTime - 100000L
        );

        assertTrue("Key should be expired", expired.isExpired());
        assertFalse("Expired key should not be valid", expired.isValid());
        assertEquals("Süresi Doldu", expired.getRemainingTimeDisplay());
    }

    @Test
    public void testPermissionsSummary() {
        DigitalKey key = new DigitalKey(
                "K1", "SE-1", "V", "P", "U",
                0, 0,
                true, true, false, true, true, "TOK", true, 0
        );
        String summary = key.getPermissionsSummary();
        assertTrue(summary.contains("Kapı Kilidi"));
        assertTrue(summary.contains("Bagaj"));
        assertTrue(summary.contains("Klima"));
        assertFalse(summary.contains("Sürüş"));
    }
}
