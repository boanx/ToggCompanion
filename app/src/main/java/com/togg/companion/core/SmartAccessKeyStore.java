package com.togg.companion.core;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** App-owned P-256 keys. Trumore's private keys belong to its Android UID. */
public final class SmartAccessKeyStore {
    private static final Object KEY_CREATION_LOCK = new Object();
    public SmartAccessKeyStore(Context context) {
        if (context == null) throw new IllegalArgumentException("Uygulama bağlamı gerekli.");
    }

    public synchronized void ensureKey(String truId, String vin) throws GeneralSecurityException {
        String alias = alias(truId, vin);
        synchronized (KEY_CREATION_LOCK) {
        try {
            KeyStore store = store();
            if (store.containsAlias(alias)) return;
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .build());
            generator.generateKeyPair();
        } catch (GeneralSecurityException error) { throw error; }
        catch (Exception error) { throw new GeneralSecurityException("Smart Access anahtarı oluşturulamadı.", error); }
        }
    }

    public synchronized byte[] getPublicKey(String truId, String vin) throws GeneralSecurityException {
        try {
            java.security.cert.Certificate certificate = store().getCertificate(alias(truId, vin));
            if (certificate == null || !(certificate.getPublicKey() instanceof ECPublicKey)) {
                throw new GeneralSecurityException("Bu kullanıcı ve araç için Smart Access anahtarı yok.");
            }
            return encodePublicKey((ECPublicKey) certificate.getPublicKey());
        } catch (GeneralSecurityException error) { throw error; }
        catch (Exception error) { throw new GeneralSecurityException("Smart Access açık anahtarı okunamadı.", error); }
    }

    public synchronized byte[] sign(String truId, String vin, byte[] data) throws GeneralSecurityException {
        if (data == null || data.length == 0 || data.length > 4096) throw new GeneralSecurityException("İmzalanacak mesaj geçersiz.");
        try {
            KeyStore.Entry entry = store().getEntry(alias(truId, vin), null);
            if (!(entry instanceof KeyStore.PrivateKeyEntry)) throw new GeneralSecurityException("Smart Access özel anahtarı yok.");
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(((KeyStore.PrivateKeyEntry) entry).getPrivateKey());
            signer.update(data);
            return derToRaw(signer.sign());
        } catch (GeneralSecurityException error) { throw error; }
        catch (Exception error) { throw new GeneralSecurityException("Smart Access mesajı imzalanamadı.", error); }
    }

    public static boolean verify(byte[] data, byte[] signature, byte[] publicKey) {
        try {
            if (data == null || signature == null || signature.length != 64) return false;
            byte[] normalized = normalizePublicKey(publicKey);
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(normalized, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(normalized, 33, 65)));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec)));
            verifier.update(data);
            return verifier.verify(rawToDer(signature));
        } catch (Exception error) { return false; }
    }

    public static byte[] normalizePublicKey(byte[] key) {
        if (key == null) throw new IllegalArgumentException("Açık anahtar yok.");
        if (key.length == 65 && key[0] == 4) return key.clone();
        if (key.length == 64) {
            byte[] result = new byte[65]; result[0] = 4;
            System.arraycopy(key, 0, result, 1, 64); return result;
        }
        throw new IllegalArgumentException("P-256 açık anahtar biçimi geçersiz.");
    }

    public static byte[] encodePublicKey(ECPublicKey key) {
        if (key == null || key.getParams().getCurve().getField().getFieldSize() != 256) throw new IllegalArgumentException("P-256 anahtarı gerekli.");
        byte[] result = new byte[65]; result[0] = 4;
        putScalar(key.getW().getAffineX(), result, 1);
        putScalar(key.getW().getAffineY(), result, 33);
        return result;
    }

    public static byte[] derToRaw(byte[] der) throws GeneralSecurityException {
        if (der == null || der.length < 8 || der.length > 72 || der[0] != 0x30 || (der[1] & 255) != der.length - 2) throw new GeneralSecurityException("ECDSA imza biçimi geçersiz.");
        byte[] result = new byte[64]; int position = 2;
        for (int component = 0; component < 2; component++) {
            if (position + 2 > der.length || der[position++] != 2) throw new GeneralSecurityException("ECDSA imza bileşeni geçersiz.");
            int count = der[position++] & 255;
            if (count == 0 || count > 33 || position + count > der.length || (der[position] & 128) != 0) throw new GeneralSecurityException("ECDSA imza bileşeni geçersiz.");
            if (count > 1 && der[position] == 0 && (der[position + 1] & 128) == 0) throw new GeneralSecurityException("ECDSA imzası standart DER biçiminde değil.");
            if (count == 33) { if (der[position] != 0) throw new GeneralSecurityException("ECDSA bileşeni çok uzun."); position++; count--; }
            System.arraycopy(der, position, result, component * 32 + 32 - count, count); position += count;
        }
        if (position != der.length) throw new GeneralSecurityException("ECDSA imzasında fazla veri var.");
        return result;
    }

    public static byte[] rawToDer(byte[] raw) throws GeneralSecurityException {
        if (raw == null || raw.length != 64) throw new GeneralSecurityException("ECDSA imzası 64 bayt olmalı.");
        byte[] r = new BigInteger(1, Arrays.copyOfRange(raw, 0, 32)).toByteArray();
        byte[] s = new BigInteger(1, Arrays.copyOfRange(raw, 32, 64)).toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x30); output.write(4 + r.length + s.length);
        output.write(2); output.write(r.length); output.write(r, 0, r.length);
        output.write(2); output.write(s.length); output.write(s, 0, s.length);
        return output.toByteArray();
    }

    public static String normalizeTruId(String truId) {
        if (truId == null || !truId.trim().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Trumore kullanıcı kimliği (UUID) gerekli.");
        return UUID.fromString(truId.trim()).toString();
    }

    public static String normalizeVin(String vin) {
        String result = vin == null ? "" : vin.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-HJ-NPR-Z0-9]{17}")) throw new IllegalArgumentException("Gerçek aracın 17 haneli VIN numarası gerekli.");
        return result;
    }

    private static String alias(String truId, String vin) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest((normalizeTruId(truId) + ":" + normalizeVin(vin)).getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder("togg_companion_smart_access_");
        for (byte value : hash) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static KeyStore store() throws Exception {
        KeyStore result = KeyStore.getInstance("AndroidKeyStore"); result.load(null); return result;
    }

    private static void putScalar(BigInteger value, byte[] destination, int offset) {
        byte[] bytes = value.toByteArray();
        if (value.signum() < 0 || value.bitLength() > 256) throw new IllegalArgumentException("P-256 koordinatı geçersiz.");
        int count = Math.min(32, bytes.length); System.arraycopy(bytes, bytes.length - count, destination, offset + 32 - count, count);
    }
}
