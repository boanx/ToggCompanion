package com.togg.companion.core;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/** A single serialized Smart Access exchange; transport writes never imply vehicle success. */
public final class SmartAccessSession {
    public interface Signer { byte[] sign(byte[] data) throws GeneralSecurityException; }
    public interface Transport { void send(byte[] frame); }
    public interface Listener {
        void onStage(String message);
        boolean onCarPublicKey(byte[] key);
        void onReady();
        void onStatus(SmartAccessProtocol.RawStatus status, long counter);
        void onCommandResult(boolean success, String message);
        void onError(String message);
    }

    private final SmartAccessCertificate certificate;
    private final Signer signer;
    private final Transport transport;
    private final Listener listener;
    private final SmartAccessProtocol.Reassembler incoming = new SmartAccessProtocol.Reassembler();
    private byte[] carKey, ownNonce, samNonce;
    private List<byte[]> outgoing;
    private int outgoingRequest, outgoingBlock;
    private int incomingRequest = SmartAccessProtocol.REQUEST_USER_CERTIFICATE;
    private long counter = -1;
    private volatile long statusAt;
    private boolean certificateAccepted, phoneProofAccepted, carProofAccepted;
    private volatile boolean ready, closed;
    private volatile SmartAccessProtocol.RawStatus status;
    private SmartAccessProtocol.CarCommand command;
    private boolean commandSent, commandAcknowledged, awaitingCarKey;

    public SmartAccessSession(SmartAccessCertificate certificate, byte[] pinnedCarKey,
                              Signer signer, Transport transport, Listener listener) {
        if (certificate == null || !certificate.isValidAt(System.currentTimeMillis()))
            throw new IllegalArgumentException("Geçerli araç sertifikası gerekli.");
        this.certificate = certificate;
        this.carKey = pinnedCarKey == null || pinnedCarKey.length == 0 ? new byte[0]
                : SmartAccessKeyStore.normalizePublicKey(pinnedCarKey);
        if (signer == null || transport == null || listener == null)
            throw new IllegalArgumentException("BLE oturum bağımlılıkları gerekli.");
        this.signer = signer; this.transport = transport; this.listener = listener;
    }

    public void start() {
        listener.onStage("Araç sertifika isteği bekleniyor. Araçtaki erişim cihazları ekranını açın.");
    }

    public boolean isReady() { return ready && !closed && certificate.isValidAt(System.currentTimeMillis()); }
    public SmartAccessProtocol.RawStatus getStatus() { return status; }
    public long getStatusAt() { return statusAt; }
    public void close() {
        closed = true; ready = false; outgoing = null; command = null; incoming.reset();
        if (ownNonce != null) Arrays.fill(ownNonce, (byte) 0);
        if (samNonce != null) Arrays.fill(samNonce, (byte) 0);
        ownNonce = samNonce = null;
    }

    public void receive(byte[] bytes) {
        if (closed) return;
        try {
            if (bytes == null || bytes.length < SmartAccessProtocol.HEADER_LENGTH)
                throw new IllegalArgumentException("Eksik BLE mesajı.");
            int id = bytes[0] & 255;
            if (id >= 0x10 && id <= 0x14) onRequest(SmartAccessProtocol.decodeRequest(bytes));
            else {
                // Unauthenticated status broadcasts cannot establish or abort a session.
                if (id == SmartAccessProtocol.SAM_APP_STATUS_MSG && !carProofAccepted) return;
                SmartAccessProtocol.DataFrame frame = SmartAccessProtocol.decodeDataFrame(bytes);
                if (frame.block == 1) {
                    if (incoming.receivedLength() != 0 && !incoming.isComplete())
                        throw new IllegalArgumentException("Tamamlanmamış BLE mesajıyla çakışan yeni mesaj.");
                    incoming.reset();
                }
                SmartAccessProtocol.Message message = incoming.accept(frame);
                // Only acknowledge the active exchange; status is independently signed.
                int ackId = frame.commandId == SmartAccessProtocol.SAM_APP_STATUS_MSG
                        ? SmartAccessProtocol.REQUEST_SAM_APP_STATUS : incomingRequest;
                byte[] acknowledgment = incoming.acknowledgment(ackId);
                Runnable followUp = message == null ? null : onMessage(message);
                if (!closed) transport.send(acknowledgment);
                // The original receiver ACKs validated data before sending its next request/data.
                if (!closed && followUp != null) followUp.run();
            }
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            fail("BLE mesajı veya imza doğrulanamadı: " + error.getMessage());
        }
    }

    private void onRequest(SmartAccessProtocol.RequestFrame frame) throws GeneralSecurityException {
        if (frame.state == SmartAccessProtocol.RequestState.PRE_PACK_RECEIVED) {
            if (outgoing == null || frame.commandId != outgoingRequest)
                throw new IllegalArgumentException("Beklenmeyen araç parça onayı.");
            if (frame.rawJuu != outgoingBlock + 1 || frame.rawJuu > outgoing.size()
                    || frame.rawIuu != sentPayloadLength())
                throw new IllegalArgumentException("Araç parça onayı eşleşmiyor.");
            transport.send(outgoing.get(outgoingBlock++));
            return;
        }
        if (frame.state == SmartAccessProtocol.RequestState.FIRST_PACK_WAITING) {
            switch (frame.commandId) {
                case SmartAccessProtocol.REQUEST_USER_CERTIFICATE:
                    if (certificateAccepted || ready || command != null || outgoing != null)
                        throw new IllegalArgumentException("Oturum yeniden yetkilendirme istiyor.");
                    listener.onStage("Araç kullanıcı sertifikasını istiyor; sertifika gönderiliyor.");
                    sendData(SmartAccessProtocol.USER_CERTIFICATE_MSG, certificate.getRawCertificate(), frame.commandId);
                    break;
                case SmartAccessProtocol.REQUEST_SIGNED_CHALLENGE:
                    if (!certificateAccepted || samNonce == null || carKey.length == 0
                            || phoneProofAccepted || command != null || outgoing != null)
                        throw new IllegalArgumentException("İmzalanacak oturum challenge'ı alınmadı.");
                    listener.onStage("Telefon anahtarıyla araç challenge'ı imzalanıyor.");
                    byte[] signature = signer.sign(samNonce);
                    if (signature.length != 64) throw new GeneralSecurityException("İmza uzunluğu geçersiz.");
                    byte[] signed = Arrays.copyOf(samNonce, samNonce.length + signature.length);
                    System.arraycopy(signature, 0, signed, samNonce.length, signature.length);
                    sendData(SmartAccessProtocol.SIGNED_CHALLENGE_MSG, signed, frame.commandId);
                    break;
                default:
                    // Permission/calibration/delete operations are never synthesized.
                    listener.onStage("Araç istek türü: 0x" + Integer.toHexString(frame.commandId));
            }
            return;
        }
        if (frame.state != SmartAccessProtocol.RequestState.PACK_COMPLETE) return;
        if (outgoing == null || frame.commandId != outgoingRequest)
            throw new IllegalArgumentException("Gönderilmiş mesajı olmayan araç tam mesaj onayı.");
        int total = 0;
        for (byte[] packet : outgoing) total += packet.length - 8;
        if (frame.rawIuu != total || frame.rawJuu != 0 || outgoingBlock != outgoing.size())
            throw new IllegalArgumentException("Araç tam mesaj onayı eşleşmiyor.");
        outgoing = null;
        if (frame.commandId == SmartAccessProtocol.REQUEST_USER_CERTIFICATE) {
            certificateAccepted = true;
            listener.onStage("Kullanıcı sertifikası araç tarafından alındı; araç anahtarı doğrulanıyor.");
            incomingRequest = SmartAccessProtocol.REQUEST_USER_CERTIFICATE;
            awaitingCarKey = carKey.length == 0;
            transport.send(SmartAccessProtocol.encodeRequest(incomingRequest,
                    carKey.length == 0 ? SmartAccessProtocol.RequestState.FIRST_PACK_WAITING
                            : SmartAccessProtocol.RequestState.PACK_COMPLETE, 0, 1));
        } else if (frame.commandId == SmartAccessProtocol.REQUEST_SIGNED_CHALLENGE) {
            if (!certificateAccepted || samNonce == null)
                throw new IllegalArgumentException("Beklenmeyen challenge onayı.");
            phoneProofAccepted = true;
            listener.onStage("Telefon challenge imzası alındı; aracın imzası kontrol ediliyor.");
            ownNonce = new byte[SmartAccessProtocol.CHALLENGE_LENGTH];
            new SecureRandom().nextBytes(ownNonce);
            // Original 0x22963b0 sends the nonce and then asks for its signed reply.
            for (byte[] packet : SmartAccessProtocol.encodeDataFrames(SmartAccessProtocol.RAND_CHALLENGE_MSG,
                    ownNonce, packetCapacity)) transport.send(packet);
            incomingRequest = SmartAccessProtocol.REQUEST_SIGNED_CHALLENGE;
            transport.send(SmartAccessProtocol.initialRequest(incomingRequest));
        } else if (frame.commandId == SmartAccessProtocol.REQUEST_APP_USER_CMD && commandSent) {
            commandAcknowledged = true;
            listener.onStage("İmzalı komut alındı; fiziksel araç durumu bekleniyor.");
            requestStatus();
        }
    }

    private int packetCapacity = SmartAccessProtocol.DEFAULT_PACKET_CAPACITY;
    public void setPacketCapacity(int capacity) {
        if (capacity < 20 || capacity > SmartAccessProtocol.DEFAULT_PACKET_CAPACITY)
            throw new IllegalArgumentException("BLE paket kapasitesi geçersiz.");
        packetCapacity = capacity;
    }

    private int sentPayloadLength() {
        int length = 0;
        for (int i = 0; i < outgoingBlock; i++) length += outgoing.get(i).length - 8;
        return length;
    }

    private void sendData(int id, byte[] data, int requestId) {
        if (outgoing != null) throw new IllegalArgumentException("Önceki BLE mesajının onayı bekleniyor.");
        outgoing = SmartAccessProtocol.encodeDataFrames(id, data, packetCapacity);
        outgoingRequest = requestId; outgoingBlock = 1; incomingRequest = requestId;
        transport.send(outgoing.get(0));
    }

    private Runnable onMessage(SmartAccessProtocol.Message message) throws GeneralSecurityException {
        byte[] data = message.payload();
        switch (message.commandId) {
            case SmartAccessProtocol.USER_CERTIFICATE_MSG:
                if (!certificateAccepted || !awaitingCarKey || phoneProofAccepted
                        || (data.length != 64 && data.length != 65))
                    throw new IllegalArgumentException("Araç public key mesajı geçersiz.");
                SmartAccessKeyStore.normalizePublicKey(data);
                if (carKey.length != 0 && !MessageDigest.isEqual(normalizeKey(carKey), normalizeKey(data)))
                    throw new GeneralSecurityException("Kayıtlı araç public key'i değişti.");
                if (!listener.onCarPublicKey(data)) throw new GeneralSecurityException("Araç anahtarı kaydedilemedi.");
                carKey = data.clone(); awaitingCarKey = false;
                return () -> listener.onStage("Araç anahtarı alındı; karşılıklı challenge bekleniyor.");
            case SmartAccessProtocol.RAND_CHALLENGE_MSG:
                if (data.length != SmartAccessProtocol.CHALLENGE_LENGTH)
                    throw new IllegalArgumentException("Challenge uzunluğu geçersiz.");
                if (command != null && isReady()) {
                    if (commandSent) throw new IllegalArgumentException("Komut challenge'ı tekrarlandı.");
                    byte[] signing = SmartAccessProtocol.commandSigningData(command, System.currentTimeMillis(), data);
                    byte[] payload = SmartAccessProtocol.signedCommandPayload(signing, signer.sign(signing));
                    commandSent = true;
                    return () -> {
                        listener.onStage("İmzalı araç komutu gönderiliyor.");
                        sendData(SmartAccessProtocol.APP_USER_CMD_MSG, payload, SmartAccessProtocol.REQUEST_APP_USER_CMD);
                    };
                } else {
                    if (!certificateAccepted || carKey.length == 0 || phoneProofAccepted || ready || samNonce != null)
                        throw new IllegalArgumentException("Beklenmeyen araç challenge'ı.");
                    samNonce = data.clone();
                    return () -> listener.onStage("Araç challenge'ı alındı; imza isteği bekleniyor.");
                }
            case SmartAccessProtocol.SIGNED_CHALLENGE_MSG:
                if (!phoneProofAccepted || carProofAccepted || ownNonce == null || carKey.length == 0 || data.length != 80
                        || !MessageDigest.isEqual(ownNonce, Arrays.copyOf(data, 10)))
                    throw new GeneralSecurityException("Araç challenge yanıtı eşleşmiyor.");
                if (!SmartAccessKeyStore.verify(Arrays.copyOf(data, 16), Arrays.copyOfRange(data, 16, 80), carKey))
                    throw new GeneralSecurityException("Araç challenge imzası geçersiz.");
                counter = littleEndian(data, 10, 6); carProofAccepted = true;
                Arrays.fill(ownNonce, (byte) 0); ownNonce = null;
                Arrays.fill(samNonce, (byte) 0); samNonce = null;
                return () -> {
                    listener.onStage("Karşılıklı imza doğrulandı; imzalı araç durumu bekleniyor.");
                    requestStatus();
                };
            case SmartAccessProtocol.SAM_APP_STATUS_MSG:
                if (!certificateAccepted || !phoneProofAccepted || !carProofAccepted || carKey.length == 0)
                    throw new GeneralSecurityException("Yetkilendirilmeden araç durumu alındı.");
                SmartAccessProtocol.SignedStatus incomingStatus = SmartAccessProtocol.decodeSignedStatus(data);
                if (!incomingStatus.isNewerThan(counter)
                        || !SmartAccessKeyStore.verify(incomingStatus.signedData(), incomingStatus.signature(), carKey))
                    throw new GeneralSecurityException("Araç durum imzası veya sayacı geçersiz.");
                if (!certificate.isValidAt(System.currentTimeMillis())) throw new GeneralSecurityException("Sertifika süresi doldu.");
                counter = incomingStatus.counter; status = incomingStatus.status; statusAt = System.currentTimeMillis();
                boolean becameReady = !ready; ready = true;
                boolean commandConfirmed = command != null && commandSent && commandAcknowledged && matches(command, status);
                if (commandConfirmed) { command = null; commandSent = commandAcknowledged = false; }
                SmartAccessProtocol.RawStatus publishedStatus = status;
                long publishedCounter = counter;
                return () -> {
                    listener.onStatus(publishedStatus, publishedCounter);
                    if (becameReady) listener.onReady();
                    if (commandConfirmed) listener.onCommandResult(true, "Komut sonucu imzalı araç durumuyla doğrulandı.");
                };
            default:
                throw new IllegalArgumentException("Desteklenmeyen BLE veri mesajı.");
        }
    }

    public void requestStatus() {
        if (!closed && certificateAccepted && phoneProofAccepted && carProofAccepted)
            transport.send(SmartAccessProtocol.initialRequest(SmartAccessProtocol.REQUEST_SAM_APP_STATUS));
    }

    public void sendCommand(SmartAccessProtocol.CarCommand command) {
        if (command == null) throw new IllegalArgumentException("Araç komutu gerekli.");
        if (!isReady() || status == null) throw new IllegalStateException("Yetkili BLE oturumu hazır değil.");
        if (this.command != null || outgoing != null) throw new IllegalStateException("Önceki BLE işlemi tamamlanmadı.");
        if (System.currentTimeMillis() - statusAt > 20000) throw new IllegalStateException("Araç durumu güncel değil; oturumu yenileyin.");
        if (!status.isInRange) throw new IllegalStateException("Telefon araç erişim menzilinde değil.");
        switch (command) {
            case LOCK: case UNLOCK:
                if (!certificate.canDoorOpen) throw new IllegalStateException("Sertifikanın kapı yetkisi yok."); break;
            case TRUNK_OPEN: case TRUNK_CLOSE:
                if (!certificate.canTrunkOpen) throw new IllegalStateException("Sertifikanın bagaj yetkisi yok.");
                if (status.canETGOpen != SmartAccessProtocol.RangeState.IN_RANGE)
                    throw new IllegalStateException("Bagaj erişimi araç tarafından uygun görülmüyor."); break;
            case EMOTOR_START:
                if (!certificate.canDrive) throw new IllegalStateException("Sertifikanın sürüş yetkisi yok.");
                if (!status.canStart) throw new IllegalStateException("Araç başlatmaya hazır değil; telefonun araçtaki konumunu kontrol edin."); break;
        }
        this.command = command; commandSent = commandAcknowledged = false;
        incomingRequest = SmartAccessProtocol.REQUEST_APP_USER_CMD;
        listener.onStage("Araç komutu için yeni challenge isteniyor.");
        transport.send(SmartAccessProtocol.initialRequest(incomingRequest));
    }

    public void timeoutCommand() {
        if (command == null) return;
        // Closing prevents a late challenge or ACK from being reused by a later command.
        fail("Komutun sonucu araçtan doğrulanamadı. Araç durumunu kontrol edip BLE oturumunu yeniden açın.");
    }

    static boolean matches(SmartAccessProtocol.CarCommand command, SmartAccessProtocol.RawStatus status) {
        switch (command) {
            case LOCK: return status.lockState == SmartAccessProtocol.LockState.CLOSED;
            case UNLOCK: return status.lockState == SmartAccessProtocol.LockState.OPEN;
            case TRUNK_OPEN: return status.ajarTrunkLid;
            case TRUNK_CLOSE: return !status.ajarTrunkLid;
            case EMOTOR_START: return status.vehStat == SmartAccessProtocol.PowerState.DRIVING;
            default: return false;
        }
    }

    private void fail(String message) {
        boolean pending = command != null;
        close();
        if (pending) listener.onCommandResult(false, message);
        listener.onError(message);
    }

    private static byte[] normalizeKey(byte[] key) {
        return key.length == 65 && key[0] == 4 ? Arrays.copyOfRange(key, 1, 65) : key;
    }
    private static long littleEndian(byte[] data, int offset, int size) {
        long value = 0;
        for (int i = 0; i < size; i++) value |= (long) (data[offset+i] & 255) << (8*i);
        return value;
    }
}
