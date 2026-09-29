package com.togg.companion.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Locale;

/**
 * Domain model representing a Togg Digital Key shared from the Trumore application.
 * Encapsulates Secure Element ID (seId), permissions, expiration, and authorization token.
 */
public final class DigitalKey implements Serializable {
    public final String keyId;
    public final String seId;              // Trumore reference / Secure Element ID (e.g. SE-8F4219)
    public final String vehicleVin;
    public final String vehiclePlate;
    public final String recipientName;     // Shared user name or alias
    public final long validFromMillis;
    public final long validUntilMillis;
    public final boolean canLock;
    public final boolean canUnlock;
    public final boolean canStartDrive;
    public final boolean canClimate;
    public final boolean canTrunk;
    public final String keyToken;          // Cryptographic/JWT token for vehicle authorization
    public final boolean isActive;
    public final long createdAtMillis;

    public DigitalKey(
            String keyId,
            String seId,
            String vehicleVin,
            String vehiclePlate,
            String recipientName,
            long validFromMillis,
            long validUntilMillis,
            boolean canLock,
            boolean canUnlock,
            boolean canStartDrive,
            boolean canClimate,
            boolean canTrunk,
            String keyToken,
            boolean isActive,
            long createdAtMillis) {
        this.keyId = keyId != null && !keyId.isEmpty() ? keyId : "KEY-" + System.currentTimeMillis();
        this.seId = seId != null ? seId : "";
        this.vehicleVin = vehicleVin != null ? vehicleVin : "T10F-DEV-001";
        this.vehiclePlate = vehiclePlate != null ? vehiclePlate : "34 TOGG 10";
        this.recipientName = recipientName != null ? recipientName : "Paylaşılan Kullanıcı";
        this.validFromMillis = validFromMillis > 0 ? validFromMillis : System.currentTimeMillis();
        // Default validity: 30 days if not specified
        this.validUntilMillis = validUntilMillis > 0 ? validUntilMillis : (System.currentTimeMillis() + 30L * 24 * 3600 * 1000);
        this.canLock = canLock;
        this.canUnlock = canUnlock;
        this.canStartDrive = canStartDrive;
        this.canClimate = canClimate;
        this.canTrunk = canTrunk;
        this.keyToken = keyToken != null ? keyToken : "";
        this.isActive = isActive;
        this.createdAtMillis = createdAtMillis > 0 ? createdAtMillis : System.currentTimeMillis();
    }

    public boolean isExpired() {
        return validUntilMillis > 0 && System.currentTimeMillis() > validUntilMillis;
    }

    public boolean isValid() {
        return isActive && !isExpired() && keyToken != null && !keyToken.isEmpty();
    }

    public String getRemainingTimeDisplay() {
        if (isExpired()) {
            return "Süresi Doldu";
        }
        long diff = validUntilMillis - System.currentTimeMillis();
        long days = diff / (24L * 3600 * 1000);
        if (days > 1) {
            return days + " gün kaldı";
        }
        long hours = diff / (3600 * 1000);
        if (hours > 1) {
            return hours + " saat kaldı";
        }
        long minutes = diff / (60 * 1000);
        return Math.max(1, minutes) + " dakika kaldı";
    }

    public String getPermissionsSummary() {
        StringBuilder sb = new StringBuilder();
        if (canLock && canUnlock) sb.append("Kapı Kilidi");
        else if (canLock) sb.append("Kilitleme");
        else if (canUnlock) sb.append("Kilit Açma");

        if (canTrunk) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Bagaj");
        }
        if (canClimate) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Klima");
        }
        if (canStartDrive) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Sürüş");
        }
        return sb.length() > 0 ? sb.toString() : "Temel Erişim";
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("keyId", keyId);
            obj.put("seId", seId);
            obj.put("vehicleVin", vehicleVin);
            obj.put("vehiclePlate", vehiclePlate);
            obj.put("recipientName", recipientName);
            obj.put("validFromMillis", validFromMillis);
            obj.put("validUntilMillis", validUntilMillis);
            obj.put("canLock", canLock);
            obj.put("canUnlock", canUnlock);
            obj.put("canStartDrive", canStartDrive);
            obj.put("canClimate", canClimate);
            obj.put("canTrunk", canTrunk);
            obj.put("keyToken", keyToken);
            obj.put("isActive", isActive);
            obj.put("createdAtMillis", createdAtMillis);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static DigitalKey fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new DigitalKey(
                obj.optString("keyId"),
                obj.optString("seId"),
                obj.optString("vehicleVin", "T10F-DEV-001"),
                obj.optString("vehiclePlate", "34 TOGG 10"),
                obj.optString("recipientName", "Paylaşılan Kullanıcı"),
                obj.optLong("validFromMillis", System.currentTimeMillis()),
                obj.optLong("validUntilMillis", System.currentTimeMillis() + 30L * 24 * 3600 * 1000),
                obj.optBoolean("canLock", true),
                obj.optBoolean("canUnlock", true),
                obj.optBoolean("canStartDrive", true),
                obj.optBoolean("canClimate", true),
                obj.optBoolean("canTrunk", true),
                obj.optString("keyToken", ""),
                obj.optBoolean("isActive", true),
                obj.optLong("createdAtMillis", System.currentTimeMillis())
        );
    }

    public static DigitalKey createSampleKey() {
        return new DigitalKey(
                "KEY-TRUMORE-DEMO-01",
                "SE-8F4219",
                "T10F-DEV-001",
                "34 TOGG 10",
                "Aile Üyesi / Misafir",
                System.currentTimeMillis(),
                System.currentTimeMillis() + (30L * 24 * 3600 * 1000),
                true,
                true,
                true,
                true,
                true,
                "TRUMORE_DK_TOKEN_" + System.currentTimeMillis(),
                true,
                System.currentTimeMillis()
        );
    }
}
