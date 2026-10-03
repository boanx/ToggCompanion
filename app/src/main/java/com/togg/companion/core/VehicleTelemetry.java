package com.togg.companion.core;

import java.io.Serializable;

/**
 * Immutable domain model representing snapshot of vehicle telemetry.
 * Corresponds to Togg TVA telemetry structures.
 */
public final class VehicleTelemetry implements Serializable {
    public final String vin;
    public final String plate;

    // 1. Batarya & Şarj
    public final int batterySocPercent;           // 0 - 100%
    public final int estimatedRangeKm;             // e.g. 426 km
    public final boolean isCharging;
    public final double chargePowerKw;            // e.g. 11.0 kW
    public final String chargeMode;               // "AC Şarj" / "DC Trugo Hızlı Şarj"
    public final int chargeRemainingMinutes;      // e.g. 45 dk
    public final int targetChargeLimitPercent;    // e.g. 80% veya 100%
    public final double batteryTotalCapacityKwh;  // e.g. 88.5 kWh
    public final double lastChargeEnergyKwh;      // e.g. 34.2 kWh

    // 2. 12V Destek Aküsü
    public final int lowVoltage12vPercent;        // 12V auxiliary battery SoC (%)
    public final double lowVoltage12vVolts;       // e.g. 13.2 V

    // 3. Lastikler ve TPMS
    public final double tirePressureFL;
    public final int tireTempFL;
    public final double tirePressureFR;
    public final int tireTempFR;
    public final double tirePressureRL;
    public final int tireTempRL;
    public final double tirePressureRR;
    public final int tireTempRR;
    public final boolean tpmsSensorOk;

    // 4. İklimlendirme (HVAC)
    public final double cabinTempCelsius;         // e.g. 22.0 C
    public final double targetHvacCelsius;        // e.g. 21.5 C
    public final double outdoorTempCelsius;       // e.g. 17.5 C
    public final boolean isClimateActive;
    public final boolean isDefrostActive;
    public final int seatHeaterDriver;            // 0 - 3
    public final int seatHeaterPassenger;         // 0 - 3
    public final boolean steeringHeaterActive;

    // 5. Güvenlik, Kapılar ve Erişim
    public final boolean isLocked;
    public final boolean doorOpenFL;
    public final boolean doorOpenFR;
    public final boolean doorOpenRL;
    public final boolean doorOpenRR;
    public final boolean trunkOpen;
    public final boolean frunkOpen;
    public final boolean windowsOpen;
    public final boolean chargePortOpen;
    public final boolean samConnected;
    public final boolean headlightsOn;
    public final boolean isReadyToDrive;

    // 6. Sürüş İstatistikleri
    public final long odometerKm;
    public final String driveMode;                // "Comfort", "Eco", "Sport"
    public final double lastTripDistanceKm;       // e.g. 38.4 km
    public final int lastTripDurationMin;         // e.g. 42 dk
    public final double lastTripAvgConsumption;   // e.g. 16.8 kWh/100km
    public final int lastTripAvgSpeed;            // e.g. 58 km/s

    // 6b. Üçlü Yolculuk Modeli: Güncel, Son Şarj, Yolculuğum
    public final TripData currentTrip;
    public final TripData lastChargeTrip;
    public final TripData myTrip;

    // 7. Konum ve Park
    public final double latitude;
    public final double longitude;
    public final String parkAddress;
    public final int parkDurationMinutes;

    public final long timestampMillis;

    public static class TripData implements Serializable {
        public final double distanceKm;
        public final int durationMinutes;
        public final double avgConsumption;
        public final int avgSpeed;

        public TripData(double distanceKm, int durationMinutes, double avgConsumption, int avgSpeed) {
            this.distanceKm = Math.max(0.0, distanceKm);
            this.durationMinutes = Math.max(0, durationMinutes);
            this.avgConsumption = Math.max(0.0, avgConsumption);
            this.avgSpeed = Math.max(0, avgSpeed);
        }
    }

    public VehicleTelemetry(
            String vin,
            String plate,
            int batterySocPercent,
            int estimatedRangeKm,
            boolean isCharging,
            double chargePowerKw,
            String chargeMode,
            int chargeRemainingMinutes,
            int targetChargeLimitPercent,
            double batteryTotalCapacityKwh,
            double lastChargeEnergyKwh,
            int lowVoltage12vPercent,
            double lowVoltage12vVolts,
            double tirePressureFL,
            int tireTempFL,
            double tirePressureFR,
            int tireTempFR,
            double tirePressureRL,
            int tireTempRL,
            double tirePressureRR,
            int tireTempRR,
            boolean tpmsSensorOk,
            double cabinTempCelsius,
            double targetHvacCelsius,
            double outdoorTempCelsius,
            boolean isClimateActive,
            boolean isDefrostActive,
            int seatHeaterDriver,
            int seatHeaterPassenger,
            boolean steeringHeaterActive,
            boolean isLocked,
            boolean doorOpenFL,
            boolean doorOpenFR,
            boolean doorOpenRL,
            boolean doorOpenRR,
            boolean trunkOpen,
            boolean frunkOpen,
            boolean windowsOpen,
            boolean chargePortOpen,
            boolean samConnected,
            long odometerKm,
            String driveMode,
            double lastTripDistanceKm,
            int lastTripDurationMin,
            double lastTripAvgConsumption,
            int lastTripAvgSpeed,
            double latitude,
            double longitude,
            String parkAddress,
            int parkDurationMinutes,
            long timestampMillis,
            TripData currentTrip,
            TripData lastChargeTrip,
            TripData myTrip,
            boolean headlightsOn) {
        this(vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw, chargeMode,
                chargeRemainingMinutes, targetChargeLimitPercent, batteryTotalCapacityKwh, lastChargeEnergyKwh,
                lowVoltage12vPercent, lowVoltage12vVolts, tirePressureFL, tireTempFL, tirePressureFR, tireTempFR,
                tirePressureRL, tireTempRL, tirePressureRR, tireTempRR, tpmsSensorOk, cabinTempCelsius,
                targetHvacCelsius, outdoorTempCelsius, isClimateActive, isDefrostActive, seatHeaterDriver,
                seatHeaterPassenger, steeringHeaterActive, isLocked, doorOpenFL, doorOpenFR, doorOpenRL,
                doorOpenRR, trunkOpen, frunkOpen, windowsOpen, chargePortOpen, samConnected, odometerKm,
                driveMode, lastTripDistanceKm, lastTripDurationMin, lastTripAvgConsumption, lastTripAvgSpeed,
                latitude, longitude, parkAddress, parkDurationMinutes, timestampMillis, currentTrip,
                lastChargeTrip, myTrip, headlightsOn, false);
    }

    public VehicleTelemetry(String vin, String plate, int batterySocPercent, int estimatedRangeKm,
            boolean isCharging, double chargePowerKw, String chargeMode, int chargeRemainingMinutes,
            int targetChargeLimitPercent, double batteryTotalCapacityKwh, double lastChargeEnergyKwh,
            int lowVoltage12vPercent, double lowVoltage12vVolts, double tirePressureFL, int tireTempFL,
            double tirePressureFR, int tireTempFR, double tirePressureRL, int tireTempRL,
            double tirePressureRR, int tireTempRR, boolean tpmsSensorOk, double cabinTempCelsius,
            double targetHvacCelsius, double outdoorTempCelsius, boolean isClimateActive,
            boolean isDefrostActive, int seatHeaterDriver, int seatHeaterPassenger, boolean steeringHeaterActive,
            boolean isLocked, boolean doorOpenFL, boolean doorOpenFR, boolean doorOpenRL, boolean doorOpenRR,
            boolean trunkOpen, boolean frunkOpen, boolean windowsOpen, boolean chargePortOpen, boolean samConnected,
            long odometerKm, String driveMode, double lastTripDistanceKm, int lastTripDurationMin,
            double lastTripAvgConsumption, int lastTripAvgSpeed, double latitude, double longitude,
            String parkAddress, int parkDurationMinutes, long timestampMillis, TripData currentTrip,
            TripData lastChargeTrip, TripData myTrip, boolean headlightsOn, boolean isReadyToDrive) {
        this.vin = vin != null ? vin : "T10F-DEV-001";
        this.plate = plate != null ? plate : "34 TOGG 10";
        this.batterySocPercent = Math.max(0, Math.min(100, batterySocPercent));
        this.estimatedRangeKm = Math.max(0, estimatedRangeKm);
        this.isCharging = isCharging;
        this.chargePowerKw = Math.max(0.0, chargePowerKw);
        this.chargeMode = chargeMode != null ? chargeMode : (isCharging ? "AC Şarj (11 kW)" : "Bağlı Değil");
        this.chargeRemainingMinutes = Math.max(0, chargeRemainingMinutes);
        this.targetChargeLimitPercent = Math.max(50, Math.min(100, targetChargeLimitPercent > 0 ? targetChargeLimitPercent : 80));
        this.batteryTotalCapacityKwh = batteryTotalCapacityKwh > 0 ? batteryTotalCapacityKwh : 88.5;
        this.lastChargeEnergyKwh = Math.max(0.0, lastChargeEnergyKwh);
        this.lowVoltage12vPercent = Math.max(0, Math.min(100, lowVoltage12vPercent));
        this.lowVoltage12vVolts = lowVoltage12vVolts > 0 ? lowVoltage12vVolts : 13.2;
        this.tirePressureFL = tirePressureFL;
        this.tireTempFL = tireTempFL;
        this.tirePressureFR = tirePressureFR;
        this.tireTempFR = tireTempFR;
        this.tirePressureRL = tirePressureRL;
        this.tireTempRL = tireTempRL;
        this.tirePressureRR = tirePressureRR;
        this.tireTempRR = tireTempRR;
        this.tpmsSensorOk = tpmsSensorOk;
        this.cabinTempCelsius = cabinTempCelsius;
        this.targetHvacCelsius = targetHvacCelsius;
        this.outdoorTempCelsius = outdoorTempCelsius;
        this.isClimateActive = isClimateActive;
        this.isDefrostActive = isDefrostActive;
        this.seatHeaterDriver = seatHeaterDriver;
        this.seatHeaterPassenger = seatHeaterPassenger;
        this.steeringHeaterActive = steeringHeaterActive;
        this.isLocked = isLocked;
        this.doorOpenFL = doorOpenFL;
        this.doorOpenFR = doorOpenFR;
        this.doorOpenRL = doorOpenRL;
        this.doorOpenRR = doorOpenRR;
        this.trunkOpen = trunkOpen;
        this.frunkOpen = frunkOpen;
        this.windowsOpen = windowsOpen;
        this.chargePortOpen = chargePortOpen;
        this.samConnected = samConnected;
        this.headlightsOn = headlightsOn;
        this.isReadyToDrive = isReadyToDrive;
        this.odometerKm = Math.max(0, odometerKm);
        this.driveMode = driveMode != null ? driveMode : "Comfort";
        this.lastTripDistanceKm = Math.max(0.0, lastTripDistanceKm);
        this.lastTripDurationMin = Math.max(0, lastTripDurationMin);
        this.lastTripAvgConsumption = Math.max(0.0, lastTripAvgConsumption);
        this.lastTripAvgSpeed = Math.max(0, lastTripAvgSpeed);
        this.latitude = latitude;
        this.longitude = longitude;
        this.parkAddress = parkAddress != null ? parkAddress : "Aracın konumu belirleniyor...";
        this.parkDurationMinutes = Math.max(0, parkDurationMinutes);
        this.timestampMillis = timestampMillis;

        this.currentTrip = currentTrip != null ? currentTrip :
                new TripData(this.lastTripDistanceKm, this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed);
        this.lastChargeTrip = lastChargeTrip != null ? lastChargeTrip :
                new TripData(85.0, 110, 17.2, 54);
        this.myTrip = myTrip != null ? myTrip :
                new TripData(this.odometerKm, 14500, 17.8, 56);
    }

    public VehicleTelemetry(
            String vin,
            String plate,
            int batterySocPercent,
            int estimatedRangeKm,
            boolean isCharging,
            double chargePowerKw,
            String chargeMode,
            int chargeRemainingMinutes,
            int targetChargeLimitPercent,
            double batteryTotalCapacityKwh,
            double lastChargeEnergyKwh,
            int lowVoltage12vPercent,
            double lowVoltage12vVolts,
            double tirePressureFL,
            int tireTempFL,
            double tirePressureFR,
            int tireTempFR,
            double tirePressureRL,
            int tireTempRL,
            double tirePressureRR,
            int tireTempRR,
            boolean tpmsSensorOk,
            double cabinTempCelsius,
            double targetHvacCelsius,
            double outdoorTempCelsius,
            boolean isClimateActive,
            boolean isDefrostActive,
            int seatHeaterDriver,
            int seatHeaterPassenger,
            boolean steeringHeaterActive,
            boolean isLocked,
            boolean doorOpenFL,
            boolean doorOpenFR,
            boolean doorOpenRL,
            boolean doorOpenRR,
            boolean trunkOpen,
            boolean frunkOpen,
            boolean windowsOpen,
            boolean chargePortOpen,
            boolean samConnected,
            long odometerKm,
            String driveMode,
            double lastTripDistanceKm,
            int lastTripDurationMin,
            double lastTripAvgConsumption,
            int lastTripAvgSpeed,
            double latitude,
            double longitude,
            String parkAddress,
            int parkDurationMinutes,
            long timestampMillis,
            TripData currentTrip,
            TripData lastChargeTrip,
            TripData myTrip) {
        this(
                vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw,
                chargeMode, chargeRemainingMinutes, targetChargeLimitPercent, batteryTotalCapacityKwh,
                lastChargeEnergyKwh, lowVoltage12vPercent, lowVoltage12vVolts,
                tirePressureFL, tireTempFL, tirePressureFR, tireTempFR,
                tirePressureRL, tireTempRL, tirePressureRR, tireTempRR,
                tpmsSensorOk, cabinTempCelsius, targetHvacCelsius, outdoorTempCelsius,
                isClimateActive, isDefrostActive, seatHeaterDriver, seatHeaterPassenger,
                steeringHeaterActive, isLocked, doorOpenFL, doorOpenFR, doorOpenRL,
                doorOpenRR, trunkOpen, frunkOpen, windowsOpen, chargePortOpen,
                samConnected, odometerKm, driveMode, lastTripDistanceKm,
                lastTripDurationMin, lastTripAvgConsumption, lastTripAvgSpeed,
                latitude, longitude, parkAddress, parkDurationMinutes,
                timestampMillis, currentTrip, lastChargeTrip, myTrip, false
        );
    }

    public VehicleTelemetry(
            String vin,
            String plate,
            int batterySocPercent,
            int estimatedRangeKm,
            boolean isCharging,
            double chargePowerKw,
            String chargeMode,
            int chargeRemainingMinutes,
            int targetChargeLimitPercent,
            double batteryTotalCapacityKwh,
            double lastChargeEnergyKwh,
            int lowVoltage12vPercent,
            double lowVoltage12vVolts,
            double tirePressureFL,
            int tireTempFL,
            double tirePressureFR,
            int tireTempFR,
            double tirePressureRL,
            int tireTempRL,
            double tirePressureRR,
            int tireTempRR,
            boolean tpmsSensorOk,
            double cabinTempCelsius,
            double targetHvacCelsius,
            double outdoorTempCelsius,
            boolean isClimateActive,
            boolean isDefrostActive,
            int seatHeaterDriver,
            int seatHeaterPassenger,
            boolean steeringHeaterActive,
            boolean isLocked,
            boolean doorOpenFL,
            boolean doorOpenFR,
            boolean doorOpenRL,
            boolean doorOpenRR,
            boolean trunkOpen,
            boolean frunkOpen,
            boolean windowsOpen,
            boolean chargePortOpen,
            boolean samConnected,
            long odometerKm,
            String driveMode,
            double lastTripDistanceKm,
            int lastTripDurationMin,
            double lastTripAvgConsumption,
            int lastTripAvgSpeed,
            double latitude,
            double longitude,
            String parkAddress,
            int parkDurationMinutes,
            long timestampMillis) {
        this(
                vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw,
                chargeMode, chargeRemainingMinutes, targetChargeLimitPercent, batteryTotalCapacityKwh,
                lastChargeEnergyKwh, lowVoltage12vPercent, lowVoltage12vVolts,
                tirePressureFL, tireTempFL, tirePressureFR, tireTempFR,
                tirePressureRL, tireTempRL, tirePressureRR, tireTempRR,
                tpmsSensorOk, cabinTempCelsius, targetHvacCelsius, outdoorTempCelsius,
                isClimateActive, isDefrostActive, seatHeaterDriver, seatHeaterPassenger,
                steeringHeaterActive, isLocked, doorOpenFL, doorOpenFR, doorOpenRL,
                doorOpenRR, trunkOpen, frunkOpen, windowsOpen, chargePortOpen,
                samConnected, odometerKm, driveMode, lastTripDistanceKm,
                lastTripDurationMin, lastTripAvgConsumption, lastTripAvgSpeed,
                latitude, longitude, parkAddress, parkDurationMinutes,
                timestampMillis, null, null, null, false
        );
    }

    /**
     * SQLite persistence constructor supporting all columns including location & controls.
     */
    public VehicleTelemetry(
            String vin,
            String plate,
            int batterySocPercent,
            int estimatedRangeKm,
            boolean isCharging,
            double chargePowerKw,
            int lowVoltage12vPercent,
            double cabinTempCelsius,
            double targetHvacCelsius,
            double outdoorTempCelsius,
            double tirePressureFL,
            int tireTempFL,
            double tirePressureFR,
            int tireTempFR,
            double tirePressureRL,
            int tireTempRL,
            double tirePressureRR,
            int tireTempRR,
            long odometerKm,
            boolean isLocked,
            double latitude,
            double longitude,
            String parkAddress,
            boolean trunkOpen,
            String driveMode,
            boolean headlightsOn,
            long timestampMillis) {
        this(vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw, lowVoltage12vPercent,
                cabinTempCelsius, targetHvacCelsius, outdoorTempCelsius, tirePressureFL, tireTempFL,
                tirePressureFR, tireTempFR, tirePressureRL, tireTempRL, tirePressureRR, tireTempRR,
                odometerKm, isLocked, latitude, longitude, parkAddress, trunkOpen, driveMode, headlightsOn,
                false, timestampMillis);
    }

    public VehicleTelemetry(String vin, String plate, int batterySocPercent, int estimatedRangeKm,
            boolean isCharging, double chargePowerKw, int lowVoltage12vPercent, double cabinTempCelsius,
            double targetHvacCelsius, double outdoorTempCelsius, double tirePressureFL, int tireTempFL,
            double tirePressureFR, int tireTempFR, double tirePressureRL, int tireTempRL,
            double tirePressureRR, int tireTempRR, long odometerKm, boolean isLocked, double latitude,
            double longitude, String parkAddress, boolean trunkOpen, String driveMode, boolean headlightsOn,
            boolean isReadyToDrive, long timestampMillis) {
        this(
                vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw,
                isCharging ? "AC Şarj (11 kW)" : "Bağlı Değil", 0, 80, 88.5, 0.0,
                lowVoltage12vPercent, 13.2,
                tirePressureFL, tireTempFL, tirePressureFR, tireTempFR,
                tirePressureRL, tireTempRL, tirePressureRR, tireTempRR, true,
                cabinTempCelsius, targetHvacCelsius, outdoorTempCelsius, false, false,
                0, 0, false,
                isLocked, false, false, false, false, trunkOpen, false, false, false, true,
                odometerKm, driveMode != null ? driveMode : "Comfort", 0.0, 0, 0.0, 0,
                latitude, longitude, parkAddress != null ? parkAddress : "Aracın konumu belirleniyor...", 0,
                timestampMillis, null, null, null, headlightsOn, isReadyToDrive
        );
    }

    /**
     * Backwards-compatible constructor for legacy tests and calls.
     */
    public VehicleTelemetry(
            String vin,
            String plate,
            int batterySocPercent,
            int estimatedRangeKm,
            boolean isCharging,
            double chargePowerKw,
            int lowVoltage12vPercent,
            double cabinTempCelsius,
            double targetHvacCelsius,
            double outdoorTempCelsius,
            double tirePressureFL,
            int tireTempFL,
            double tirePressureFR,
            int tireTempFR,
            double tirePressureRL,
            int tireTempRL,
            double tirePressureRR,
            int tireTempRR,
            long odometerKm,
            boolean isLocked,
            long timestampMillis) {
        this(
                vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw,
                lowVoltage12vPercent, cabinTempCelsius, targetHvacCelsius, outdoorTempCelsius,
                tirePressureFL, tireTempFL, tirePressureFR, tireTempFR,
                tirePressureRL, tireTempRL, tirePressureRR, tireTempRR,
                odometerKm, isLocked, 0.0, 0.0, "Aracın konumu belirleniyor...", false, "Comfort", false,
                timestampMillis
        );
    }

    public VehicleTelemetry withLock(boolean locked) {
        return new VehicleTelemetry(
                this.vin, this.plate, this.batterySocPercent, this.estimatedRangeKm,
                this.isCharging, this.chargePowerKw, this.chargeMode, this.chargeRemainingMinutes,
                this.targetChargeLimitPercent, this.batteryTotalCapacityKwh, this.lastChargeEnergyKwh,
                this.lowVoltage12vPercent, this.lowVoltage12vVolts,
                this.tirePressureFL, this.tireTempFL, this.tirePressureFR, this.tireTempFR,
                this.tirePressureRL, this.tireTempRL, this.tirePressureRR, this.tireTempRR,
                this.tpmsSensorOk, this.cabinTempCelsius, this.targetHvacCelsius, this.outdoorTempCelsius,
                this.isClimateActive, this.isDefrostActive, this.seatHeaterDriver, this.seatHeaterPassenger,
                this.steeringHeaterActive, locked, this.doorOpenFL, this.doorOpenFR, this.doorOpenRL,
                this.doorOpenRR, this.trunkOpen, this.frunkOpen, this.windowsOpen, this.chargePortOpen,
                this.samConnected, this.odometerKm, this.driveMode, this.lastTripDistanceKm,
                this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed,
                this.latitude, this.longitude, this.parkAddress, this.parkDurationMinutes,
                this.timestampMillis, this.currentTrip, this.lastChargeTrip, this.myTrip,
                this.headlightsOn, this.isReadyToDrive
        );
    }

    public VehicleTelemetry withDriveMode(String driveMode) {
        return new VehicleTelemetry(
                this.vin, this.plate, this.batterySocPercent, this.estimatedRangeKm,
                this.isCharging, this.chargePowerKw, this.chargeMode, this.chargeRemainingMinutes,
                this.targetChargeLimitPercent, this.batteryTotalCapacityKwh, this.lastChargeEnergyKwh,
                this.lowVoltage12vPercent, this.lowVoltage12vVolts,
                this.tirePressureFL, this.tireTempFL, this.tirePressureFR, this.tireTempFR,
                this.tirePressureRL, this.tireTempRL, this.tirePressureRR, this.tireTempRR,
                this.tpmsSensorOk, this.cabinTempCelsius, this.targetHvacCelsius, this.outdoorTempCelsius,
                this.isClimateActive, this.isDefrostActive, this.seatHeaterDriver, this.seatHeaterPassenger,
                this.steeringHeaterActive, this.isLocked, this.doorOpenFL, this.doorOpenFR, this.doorOpenRL,
                this.doorOpenRR, this.trunkOpen, this.frunkOpen, this.windowsOpen, this.chargePortOpen,
                this.samConnected, this.odometerKm, driveMode, this.lastTripDistanceKm,
                this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed,
                this.latitude, this.longitude, this.parkAddress, this.parkDurationMinutes,
                this.timestampMillis, this.currentTrip, this.lastChargeTrip, this.myTrip,
                this.headlightsOn, this.isReadyToDrive
        );
    }

    public VehicleTelemetry withTrunk(boolean trunk) {
        return new VehicleTelemetry(
                this.vin, this.plate, this.batterySocPercent, this.estimatedRangeKm,
                this.isCharging, this.chargePowerKw, this.chargeMode, this.chargeRemainingMinutes,
                this.targetChargeLimitPercent, this.batteryTotalCapacityKwh, this.lastChargeEnergyKwh,
                this.lowVoltage12vPercent, this.lowVoltage12vVolts,
                this.tirePressureFL, this.tireTempFL, this.tirePressureFR, this.tireTempFR,
                this.tirePressureRL, this.tireTempRL, this.tirePressureRR, this.tireTempRR,
                this.tpmsSensorOk, this.cabinTempCelsius, this.targetHvacCelsius, this.outdoorTempCelsius,
                this.isClimateActive, this.isDefrostActive, this.seatHeaterDriver, this.seatHeaterPassenger,
                this.steeringHeaterActive, this.isLocked, this.doorOpenFL, this.doorOpenFR, this.doorOpenRL,
                this.doorOpenRR, trunk, this.frunkOpen, this.windowsOpen, this.chargePortOpen,
                this.samConnected, this.odometerKm, this.driveMode, this.lastTripDistanceKm,
                this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed,
                this.latitude, this.longitude, this.parkAddress, this.parkDurationMinutes,
                this.timestampMillis, this.currentTrip, this.lastChargeTrip, this.myTrip,
                this.headlightsOn, this.isReadyToDrive
        );
    }

    public VehicleTelemetry withClimate(double targetHvac, boolean climateActive, boolean defrostActive) {
        return new VehicleTelemetry(
                this.vin, this.plate, this.batterySocPercent, this.estimatedRangeKm,
                this.isCharging, this.chargePowerKw, this.chargeMode, this.chargeRemainingMinutes,
                this.targetChargeLimitPercent, this.batteryTotalCapacityKwh, this.lastChargeEnergyKwh,
                this.lowVoltage12vPercent, this.lowVoltage12vVolts,
                this.tirePressureFL, this.tireTempFL, this.tirePressureFR, this.tireTempFR,
                this.tirePressureRL, this.tireTempRL, this.tirePressureRR, this.tireTempRR,
                this.tpmsSensorOk, this.cabinTempCelsius, targetHvac, this.outdoorTempCelsius,
                climateActive, defrostActive, this.seatHeaterDriver, this.seatHeaterPassenger,
                this.steeringHeaterActive, this.isLocked, this.doorOpenFL, this.doorOpenFR, this.doorOpenRL,
                this.doorOpenRR, this.trunkOpen, this.frunkOpen, this.windowsOpen, this.chargePortOpen,
                this.samConnected, this.odometerKm, this.driveMode, this.lastTripDistanceKm,
                this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed,
                this.latitude, this.longitude, this.parkAddress, this.parkDurationMinutes,
                this.timestampMillis, this.currentTrip, this.lastChargeTrip, this.myTrip,
                this.headlightsOn, this.isReadyToDrive
        );
    }

    public VehicleTelemetry withHeadlights(boolean headlights) {
        return new VehicleTelemetry(
                this.vin, this.plate, this.batterySocPercent, this.estimatedRangeKm,
                this.isCharging, this.chargePowerKw, this.chargeMode, this.chargeRemainingMinutes,
                this.targetChargeLimitPercent, this.batteryTotalCapacityKwh, this.lastChargeEnergyKwh,
                this.lowVoltage12vPercent, this.lowVoltage12vVolts,
                this.tirePressureFL, this.tireTempFL, this.tirePressureFR, this.tireTempFR,
                this.tirePressureRL, this.tireTempRL, this.tirePressureRR, this.tireTempRR,
                this.tpmsSensorOk, this.cabinTempCelsius, this.targetHvacCelsius, this.outdoorTempCelsius,
                this.isClimateActive, this.isDefrostActive, this.seatHeaterDriver, this.seatHeaterPassenger,
                this.steeringHeaterActive, this.isLocked, this.doorOpenFL, this.doorOpenFR, this.doorOpenRL,
                this.doorOpenRR, this.trunkOpen, this.frunkOpen, this.windowsOpen, this.chargePortOpen,
                this.samConnected, this.odometerKm, this.driveMode, this.lastTripDistanceKm,
                this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed,
                this.latitude, this.longitude, this.parkAddress, this.parkDurationMinutes,
                this.timestampMillis, this.currentTrip, this.lastChargeTrip, this.myTrip,
                headlights, this.isReadyToDrive
        );
    }

    public VehicleTelemetry withLocation(double lat, double lon, String address) {
        return new VehicleTelemetry(
                this.vin, this.plate, this.batterySocPercent, this.estimatedRangeKm,
                this.isCharging, this.chargePowerKw, this.chargeMode, this.chargeRemainingMinutes,
                this.targetChargeLimitPercent, this.batteryTotalCapacityKwh, this.lastChargeEnergyKwh,
                this.lowVoltage12vPercent, this.lowVoltage12vVolts,
                this.tirePressureFL, this.tireTempFL, this.tirePressureFR, this.tireTempFR,
                this.tirePressureRL, this.tireTempRL, this.tirePressureRR, this.tireTempRR,
                this.tpmsSensorOk, this.cabinTempCelsius, this.targetHvacCelsius, this.outdoorTempCelsius,
                this.isClimateActive, this.isDefrostActive, this.seatHeaterDriver, this.seatHeaterPassenger,
                this.steeringHeaterActive, this.isLocked, this.doorOpenFL, this.doorOpenFR, this.doorOpenRL,
                this.doorOpenRR, this.trunkOpen, this.frunkOpen, this.windowsOpen, this.chargePortOpen,
                this.samConnected, this.odometerKm, this.driveMode, this.lastTripDistanceKm,
                this.lastTripDurationMin, this.lastTripAvgConsumption, this.lastTripAvgSpeed,
                lat, lon, address != null ? address : this.parkAddress, this.parkDurationMinutes,
                this.timestampMillis, this.currentTrip, this.lastChargeTrip, this.myTrip,
                this.headlightsOn, this.isReadyToDrive
        );
    }

    public VehicleTelemetry withReadyToDrive(boolean ready) {
        return new VehicleTelemetry(vin, plate, batterySocPercent, estimatedRangeKm, isCharging, chargePowerKw,
                chargeMode, chargeRemainingMinutes, targetChargeLimitPercent, batteryTotalCapacityKwh,
                lastChargeEnergyKwh, lowVoltage12vPercent, lowVoltage12vVolts, tirePressureFL, tireTempFL,
                tirePressureFR, tireTempFR, tirePressureRL, tireTempRL, tirePressureRR, tireTempRR,
                tpmsSensorOk, cabinTempCelsius, targetHvacCelsius, outdoorTempCelsius, isClimateActive,
                isDefrostActive, seatHeaterDriver, seatHeaterPassenger, steeringHeaterActive, isLocked,
                doorOpenFL, doorOpenFR, doorOpenRL, doorOpenRR, trunkOpen, frunkOpen, windowsOpen,
                chargePortOpen, samConnected, odometerKm, driveMode, lastTripDistanceKm, lastTripDurationMin,
                lastTripAvgConsumption, lastTripAvgSpeed, latitude, longitude, parkAddress, parkDurationMinutes,
                timestampMillis, currentTrip, lastChargeTrip, myTrip, headlightsOn, ready);
    }

    public boolean is12vBatteryLow() {
        return lowVoltage12vPercent < 60;
    }

    public boolean isAnyTireLow() {
        return tirePressureFL < 2.2 || tirePressureFR < 2.2 || tirePressureRL < 2.2 || tirePressureRR < 2.2;
    }

    public boolean isAnyDoorOpen() {
        return doorOpenFL || doorOpenFR || doorOpenRL || doorOpenRR;
    }

    public static double barToPsi(double bar) {
        if (bar <= 0) return 0.0;
        // If value is already in psi (e.g. > 15), return as-is
        if (bar > 15.0) return bar;
        return bar * 14.50377;
    }

    public double getTirePressurePsiFL() {
        return barToPsi(tirePressureFL);
    }

    public double getTirePressurePsiFR() {
        return barToPsi(tirePressureFR);
    }

    public double getTirePressurePsiRL() {
        return barToPsi(tirePressureRL);
    }

    public double getTirePressurePsiRR() {
        return barToPsi(tirePressureRR);
    }
}
