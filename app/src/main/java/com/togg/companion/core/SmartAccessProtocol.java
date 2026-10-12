package com.togg.companion.core;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Smart Access bytes recovered from Trumore 2.1.25 ARM64 AOT.
 * Parsing proves structure only; the caller must verify signatures, freshness,
 * session authorization and command feedback before applying vehicle state.
 * Evidence: docs/TRUMORE_2_1_25_APK_VERIFICATION.md (AF133, AF134, AF142, AF143).
 */
public final class SmartAccessProtocol {
    public static final int HEADER_LENGTH = 8;
    public static final int DEFAULT_PACKET_CAPACITY = 247;
    public static final int CHALLENGE_LENGTH = 10;
    public static final int SIGNATURE_LENGTH = 64;
    public static final int STATUS_LENGTH = 15;
    public static final int SIGNED_STATUS_LENGTH = 85;
    public static final int REQUEST_USER_CERTIFICATE = 0x10;
    public static final int REQUEST_SIGNED_CHALLENGE = 0x11;
    public static final int REQUEST_APP_USER_CMD = 0x12;
    public static final int REQUEST_PERMISSION_CMD = 0x13;
    public static final int REQUEST_SAM_APP_STATUS = 0x14;
    public static final int USER_CERTIFICATE_MSG = 0x21;
    public static final int SIGNED_CHALLENGE_MSG = 0x22;
    public static final int APP_USER_CMD_MSG = 0x23;
    public static final int APP_USER_DELETEPAIR_MSG = 0x24;
    public static final int APP_USER_CALIBRATE = 0x25;
    public static final int SAM_APP_STATUS_MSG = 0x26;
    public static final int RAND_CHALLENGE_MSG = 0x27;

    private SmartAccessProtocol() { }

    public enum CarCommand {
        LOCK(0x01), UNLOCK(0x02), TRUNK_OPEN(0x04), TRUNK_CLOSE(0x05), EMOTOR_START(0x0e);
        public final int id;
        CarCommand(int id) { this.id = id; }
    }

    public enum RequestState {
        FIRST_PACK_WAITING(0), PRE_PACK_RECEIVED(1), PACK_COMPLETE(2);
        public final int id;
        RequestState(int id) { this.id = id; }
        private static RequestState fromId(int id) {
            for (RequestState state : values()) if (state.id == id) return state;
            throw malformed("Unknown previous-message state");
        }
    }

    public enum LockState { INIT_STATE, CLOSED, OPEN }
    public enum RangeState { OUT_OF_RANGE, IN_RANGE }
    public enum PowerState {
        SNA, SLEEP, AWAKE, ON, DRIVING, DIAGNOSTICS, PARTIAL_NETWORK, DEEP_SLEEP, PRE_ON, PRE_DRIVING
    }

    /** Original 0x19fa42c: command byte followed by UTC year BE16/month/day/h/m/s. */
    public static byte[] commandPayload(CarCommand command, long timestampMillis) {
        Objects.requireNonNull(command, "command");
        ZonedDateTime utc = Instant.ofEpochMilli(timestampMillis).atZone(ZoneOffset.UTC);
        int year = utc.getYear();
        if (year < 0 || year > 0xffff) throw malformed("Timestamp year does not fit protocol");
        return new byte[] {(byte) command.id, (byte) (year >>> 8), (byte) year,
                (byte) utc.getMonthValue(), (byte) utc.getDayOfMonth(),
                (byte) utc.getHour(), (byte) utc.getMinute(), (byte) utc.getSecond()};
    }

    public static byte[] commandSigningData(CarCommand command, long timestampMillis, byte[] challenge) {
        requireLength(challenge, CHALLENGE_LENGTH, "Challenge");
        byte[] data = Arrays.copyOf(commandPayload(command, timestampMillis), 8 + CHALLENGE_LENGTH);
        System.arraycopy(challenge, 0, data, 8, CHALLENGE_LENGTH);
        return data;
    }

    /** The caller signs commandSigningData with its provisioned per-user/VIN key. */
    public static byte[] signedCommandPayload(byte[] signingData, byte[] signature) {
        requireLength(signingData, 8 + CHALLENGE_LENGTH, "Command signing data");
        requireLength(signature, SIGNATURE_LENGTH, "Signature");
        byte[] payload = Arrays.copyOf(signingData, signingData.length + SIGNATURE_LENGTH);
        System.arraycopy(signature, 0, payload, signingData.length, signature.length);
        return payload;
    }

    /** Original 0x1f0c23c. Raw fields are named only where their meaning is proven. */
    public static byte[] encodeRequest(int commandId, RequestState state, int rawIuu, long rawJuu) {
        requireRequestId(commandId);
        Objects.requireNonNull(state, "state");
        requireRange(rawIuu, 0xffffL, "Iuu");
        requireRange(rawJuu, 0xffffffffL, "Juu");
        byte[] bytes = new byte[HEADER_LENGTH];
        bytes[0] = (byte) commandId;
        bytes[1] = (byte) state.id;
        putLe(bytes, 2, rawIuu, 2);
        putLe(bytes, 4, rawJuu, 4);
        return bytes;
    }

    /** Original request constructor defaults: state FIRST, Iuu=0, Juu=0. */
    public static byte[] initialRequest(int commandId) {
        return encodeRequest(commandId, RequestState.FIRST_PACK_WAITING, 0, 0);
    }

    public static RequestFrame decodeRequest(byte[] bytes) {
        requireLength(bytes, HEADER_LENGTH, "Request frame");
        int id = unsigned(bytes[0]);
        requireRequestId(id);
        return new RequestFrame(id, RequestState.fromId(unsigned(bytes[1])),
                (int) le(bytes, 2, 2), le(bytes, 4, 4));
    }

    /** Original packet builder defaults to 247 bytes including its eight-byte header. */
    public static List<byte[]> encodeDataFrames(int commandId, byte[] payload, int packetCapacity) {
        requireDataId(commandId);
        Objects.requireNonNull(payload, "payload");
        if (packetCapacity <= HEADER_LENGTH || packetCapacity > DEFAULT_PACKET_CAPACITY)
            throw malformed("Packet capacity must be between 9 and 247");
        if (payload.length == 0 || payload.length > 0xffff)
            throw malformed("Payload length must be between 1 and 65535");
        int chunkCapacity = packetCapacity - HEADER_LENGTH;
        int totalBlocks = (payload.length + chunkCapacity - 1) / chunkCapacity;
        if (totalBlocks > 0xff) throw malformed("Current block cannot exceed one byte");
        List<byte[]> frames = new ArrayList<>(totalBlocks);
        for (int block = 1, offset = 0; block <= totalBlocks; block++) {
            int count = Math.min(chunkCapacity, payload.length - offset);
            byte[] frame = new byte[HEADER_LENGTH + count];
            frame[0] = (byte) commandId;
            frame[1] = (byte) block;
            putLe(frame, 2, count, 2);
            putLe(frame, 4, payload.length, 2);
            putLe(frame, 6, totalBlocks, 2);
            System.arraycopy(payload, offset, frame, HEADER_LENGTH, count);
            frames.add(frame);
            offset += count;
        }
        return Collections.unmodifiableList(frames);
    }

    public static DataFrame decodeDataFrame(byte[] bytes) {
        if (bytes == null || bytes.length <= HEADER_LENGTH)
            throw malformed("Data frame must contain a header and payload");
        int commandId = unsigned(bytes[0]);
        requireDataId(commandId);
        int block = unsigned(bytes[1]);
        int count = (int) le(bytes, 2, 2);
        int totalLength = (int) le(bytes, 4, 2);
        int totalBlocks = (int) le(bytes, 6, 2);
        if (count != bytes.length - HEADER_LENGTH) throw malformed("Claimed payload length differs");
        if (block < 1 || totalBlocks < 1 || totalBlocks > 255 || block > totalBlocks)
            throw malformed("Invalid block metadata");
        if (count < 1 || totalLength < count || totalLength < totalBlocks)
            throw malformed("Invalid total payload length");
        if (totalBlocks == 1 && totalLength != count)
            throw malformed("Single-block payload is incomplete");
        if (totalBlocks > 1 && totalLength == count)
            throw malformed("Multiblock frame contains the entire payload");
        return new DataFrame(commandId, block, totalLength, totalBlocks,
                Arrays.copyOfRange(bytes, HEADER_LENGTH, bytes.length));
    }

    public static SignedStatus decodeSignedStatus(byte[] bytes) {
        requireLength(bytes, SIGNED_STATUS_LENGTH, "Signed status");
        RawStatus status = new RawStatus(Arrays.copyOf(bytes, STATUS_LENGTH));
        // AOT 0x19f81ac bounds-checks these enum indexes. Booleans accept any nonzero byte.
        return new SignedStatus(status, le(bytes, 15, 6),
                Arrays.copyOf(bytes, 21), Arrays.copyOfRange(bytes, 21, 85));
    }

    public static final class RequestFrame {
        public final int commandId;
        public final RequestState state;
        public final int rawIuu;
        public final long rawJuu;
        private RequestFrame(int commandId, RequestState state, int rawIuu, long rawJuu) {
            this.commandId = commandId; this.state = state; this.rawIuu = rawIuu; this.rawJuu = rawJuu;
        }
    }

    public static final class DataFrame {
        public final int commandId, block, totalLength, totalBlocks;
        private final byte[] payload;
        private DataFrame(int commandId, int block, int totalLength, int totalBlocks, byte[] payload) {
            this.commandId = commandId; this.block = block; this.totalLength = totalLength;
            this.totalBlocks = totalBlocks; this.payload = payload.clone();
        }
        public byte[] payload() { return payload.clone(); }
    }

    public static final class Message {
        public final int commandId;
        private final byte[] payload;
        private Message(int commandId, byte[] payload) { this.commandId = commandId; this.payload = payload.clone(); }
        public byte[] payload() { return payload.clone(); }
    }

    /** One in-flight message; the caller resets it on timeout/disconnect/new exchange. */
    public static final class Reassembler {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int commandId = -1, totalLength, totalBlocks, lastBlock;
        private boolean complete;

        public Message accept(DataFrame frame) {
            Objects.requireNonNull(frame, "frame");
            if (complete) throw malformed("Completed message must be reset");
            if (commandId == -1) {
                if (frame.block != 1) throw malformed("First block is missing");
                commandId = frame.commandId; totalLength = frame.totalLength; totalBlocks = frame.totalBlocks;
            } else if (commandId != frame.commandId || totalLength != frame.totalLength || totalBlocks != frame.totalBlocks) {
                throw malformed("Packets are incompatible");
            }
            if (frame.block != lastBlock + 1) throw malformed("Duplicate or out-of-order block");
            int newLength = bytes.size() + frame.payload.length;
            int remainingBlocks = totalBlocks - frame.block;
            if (newLength > totalLength || newLength + remainingBlocks > totalLength
                    || (frame.block == totalBlocks) != (newLength == totalLength))
                throw malformed("Payload and block completion disagree");
            bytes.write(frame.payload, 0, frame.payload.length);
            lastBlock = frame.block;
            complete = frame.block == totalBlocks;
            return complete ? new Message(commandId, bytes.toByteArray()) : null;
        }

        public int receivedLength() { return bytes.size(); }
        public int lastBlock() { return lastBlock; }
        public boolean isComplete() { return complete; }
        /** Original 0x19f7c60: received byte count and next block (zero when complete). */
        public byte[] acknowledgment(int requestCommandId) {
            if (lastBlock == 0) return initialRequest(requestCommandId);
            return encodeRequest(requestCommandId,
                    complete ? RequestState.PACK_COMPLETE : RequestState.PRE_PACK_RECEIVED,
                    bytes.size(), complete ? 0 : lastBlock + 1);
        }
        public void reset() {
            bytes.reset(); commandId = -1; totalLength = 0; totalBlocks = 0; lastBlock = 0; complete = false;
        }
    }

    /** Original 0x19f81ac + constructor 0x19f8528 + named toString 0x1bf7ea8. */
    public static final class RawStatus {
        public final int rawLockState, rawState12, rawState13, rawPowerState;
        public final boolean canStart, isInRange, ajarDriver, ajarCoDriver, ajarRearLeft,
                ajarRearRight, ajarTrunkLid, windowFrontLeft, windowFrontRight, windowRearLeft, windowRearRight;
        public final LockState lockState;
        public final RangeState canETGOpen, canWindowOpenClose;
        public final PowerState vehStat;
        private final byte[] bytes;
        private RawStatus(byte[] bytes) {
            this.bytes = bytes.clone(); rawLockState = unsigned(bytes[2]);
            rawState12 = unsigned(bytes[12]); rawState13 = unsigned(bytes[13]); rawPowerState = unsigned(bytes[14]);
            if (rawLockState > 2 || rawState12 > 1 || rawState13 > 1 || rawPowerState > 9)
                throw malformed("Unknown status enum value");
            canStart = flag(0); isInRange = flag(1);
            ajarDriver = flag(3); ajarCoDriver = flag(4); ajarRearLeft = flag(5); ajarRearRight = flag(6);
            ajarTrunkLid = flag(7); windowFrontLeft = flag(8); windowFrontRight = flag(9);
            windowRearLeft = flag(10); windowRearRight = flag(11);
            lockState = LockState.values()[rawLockState];
            canETGOpen = RangeState.values()[rawState12];
            canWindowOpenClose = RangeState.values()[rawState13];
            vehStat = PowerState.values()[rawPowerState];
        }
        public boolean flag(int index) {
            if (index < 0 || index > 11 || index == 2) throw malformed("Not a boolean status index");
            return bytes[index] != 0;
        }
        public byte[] bytes() { return bytes.clone(); }
    }

    public static final class SignedStatus {
        public final RawStatus status;
        public final long counter;
        private final byte[] signedData, signature;
        private SignedStatus(RawStatus status, long counter, byte[] signedData, byte[] signature) {
            this.status = status; this.counter = counter;
            this.signedData = signedData.clone(); this.signature = signature.clone();
        }
        public byte[] statusBytes() { return status.bytes(); }
        public byte[] signedData() { return signedData.clone(); }
        public byte[] signature() { return signature.clone(); }
        public boolean isNewerThan(long previousCounter) { return previousCounter >= -1 && counter > previousCounter; }
    }

    private static void requireRequestId(int id) {
        if (id < REQUEST_USER_CERTIFICATE || id > REQUEST_SAM_APP_STATUS) throw malformed("Unknown request ID");
    }
    private static void requireDataId(int id) {
        if (id < USER_CERTIFICATE_MSG || id > RAND_CHALLENGE_MSG) throw malformed("Unknown data ID");
    }
    private static void requireRange(long value, long max, String field) {
        if (value < 0 || value > max) throw malformed(field + " is out of range");
    }
    private static void requireLength(byte[] data, int length, String field) {
        if (data == null || data.length != length) throw malformed(field + " has an invalid length");
    }
    private static long le(byte[] bytes, int offset, int count) {
        long result = 0;
        for (int i = 0; i < count; i++) result |= (long) unsigned(bytes[offset + i]) << (i * 8);
        return result;
    }
    private static void putLe(byte[] bytes, int offset, long value, int count) {
        for (int i = 0; i < count; i++) bytes[offset + i] = (byte) (value >>> (i * 8));
    }
    private static int unsigned(byte value) { return value & 0xff; }
    private static IllegalArgumentException malformed(String message) { return new IllegalArgumentException(message); }
}
