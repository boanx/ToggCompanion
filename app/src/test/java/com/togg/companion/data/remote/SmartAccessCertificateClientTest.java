package com.togg.companion.data.remote;

import com.togg.companion.core.SmartAccessKeyStore;
import org.json.JSONObject;
import org.junit.Test;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import static org.junit.Assert.*;

public class SmartAccessCertificateClientTest {
    private static final String VIN = "NL1CFB0P800100522";
    private byte[] publicKey() throws Exception {
        KeyPairGenerator keys = KeyPairGenerator.getInstance("EC");
        keys.initialize(new ECGenParameterSpec("secp256r1"));
        return SmartAccessKeyStore.encodePublicKey((ECPublicKey) keys.generateKeyPair().getPublic());
    }
    @Test public void getUsesVerifiedHostAndHexKeyVinOwnerQuery() throws Exception {
        byte[] key = publicKey();
        String hex = SmartAccessCertificateClient.publicKeyHex(key);
        assertEquals(130, hex.length()); assertTrue(hex.startsWith("04"));
        String url = SmartAccessCertificateClient.buildFetchUrl(key, VIN.toLowerCase(), true);
        assertEquals("https://tva-api.togg.cloud/smart-access/api/v1/Certificates/GetCertificate?phonePublicKey="
                + hex + "&vin=" + VIN + "&isOwner=true", url);
        assertTrue(SmartAccessCertificateClient.buildFetchUrl(key, VIN, false).endsWith("isOwner=false"));
        assertFalse(url.contains("lastCheck"));
    }
    @Test public void createPlacesPublicKeyInQueryAndSignedShortCertificateInBody() throws Exception {
        byte[] key = publicKey(), shortCertificate = new byte[183]; shortCertificate[0] = 1;
        assertEquals(SmartAccessCertificateClient.BASE_URL + "/CreateCertificate?phonePublicKey="
                + SmartAccessCertificateClient.publicKeyHex(key), SmartAccessCertificateClient.buildCreateUrl(key));
        JSONObject body = SmartAccessCertificateClient.buildCreateBody(VIN, shortCertificate);
        assertEquals(3, body.length()); assertEquals(VIN, body.getString("vin"));
        assertTrue(body.getBoolean("isOwnerCertificate"));
        assertArrayEquals(shortCertificate, Base64.getDecoder().decode(body.getString("shortCertificateBase64")));
        assertFalse(body.has("phonePublicKey")); assertFalse(body.has("token"));
    }
    @Test public void accountUuidComesFromAuthenticatedAccessTokenSubject() throws Exception {
        String uuid = "00112233-4455-6677-8899-aabbccddeeff";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new JSONObject().put("sub", uuid).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(uuid, SmartAccessCertificateClient.accountTruId("header." + payload + ".signature"));
        try { SmartAccessCertificateClient.accountTruId("not-a-jwt"); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void malformedProvisioningInputsAreRejected() throws Exception {
        try { SmartAccessCertificateClient.buildCreateBody(VIN, new byte[182]); fail(); }
        catch (IllegalArgumentException expected) { }
        try { SmartAccessCertificateClient.buildFetchUrl(publicKey(), "demo", true); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void phoneRelationUsesAccountPublicKeyAndRealPlatformMetadata() throws Exception {
        byte[] key = publicKey();
        assertEquals(SmartAccessCertificateClient.API_URL + "/UserPhoneRelation/CheckUserPhoneRelation?publicKey="
                + SmartAccessCertificateClient.publicKeyHex(key), SmartAccessCertificateClient.buildPhoneRelationUrl(key));
        JSONObject body = SmartAccessCertificateClient.buildPhoneRelationBody("tr");
        assertEquals(3, body.length());
        assertEquals("android", body.getJSONArray("Platform").getString(0));
        assertEquals("tr", body.getJSONArray("Language").getString(0));
        assertEquals("", body.getJSONArray("PushNotificationToken").getString(0));
        assertFalse(body.has("vin")); assertFalse(body.has("token"));
    }
}
