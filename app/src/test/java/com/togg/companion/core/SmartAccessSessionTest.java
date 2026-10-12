package com.togg.companion.core;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Local SAM simulation using real P-256 signatures; no vehicle or server calls. */
public class SmartAccessSessionTest {
    private static final String USER = "00112233-4455-6677-8899-aabbccddeeff";
    private static final String VIN = "NL1CFB0P800100522";

    @Test public void reactiveHandshakeUsesRealSignaturesAndAcknowledgesBeforeFollowUp() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.toCarProof();
        assertFalse(fixture.session.isReady());
        fixture.status(101, 1, false, 3);
        assertTrue(fixture.session.isReady());
        assertEquals(1, fixture.events.ready);
        assertEquals(1, fixture.events.statuses);
        assertEquals(SmartAccessProtocol.PowerState.ON, fixture.session.getStatus().vehStat);
        assertRequest(fixture.events.onlyFrame(), 0x14, 2, 85, 0);
        assertEquals(0, fixture.events.errors.size());
    }

    @Test public void cachedSamKeyUsesOriginalCompleteStartupRequest() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.certificateAccepted();
        assertRequest(fixture.events.onlyFrame(), 0x10, 2, 0, 1);
        fixture.toPhoneProof();
        fixture.finishCarProof();
        fixture.status(101, 1, false, 4);
        assertTrue(fixture.session.isReady());
        assertEquals(0, fixture.events.keyPins);
    }

    @Test public void commandRequiresAcknowledgmentAndFreshSignedPhysicalStatus() throws Exception {
        Fixture fixture = new Fixture(false); fixture.ready(); fixture.events.clearFrames();
        fixture.session.sendCommand(SmartAccessProtocol.CarCommand.UNLOCK);
        assertRequest(fixture.events.onlyFrame(), 0x12, 0, 0, 0);
        fixture.events.clearFrames();
        byte[] nonce = new byte[10]; Arrays.fill(nonce, (byte) 0x42);
        fixture.data(0x27, nonce);
        assertRequest(fixture.events.frames.get(0), 0x12, 2, 10, 0);
        byte[] payload = SmartAccessProtocol.decodeDataFrame(fixture.events.frames.get(1)).payload();
        assertEquals(82, payload.length); assertEquals(2, payload[0]);
        assertArrayEquals(nonce, Arrays.copyOfRange(payload, 8, 18));
        assertTrue(SmartAccessKeyStore.verify(Arrays.copyOf(payload, 18),
                Arrays.copyOfRange(payload, 18, 82), fixture.phonePublicKey));
        fixture.events.clearFrames();
        fixture.status(102, 2, false, 3);
        assertEquals(0, fixture.events.results.size());
        fixture.events.clearFrames();
        fixture.request(0x12, 2, 82, 0);
        assertRequest(fixture.events.onlyFrame(), 0x14, 0, 0, 0);
        fixture.events.clearFrames(); fixture.status(103, 2, false, 3);
        assertEquals(Arrays.asList(true), fixture.events.results);
        assertTrue(fixture.session.isReady());
        fixture.session.sendCommand(SmartAccessProtocol.CarCommand.LOCK);
        assertEquals(0, fixture.events.errors.size());
    }

    @Test public void motorReadyRequiresDrivingAndNotAccessoryOn() throws Exception {
        Fixture fixture = new Fixture(false); fixture.ready(); fixture.events.clearFrames();
        fixture.session.sendCommand(SmartAccessProtocol.CarCommand.EMOTOR_START);
        fixture.events.clearFrames(); fixture.data(0x27, new byte[10]);
        fixture.events.clearFrames(); fixture.request(0x12, 2, 82, 0);
        fixture.events.clearFrames(); fixture.status(102, 1, false, 3);
        assertEquals(0, fixture.events.results.size());
        fixture.events.clearFrames(); fixture.status(103, 1, false, 4);
        assertEquals(Arrays.asList(true), fixture.events.results);
    }

    @Test public void unsolicitedCertificateCompleteCannotAuthorize() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.request(0x10, 2, 0, 0);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void unsolicitedChallengeCompleteCannotAcceptPhoneProof() throws Exception {
        Fixture fixture = new Fixture(false); fixture.toSamNonce(); fixture.events.clearFrames();
        fixture.request(0x11, 2, 74, 0);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void incompleteCertificateCannotBeAcknowledgedAsComplete() throws Exception {
        Fixture fixture = new Fixture(false); fixture.request(0x10, 0, 0, 0);
        fixture.events.clearFrames(); fixture.request(0x10, 2, 247, 0);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void partialAcknowledgmentMustMatchLengthAndNextBlock() throws Exception {
        Fixture fixture = new Fixture(false); fixture.request(0x10, 0, 0, 0);
        fixture.events.clearFrames(); fixture.request(0x10, 1, 239, 3);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void completeAcknowledgmentMustMatchExactPayloadLength() throws Exception {
        Fixture fixture = new Fixture(false); fixture.request(0x10, 0, 0, 0);
        fixture.request(0x10, 1, 239, 2); fixture.events.clearFrames();
        fixture.request(0x10, 2, 246, 0);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void unknownEarlyStatusDoesNotAbortOrAuthorizeHandshake() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.session.receive(new byte[] {0x26, 1, 0, 0, 0, 0, 0, 0});
        assertFalse(fixture.session.isReady()); assertEquals(0, fixture.events.errors.size());
        assertEquals(0, fixture.events.frames.size());
        fixture.ready(); assertTrue(fixture.session.isReady());
    }

    @Test public void overlappingIncomingFirstBlockClosesSession() throws Exception {
        Fixture fixture = new Fixture(false); fixture.certificateAccepted(); fixture.events.clearFrames();
        List<byte[]> keyPackets = SmartAccessProtocol.encodeDataFrames(0x21, fixture.samPublicKey, 20);
        fixture.session.receive(keyPackets.get(0));
        assertRequest(fixture.events.onlyFrame(), 0x10, 1, 12, 2);
        fixture.events.clearFrames(); fixture.session.receive(keyPackets.get(0));
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void invalidCarProofSignatureNeverGetsCompleteAck() throws Exception {
        Fixture fixture = new Fixture(false); fixture.toPhoneProof();
        byte[] proof = fixture.carProof(); proof[16] ^= 1;
        fixture.data(0x22, proof);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void carProofMustContainThePhoneNonce() throws Exception {
        Fixture fixture = new Fixture(false); fixture.toPhoneProof();
        byte[] proof = fixture.carProof(); proof[0] ^= 1;
        byte[] signature = sign(fixture.sam, Arrays.copyOf(proof, 16));
        System.arraycopy(signature, 0, proof, 16, 64);
        fixture.data(0x22, proof);
        assertClosed(fixture); assertEquals(0, fixture.events.frames.size());
    }

    @Test public void invalidSignedStatusNeverPublishesOrGetsCompleteAck() throws Exception {
        Fixture fixture = new Fixture(false); fixture.toCarProof(); fixture.events.clearFrames();
        byte[] status = fixture.statusPayload(101, 1, false, 3); status[21] ^= 1;
        fixture.data(0x26, status);
        assertClosed(fixture); assertEquals(0, fixture.events.statuses);
        assertEquals(0, fixture.events.frames.size());
    }

    @Test public void statusCounterMustAdvanceBeyondChallengeAndCannotReplay() throws Exception {
        Fixture fixture = new Fixture(false); fixture.toCarProof(); fixture.events.clearFrames();
        fixture.status(100, 1, false, 3);
        assertClosed(fixture); assertEquals(0, fixture.events.statuses);
        Fixture ready = new Fixture(false); ready.ready(); ready.events.clearFrames();
        ready.status(101, 2, false, 3);
        assertClosed(ready); assertEquals(1, ready.events.statuses);
        assertEquals(0, ready.events.frames.size());
    }

    @Test public void commandTimeoutClosesAgainstLateNonceAndAcknowledgments() throws Exception {
        Fixture fixture = new Fixture(false); fixture.ready(); fixture.events.clearFrames();
        fixture.session.sendCommand(SmartAccessProtocol.CarCommand.UNLOCK);
        fixture.session.timeoutCommand(); assertClosed(fixture);
        assertEquals(Arrays.asList(false), fixture.events.results);
        fixture.events.clearFrames(); fixture.data(0x27, new byte[10]); fixture.request(0x12, 2, 82, 0);
        assertEquals(0, fixture.events.frames.size());
        assertEquals(1, fixture.events.results.size());
        try { fixture.session.sendCommand(SmartAccessProtocol.CarCommand.LOCK); fail("Closed session"); }
        catch (IllegalStateException expected) { }
    }

    @Test public void carKeyChangingDuringBindingIsRejected() throws Exception {
        Fixture fixture = new Fixture(false); fixture.certificateAccepted(); fixture.events.clearFrames();
        byte[] invalidKey = new byte[65]; invalidKey[0] = 3;
        fixture.data(0x21, invalidKey);
        assertClosed(fixture); assertEquals(0, fixture.events.keyPins);
        assertEquals(0, fixture.events.frames.size());
    }

    @Test public void duplicateSamNonceCannotReplaceTheNonceBeingSigned() throws Exception {
        Fixture fixture = new Fixture(false); fixture.toSamNonce(); fixture.events.clearFrames();
        fixture.data(0x27, new byte[10]); assertClosed(fixture);
        assertEquals(0, fixture.events.frames.size());
    }

    @Test public void minimumMtuFragmentsCertificateProofCommandAndSignedStatus() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.session.setPacketCapacity(20);
        fixture.request(0x10, 0, 0, 0);
        assertEquals(247, outgoing(fixture, 0x10, 0x21).length);
        fixture.events.clearFrames();
        incomingSmall(fixture, 0x21, Arrays.copyOfRange(fixture.samPublicKey, 1, 65));
        fixture.events.clearFrames();
        byte[] nonce = new byte[10]; Arrays.fill(nonce, (byte) 0x31);
        incomingSmall(fixture, 0x27, nonce); fixture.events.clearFrames();
        fixture.request(0x11, 0, 0, 0);
        byte[] proof = outgoing(fixture, 0x11, 0x22);
        assertEquals(74, proof.length);
        assertTrue(SmartAccessKeyStore.verify(nonce, Arrays.copyOfRange(proof, 10, 74), fixture.phonePublicKey));
        fixture.ownNonce = SmartAccessProtocol.decodeDataFrame(fixture.events.frames.get(0)).payload();
        fixture.events.clearFrames(); incomingSmall(fixture, 0x22, fixture.carProof());
        fixture.events.clearFrames(); incomingSmall(fixture, 0x26, fixture.statusPayload(101, 1, false, 3));
        assertTrue(fixture.session.isReady());
        fixture.events.clearFrames(); fixture.session.sendCommand(SmartAccessProtocol.CarCommand.UNLOCK);
        fixture.events.clearFrames(); incomingSmall(fixture, 0x27, nonce);
        // Nonce ACK comes before the first command fragment.
        fixture.events.frames.remove(0);
        assertEquals(82, outgoing(fixture, 0x12, 0x23).length);
        fixture.events.clearFrames(); incomingSmall(fixture, 0x26, fixture.statusPayload(102, 2, false, 3));
        assertEquals(Arrays.asList(true), fixture.events.results);
        assertEquals(0, fixture.events.errors.size());
    }

    private static void incomingSmall(Fixture fixture, int id, byte[] payload) {
        for (byte[] packet : SmartAccessProtocol.encodeDataFrames(id, payload, 20)) fixture.session.receive(packet);
    }
    private static byte[] outgoing(Fixture fixture, int requestId, int dataId) {
        SmartAccessProtocol.Reassembler buffer = new SmartAccessProtocol.Reassembler();
        while (true) {
            byte[] packet = fixture.events.onlyFrame(); assertTrue(packet.length <= 20);
            SmartAccessProtocol.DataFrame frame = SmartAccessProtocol.decodeDataFrame(packet);
            assertEquals(dataId, frame.commandId);
            SmartAccessProtocol.Message complete = buffer.accept(frame); fixture.events.clearFrames();
            if (complete != null) {
                fixture.request(requestId, 2, buffer.receivedLength(), 0); return complete.payload();
            }
            fixture.request(requestId, 1, buffer.receivedLength(), frame.block + 1);
        }
    }

    private static void assertClosed(Fixture fixture) {
        assertFalse(fixture.session.isReady()); assertEquals(1, fixture.events.errors.size());
    }

    private static void assertRequest(byte[] bytes, int id, int state, int length, long nextBlock) {
        SmartAccessProtocol.RequestFrame frame = SmartAccessProtocol.decodeRequest(bytes);
        assertEquals(id, frame.commandId); assertEquals(state, frame.state.ordinal());
        assertEquals(length, frame.rawIuu); assertEquals(nextBlock, frame.rawJuu);
    }

    private static byte[] sign(KeyPair pair, byte[] data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(pair.getPrivate()); signature.update(data);
        return SmartAccessKeyStore.derToRaw(signature.sign());
    }

    private static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1")); return generator.generateKeyPair();
    }

    private static final class Events implements SmartAccessSession.Listener, SmartAccessSession.Transport {
        final List<byte[]> frames = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<Boolean> results = new ArrayList<>();
        int ready, statuses, keyPins;
        @Override public void send(byte[] frame) { frames.add(frame.clone()); }
        @Override public void onStage(String message) { }
        @Override public boolean onCarPublicKey(byte[] key) { keyPins++; return true; }
        @Override public void onReady() { ready++; }
        @Override public void onStatus(SmartAccessProtocol.RawStatus status, long counter) { statuses++; }
        @Override public void onCommandResult(boolean success, String message) { results.add(success); }
        @Override public void onError(String message) { errors.add(message); }
        void clearFrames() { frames.clear(); }
        byte[] onlyFrame() { assertEquals(1, frames.size()); return frames.get(0); }
    }

    private static final class Fixture {
        final KeyPair phone = keyPair(), sam = keyPair();
        final byte[] phonePublicKey = SmartAccessKeyStore.encodePublicKey((ECPublicKey) phone.getPublic());
        final byte[] samPublicKey = SmartAccessKeyStore.encodePublicKey((ECPublicKey) sam.getPublic());
        final Events events = new Events();
        final SmartAccessSession session;
        final boolean cached;
        byte[] ownNonce;
        Fixture(boolean cached) throws GeneralSecurityException {
            this.cached = cached;
            byte[] certificate = Arrays.copyOf(SmartAccessCertificate.buildOwnerSigningData(USER, VIN,
                    phonePublicKey, System.currentTimeMillis() - 60000), 247);
            session = new SmartAccessSession(SmartAccessCertificate.parse(certificate, 0),
                    cached ? samPublicKey : null, data -> sign(phone, data), events, events);
            session.start();
        }
        void request(int id, int state, int length, long nextBlock) {
            session.receive(SmartAccessProtocol.encodeRequest(id,
                    SmartAccessProtocol.RequestState.values()[state], length, nextBlock));
        }
        void data(int id, byte[] payload) {
            for (byte[] frame : SmartAccessProtocol.encodeDataFrames(id, payload, 247)) session.receive(frame);
        }
        void certificateAccepted() {
            request(0x10, 0, 0, 0);
            assertEquals(239, SmartAccessProtocol.decodeDataFrame(events.onlyFrame()).payload().length);
            events.clearFrames(); request(0x10, 1, 239, 2);
            assertEquals(8, SmartAccessProtocol.decodeDataFrame(events.onlyFrame()).payload().length);
            events.clearFrames(); request(0x10, 2, 247, 0);
            assertRequest(events.onlyFrame(), 0x10, cached ? 2 : 0, 0, 1);
        }
        void toSamNonce() {
            certificateAccepted(); events.clearFrames();
            if (!cached) {
                data(0x21, Arrays.copyOfRange(samPublicKey, 1, 65));
                assertRequest(events.onlyFrame(), 0x10, 2, 64, 0); events.clearFrames();
            }
            byte[] samNonce = new byte[10]; Arrays.fill(samNonce, (byte) 0x31);
            data(0x27, samNonce); assertRequest(events.onlyFrame(), 0x10, 2, 10, 0);
        }
        void toPhoneProof() {
            if (events.frames.size() != 1 || (events.frames.get(0)[0] & 255) != 0x10
                    || !cached) toSamNonce();
            else {
                // The cached-key test already completed the certificate step.
                events.clearFrames(); byte[] nonce = new byte[10]; Arrays.fill(nonce, (byte) 0x31);
                data(0x27, nonce);
            }
            events.clearFrames(); request(0x11, 0, 0, 0);
            byte[] phoneProof = SmartAccessProtocol.decodeDataFrame(events.onlyFrame()).payload();
            assertEquals(74, phoneProof.length);
            assertTrue(SmartAccessKeyStore.verify(Arrays.copyOf(phoneProof, 10),
                    Arrays.copyOfRange(phoneProof, 10, 74), phonePublicKey));
            events.clearFrames(); request(0x11, 2, 74, 0);
            assertEquals(2, events.frames.size());
            SmartAccessProtocol.DataFrame frame = SmartAccessProtocol.decodeDataFrame(events.frames.get(0));
            assertEquals(0x27, frame.commandId); ownNonce = frame.payload();
            assertRequest(events.frames.get(1), 0x11, 0, 0, 0); events.clearFrames();
        }
        byte[] carProof() throws GeneralSecurityException {
            byte[] result = Arrays.copyOf(ownNonce, 80); result[10] = 100;
            System.arraycopy(sign(sam, Arrays.copyOf(result, 16)), 0, result, 16, 64); return result;
        }
        void finishCarProof() throws GeneralSecurityException {
            data(0x22, carProof()); assertEquals(2, events.frames.size());
            assertRequest(events.frames.get(0), 0x11, 2, 80, 0);
            assertRequest(events.frames.get(1), 0x14, 0, 0, 0); events.clearFrames();
        }
        void toCarProof() throws GeneralSecurityException { toPhoneProof(); finishCarProof(); }
        byte[] statusPayload(long counter, int lock, boolean trunkOpen, int power) throws GeneralSecurityException {
            byte[] result = new byte[85]; result[0] = result[1] = 1; result[2] = (byte) lock;
            result[7] = (byte) (trunkOpen ? 1 : 0); result[12] = result[13] = 1; result[14] = (byte) power;
            for (int i = 0; i < 6; i++) result[15 + i] = (byte) (counter >> (8 * i));
            System.arraycopy(sign(sam, Arrays.copyOf(result, 21)), 0, result, 21, 64); return result;
        }
        void status(long counter, int lock, boolean trunkOpen, int power) throws GeneralSecurityException {
            data(0x26, statusPayload(counter, lock, trunkOpen, power));
        }
        void ready() throws GeneralSecurityException { toCarProof(); status(101, 1, false, 3); }
    }
}
