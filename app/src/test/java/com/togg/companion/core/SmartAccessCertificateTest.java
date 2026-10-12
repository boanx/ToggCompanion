package com.togg.companion.core;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;
import static org.junit.Assert.*;

public class SmartAccessCertificateTest {
    private static final String USER = "00112233-4455-6677-8899-aabbccddeeff";
    private static final String VIN = "NL1CFB0P800100522";

    @Test public void originalVersion1OffsetsReadIdentityDatesAndPermissions() {
        SmartAccessCertificate certificate = SmartAccessCertificate.parse(fixture(), 1234);
        assertEquals(USER, certificate.truId); assertEquals(VIN, certificate.vin);
        assertEquals(1, certificate.version); assertTrue(certificate.isOwner);
        assertTrue(certificate.canDrive); assertTrue(certificate.canDoorOpen); assertFalse(certificate.canTrunkOpen);
        Calendar start = new GregorianCalendar(TimeZone.getTimeZone("UTC")); start.clear(); start.set(2020, 0, 2, 3, 4, 0);
        assertEquals(start.getTimeInMillis(), certificate.validFromMillis);
        assertEquals(1234, certificate.timestampMillis);
        assertTrue(certificate.isValidAt(certificate.validFromMillis));
        assertFalse(certificate.isValidAt(certificate.validUntilMillis));
        assertEquals(0, certificate.getCarPublicKey().length);
        byte[] copy = certificate.getRawCertificate(); copy[17] = 'X';
        assertEquals('N', certificate.getRawCertificate()[17]);
    }

    @Test public void serverBindingRejectsOtherVinUserKeyAndExpiredCertificate() throws Exception {
        byte[] bytes = fixture(); byte[] publicKey = java.util.Arrays.copyOfRange(bytes, 54, 119);
        JSONObject response = new JSONObject().put("vin", VIN).put("certificateBase64", Base64.getEncoder().encodeToString(bytes)).put("timestamp", "1234");
        SmartAccessCertificate certificate = SmartAccessCertificate.parseFromServer(response, VIN, USER, publicKey);
        assertTrue(certificate.isValidFor(USER, VIN, publicKey));
        assertFalse(certificate.isValidFor("ffeeddcc-bbaa-9988-7766-554433221100", VIN, publicKey));
        publicKey[1] ^= 1; assertFalse(certificate.isValidFor(USER, VIN, publicKey));
        try { SmartAccessCertificate.parseFromServer(response, "NL1CFB0P800100523", USER, publicKey); fail("Wrong VIN"); } catch (IllegalArgumentException expected) { }
        bytes[44] = (byte) (2021 & 255); bytes[45] = (byte) (2021 >> 8);
        response.put("certificateBase64", Base64.getEncoder().encodeToString(bytes));
        try { SmartAccessCertificate.parseFromServer(response, VIN, USER, certificate.getPhonePublicKey()); fail("Expired certificate"); } catch (IllegalArgumentException expected) { }
    }

    @Test public void malformedOrUnmappedSchemasNeverGrantAccess() {
        byte[] bytes = fixture(); bytes[0] = 0;
        try { SmartAccessCertificate.parse(bytes, 0); fail("Legacy schema has unverified permissions"); } catch (IllegalArgumentException expected) { }
        bytes = fixture(); bytes[36] = 13;
        try { SmartAccessCertificate.parse(bytes, 0); fail("Invalid month"); } catch (IllegalArgumentException expected) { }
        bytes = fixture(); bytes[48] = 2;
        try { SmartAccessCertificate.parse(bytes, 0); fail("Invalid permission boolean"); } catch (IllegalArgumentException expected) { }
        try { SmartAccessCertificate.parse(new byte[183], 0); fail("Short certificate is not a server certificate"); } catch (IllegalArgumentException expected) { }
    }

    @Test public void ownerRequestMatchesRecoveredLayoutAndRequiresMatchingPrivateKey() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.KeyPair pair = generator.generateKeyPair();
        byte[] key = SmartAccessKeyStore.encodePublicKey((java.security.interfaces.ECPublicKey) pair.getPublic());
        Calendar start = new GregorianCalendar(TimeZone.getTimeZone("UTC")); start.clear(); start.set(2030, 9, 12, 10, 20, 0);
        byte[] data = SmartAccessCertificate.buildOwnerSigningData(USER, VIN, key, start.getTimeInMillis());
        assertEquals(119, data.length);
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate()); signer.update(data);
        byte[] signature = SmartAccessKeyStore.derToRaw(signer.sign());
        byte[] shortCertificate = SmartAccessCertificate.appendOwnerSignature(data, signature);
        assertEquals(183, shortCertificate.length);
        SmartAccessCertificate decoded = SmartAccessCertificate.parse(java.util.Arrays.copyOf(shortCertificate, 247), 0);
        assertEquals(start.getTimeInMillis(), decoded.validFromMillis);
        assertEquals(start.getTimeInMillis() + 365L * 24 * 3600 * 1000, decoded.validUntilMillis);
        assertTrue(decoded.canDrive); assertTrue(decoded.canDoorOpen); assertTrue(decoded.canTrunkOpen);
        assertEquals(1, decoded.userMode);
        signature[0] ^= 1;
        try { SmartAccessCertificate.appendOwnerSignature(data, signature); fail("Wrong key signature"); } catch (IllegalArgumentException expected) { }
    }

    private static byte[] fixture() {
        byte[] bytes = new byte[247]; bytes[0] = 1;
        UUID uuid = UUID.fromString(USER); ByteBuffer.wrap(bytes, 1, 16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        System.arraycopy(VIN.getBytes(StandardCharsets.US_ASCII), 0, bytes, 17, 17);
        date(bytes, 34, 2020, 1, 2, 3, 4); date(bytes, 40, 2099, 12, 31, 23, 59);
        bytes[46] = 1; bytes[47] = 1; bytes[48] = 1; bytes[49] = 0; bytes[50] = 1; bytes[54] = 4;
        return bytes;
    }

    private static void date(byte[] bytes, int offset, int year, int month, int day, int hour, int minute) {
        bytes[offset] = (byte) hour; bytes[offset + 1] = (byte) minute; bytes[offset + 2] = (byte) month; bytes[offset + 3] = (byte) day;
        bytes[offset + 4] = (byte) year; bytes[offset + 5] = (byte) (year >> 8);
    }
}
