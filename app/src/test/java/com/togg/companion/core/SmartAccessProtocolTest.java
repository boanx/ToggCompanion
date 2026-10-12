package com.togg.companion.core;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class SmartAccessProtocolTest {
    @Test public void commandPayloadUsesRecoveredIdAndUtcCalendarBytes() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
            long millis = Instant.parse("2026-10-12T06:07:08Z").toEpochMilli();
            assertArrayEquals(bytes(14, 7, 234, 10, 12, 6, 7, 8),
                    SmartAccessProtocol.commandPayload(SmartAccessProtocol.CarCommand.EMOTOR_START, millis));
            assertEquals(1, SmartAccessProtocol.CarCommand.LOCK.id);
            assertEquals(2, SmartAccessProtocol.CarCommand.UNLOCK.id);
            assertEquals(4, SmartAccessProtocol.CarCommand.TRUNK_OPEN.id);
            assertEquals(5, SmartAccessProtocol.CarCommand.TRUNK_CLOSE.id);
        } finally { TimeZone.setDefault(previous); }
    }

    @Test public void commandSignatureAppendsChallengeAndRaw64Signature() {
        byte[] challenge = bytes(0,1,2,3,4,5,6,7,8,9);
        byte[] data = SmartAccessProtocol.commandSigningData(SmartAccessProtocol.CarCommand.LOCK, 0, challenge);
        assertArrayEquals(bytes(1,7,178,1,1,0,0,0,0,1,2,3,4,5,6,7,8,9), data);
        byte[] signature = new byte[64];
        Arrays.fill(signature, (byte) 0xa5);
        byte[] payload = SmartAccessProtocol.signedCommandPayload(data, signature);
        assertEquals(82, payload.length);
        assertArrayEquals(data, Arrays.copyOf(payload, 18));
        assertArrayEquals(signature, Arrays.copyOfRange(payload, 18, 82));
        challenge[0] = 42; signature[0] = 42; data[0] = 42;
        assertEquals(1, payload[0]); assertEquals(0, payload[8]); assertEquals((byte) 0xa5, payload[18]);
    }

    @Test public void malformedCommandOrSignatureLengthsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.commandSigningData(
                SmartAccessProtocol.CarCommand.LOCK, 0, new byte[9]));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.signedCommandPayload(new byte[18], new byte[63]));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.signedCommandPayload(new byte[17], new byte[64]));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.commandPayload(
                SmartAccessProtocol.CarCommand.LOCK, Long.MAX_VALUE));
    }

    @Test public void requestFrameHasLittleEndian16And32BitFields() {
        byte[] encoded = SmartAccessProtocol.encodeRequest(SmartAccessProtocol.REQUEST_SAM_APP_STATUS,
                SmartAccessProtocol.RequestState.PRE_PACK_RECEIVED, 0x1234, 0x12345678L);
        assertArrayEquals(bytes(0x14,1,0x34,0x12,0x78,0x56,0x34,0x12), encoded);
        SmartAccessProtocol.RequestFrame decoded = SmartAccessProtocol.decodeRequest(encoded);
        assertEquals(0x14, decoded.commandId);
        assertEquals(SmartAccessProtocol.RequestState.PRE_PACK_RECEIVED, decoded.state);
        assertEquals(0x1234, decoded.rawIuu); assertEquals(0x12345678L, decoded.rawJuu);
        assertArrayEquals(bytes(0x12,0,0,0,0,0,0,0), SmartAccessProtocol.initialRequest(0x12));
    }

    @Test public void requestFieldsRetainUnsignedMaximum() {
        SmartAccessProtocol.RequestFrame decoded = SmartAccessProtocol.decodeRequest(
                SmartAccessProtocol.encodeRequest(0x10, SmartAccessProtocol.RequestState.PACK_COMPLETE, 65535, 0xffffffffL));
        assertEquals(65535, decoded.rawIuu); assertEquals(0xffffffffL, decoded.rawJuu);
    }

    @Test public void malformedRequestFieldsAndStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeRequest(0x12,
                SmartAccessProtocol.RequestState.FIRST_PACK_WAITING, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeRequest(0x12,
                SmartAccessProtocol.RequestState.FIRST_PACK_WAITING, 0, 0x100000000L));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.initialRequest(0xa1));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeRequest(bytes(0x12,3,0,0,0,0,0,0)));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeRequest(new byte[7]));
    }

    @Test public void default247BytePacketLeaves239BytePayloadChunk() {
        byte[] payload = new byte[300];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;
        List<byte[]> frames = SmartAccessProtocol.encodeDataFrames(0x23, payload, 247);
        assertEquals(2, frames.size()); assertEquals(247, frames.get(0).length); assertEquals(69, frames.get(1).length);
        assertArrayEquals(bytes(0x23,1,239,0,44,1,2,0), Arrays.copyOf(frames.get(0),8));
        assertArrayEquals(bytes(0x23,2,61,0,44,1,2,0), Arrays.copyOf(frames.get(1),8));
        assertArrayEquals(Arrays.copyOf(payload,239), SmartAccessProtocol.decodeDataFrame(frames.get(0)).payload());
    }

    @Test public void negotiatedSmallPacketLimitCreatesIndependentFrames() {
        List<byte[]> frames = SmartAccessProtocol.encodeDataFrames(0x21, new byte[25], 20);
        assertEquals(3, frames.size()); assertEquals(20, frames.get(0).length); assertEquals(9, frames.get(2).length);
        assertEquals(3, SmartAccessProtocol.decodeDataFrame(frames.get(2)).block);
    }

    @Test public void reassemblyReturnsOnlyCompleteMessageAndBuildsExactAcks() {
        byte[] payload = bytes(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25);
        List<byte[]> frames = SmartAccessProtocol.encodeDataFrames(0x26,payload,20);
        SmartAccessProtocol.Reassembler reassembler = new SmartAccessProtocol.Reassembler();
        assertArrayEquals(bytes(0x14,0,0,0,0,0,0,0), reassembler.acknowledgment(0x14));
        assertNull(reassembler.accept(SmartAccessProtocol.decodeDataFrame(frames.get(0))));
        assertArrayEquals(bytes(0x14,1,12,0,2,0,0,0), reassembler.acknowledgment(0x14));
        assertNull(reassembler.accept(SmartAccessProtocol.decodeDataFrame(frames.get(1))));
        assertArrayEquals(bytes(0x14,1,24,0,3,0,0,0), reassembler.acknowledgment(0x14));
        SmartAccessProtocol.Message message = reassembler.accept(SmartAccessProtocol.decodeDataFrame(frames.get(2)));
        assertEquals(0x26,message.commandId); assertArrayEquals(payload,message.payload()); assertTrue(reassembler.isComplete());
        assertArrayEquals(bytes(0x14,2,25,0,0,0,0,0), reassembler.acknowledgment(0x14));
        assertThrows(IllegalArgumentException.class, () -> reassembler.accept(SmartAccessProtocol.decodeDataFrame(frames.get(0))));
        reassembler.reset(); assertFalse(reassembler.isComplete()); assertEquals(0,reassembler.receivedLength());
    }

    @Test public void missingDuplicateOutOfOrderAndMixedFramesAreRejected() {
        List<byte[]> frames = SmartAccessProtocol.encodeDataFrames(0x26,new byte[30],20);
        SmartAccessProtocol.Reassembler r = new SmartAccessProtocol.Reassembler();
        assertThrows(IllegalArgumentException.class, () -> r.accept(SmartAccessProtocol.decodeDataFrame(frames.get(1))));
        assertNull(r.accept(SmartAccessProtocol.decodeDataFrame(frames.get(0))));
        assertThrows(IllegalArgumentException.class, () -> r.accept(SmartAccessProtocol.decodeDataFrame(frames.get(0))));
        assertThrows(IllegalArgumentException.class, () -> r.accept(SmartAccessProtocol.decodeDataFrame(frames.get(2))));
        byte[] wrongType = frames.get(1).clone(); wrongType[0] = 0x27;
        assertThrows(IllegalArgumentException.class, () -> r.accept(SmartAccessProtocol.decodeDataFrame(wrongType)));
        byte[] wrongLength = frames.get(1).clone(); wrongLength[4] = 31;
        assertThrows(IllegalArgumentException.class, () -> r.accept(SmartAccessProtocol.decodeDataFrame(wrongLength)));
        assertEquals(12,r.receivedLength()); assertEquals(1,r.lastBlock());
    }

    @Test public void inconsistentHeaderLengthsAndBlockCountsAreRejected() {
        byte[] frame = SmartAccessProtocol.encodeDataFrames(0x26,new byte[15],20).get(0);
        byte[] wrongCount = frame.clone(); wrongCount[2] = 11;
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeDataFrame(wrongCount));
        byte[] wrongBlock = frame.clone(); wrongBlock[1] = 0;
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeDataFrame(wrongBlock));
        byte[] wrongBlocks = frame.clone(); wrongBlocks[6] = 0;
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeDataFrame(wrongBlocks));
        byte[] wrongTotal = frame.clone(); wrongTotal[4] = 11;
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeDataFrame(wrongTotal));
        byte[] wrongType = frame.clone(); wrongType[0] = (byte)0xa1;
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeDataFrame(wrongType));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeDataFrame(new byte[8]));
    }

    @Test public void blockCompletionRequiresExactAccumulatedLength() {
        List<byte[]> frames = SmartAccessProtocol.encodeDataFrames(0x26,new byte[15],20);
        byte[] first = frames.get(0).clone(); first[4] = 16;
        byte[] last = frames.get(1).clone(); last[4] = 16;
        SmartAccessProtocol.Reassembler r = new SmartAccessProtocol.Reassembler();
        assertNull(r.accept(SmartAccessProtocol.decodeDataFrame(first)));
        assertThrows(IllegalArgumentException.class, () -> r.accept(SmartAccessProtocol.decodeDataFrame(last)));
        assertEquals(12,r.receivedLength()); assertFalse(r.isComplete());
    }

    @Test public void frameEncoderRejectsPayloadOrPacketLimits() {
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeDataFrames(0x23,new byte[0],247));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeDataFrames(0x23,new byte[65536],247));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeDataFrames(0x23,new byte[256],9));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeDataFrames(0x23,new byte[1],8));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeDataFrames(0x23,new byte[1],248));
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.encodeDataFrames(0x12,new byte[1],247));
    }

    @Test public void signedStatusSlicesCounterAndSignatureWithoutMarkingItAuthenticated() {
        byte[] fixture = statusFixture();
        SmartAccessProtocol.SignedStatus status = SmartAccessProtocol.decodeSignedStatus(fixture);
        assertEquals(0x010203040506L,status.counter);
        assertArrayEquals(Arrays.copyOf(fixture,21),status.signedData());
        assertArrayEquals(Arrays.copyOfRange(fixture,21,85),status.signature());
        assertTrue(status.isNewerThan(0x010203040505L));
        assertFalse(status.isNewerThan(status.counter)); assertFalse(status.isNewerThan(status.counter+1));
        fixture[0] = 0; byte[] signature = status.signature(); signature[0] = 0;
        assertTrue(status.status.canStart); assertEquals((byte)0xa5,status.signature()[0]);
    }

    @Test public void statusIndexesMatchOriginalParserConstructorAndNamedToString() {
        SmartAccessProtocol.RawStatus status = SmartAccessProtocol.decodeSignedStatus(statusFixture()).status;
        assertTrue(status.canStart); assertTrue(status.isInRange);
        assertEquals(SmartAccessProtocol.LockState.CLOSED,status.lockState);
        assertTrue(status.ajarDriver); assertFalse(status.ajarCoDriver); assertTrue(status.ajarRearLeft);
        assertFalse(status.ajarRearRight); assertTrue(status.ajarTrunkLid);
        assertFalse(status.windowFrontLeft); assertTrue(status.windowFrontRight);
        assertFalse(status.windowRearLeft); assertTrue(status.windowRearRight);
        assertEquals(SmartAccessProtocol.RangeState.IN_RANGE,status.canETGOpen);
        assertEquals(SmartAccessProtocol.RangeState.OUT_OF_RANGE,status.canWindowOpenClose);
        assertEquals(SmartAccessProtocol.PowerState.DRIVING,status.vehStat);
    }

    @Test public void malformedSignedStatusEnumsOrLengthsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeSignedStatus(new byte[84]));
        for (int index : new int[] {2,12,13,14}) {
            byte[] fixture = statusFixture(); fixture[index] = (byte)255;
            assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeSignedStatus(fixture));
        }
        assertThrows(IllegalArgumentException.class, () -> SmartAccessProtocol.decodeSignedStatus(statusFixture()).status.flag(2));
    }

    @Test public void dataAndMessagePayloadAccessorsAreDefensiveCopies() {
        byte[] frame = SmartAccessProtocol.encodeDataFrames(0x23,bytes(1,2,3),20).get(0);
        SmartAccessProtocol.DataFrame decoded = SmartAccessProtocol.decodeDataFrame(frame);
        frame[8] = 42; decoded.payload()[0] = 42;
        assertArrayEquals(bytes(1,2,3),decoded.payload());
        SmartAccessProtocol.Message message = new SmartAccessProtocol.Reassembler().accept(decoded);
        message.payload()[0] = 42; assertArrayEquals(bytes(1,2,3),message.payload());
    }

    private static byte[] statusFixture() {
        byte[] bytes = new byte[85];
        byte[] status = bytes(1,1,1,1,0,1,0,1,0,1,0,1,1,0,4);
        System.arraycopy(status,0,bytes,0,15);
        byte[] counter = bytes(6,5,4,3,2,1);
        System.arraycopy(counter,0,bytes,15,6);
        Arrays.fill(bytes,21,85,(byte)0xa5);
        return bytes;
    }
    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return bytes;
    }
}
