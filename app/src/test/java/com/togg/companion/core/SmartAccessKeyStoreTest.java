package com.togg.companion.core;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SmartAccessKeyStoreTest {
    @Test public void rawP256SignatureVerifiesAndRejectsChangedMessageOrSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] message = "Smart Access fixture".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate()); signer.update(message);
        byte[] der = signer.sign();
        byte[] raw = SmartAccessKeyStore.derToRaw(der);
        assertEquals(64, raw.length);
        assertArrayEquals(der, SmartAccessKeyStore.rawToDer(raw));
        byte[] key = SmartAccessKeyStore.encodePublicKey((ECPublicKey) pair.getPublic());
        assertEquals(65, key.length);
        assertTrue(SmartAccessKeyStore.verify(message, raw, key));
        assertTrue(SmartAccessKeyStore.verify(message, raw, Arrays.copyOfRange(key, 1, 65)));
        message[0] ^= 1;
        assertFalse(SmartAccessKeyStore.verify(message, raw, key));
        assertFalse(SmartAccessKeyStore.verify(message, new byte[63], key));
    }

    @Test public void scalarPaddingRoundTripAndMalformedDerAreHandled() throws Exception {
        byte[] raw = new byte[64]; raw[0] = (byte) 0x80; raw[63] = 1;
        assertArrayEquals(raw, SmartAccessKeyStore.derToRaw(SmartAccessKeyStore.rawToDer(raw)));
        try { SmartAccessKeyStore.derToRaw(new byte[]{0x30, 6, 2, 1, (byte) 0xff, 2, 1, 1}); fail("Negative DER scalar"); }
        catch (java.security.GeneralSecurityException expected) { }
        try { SmartAccessKeyStore.rawToDer(new byte[65]); fail("Wrong signature length"); }
        catch (java.security.GeneralSecurityException expected) { }
        try { SmartAccessKeyStore.normalizePublicKey(new byte[65]); fail("Missing uncompressed key marker"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void identityAndVinNeverAcceptDemoOrArbitraryIdentifiers() {
        assertEquals("00112233-4455-6677-8899-aabbccddeeff", SmartAccessKeyStore.normalizeTruId("00112233-4455-6677-8899-AABBCCDDEEFF"));
        assertEquals("NL1CFB0P800100522", SmartAccessKeyStore.normalizeVin("nl1cfb0p800100522"));
        try { SmartAccessKeyStore.normalizeVin("T10F-DEV-001"); fail("Demo VIN"); } catch (IllegalArgumentException expected) { }
        try { SmartAccessKeyStore.normalizeTruId("SE-123456"); fail("Invitation is not account UUID"); } catch (IllegalArgumentException expected) { }
    }
}
