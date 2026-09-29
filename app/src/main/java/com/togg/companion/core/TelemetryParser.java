package com.togg.companion.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Parses JSON payloads returned by Togg TVA (Telematics & Vehicle Access) endpoints
 * into strongly-typed VehicleTelemetry instances.
 *
 * Implements full support for Trumore 2.1.25 DeviceInformationResponse data schema:
 *   - DeviceInformationResponse -> telemetry -> battery -> content -> batteryStateOfCharge -> batteryStateOfChargeValue
 *   - DeviceInformationResponse -> telemetry -> vehicleRange -> content -> estRange -> range
 *   - DeviceInformationResponse -> telemetry -> batteryPowerStatus -> batteryPowerStatusContent -> lowVoltageStateOfChargeValue
 *   - DeviceInformationResponse -> telemetry -> tirePressure -> content -> frontLeft/frontRight/rearLeft/rearRight -> ...Value
 *   - DeviceInformationResponse -> telemetry -> tireTemperature -> tireTemperatureContent -> ...TempValue
 *   - DeviceInformationResponse -> telemetry -> odometerStatus -> odometerStatusContent -> odometer -> odometerValue
 *   - DeviceInformationResponse -> telemetry -> obcState -> content -> chargeMode -> chargeModeEnumValue
 *   - DeviceInformationResponse -> telemetry -> vehicleStatus -> content -> vehicleStat -> vehicleStatusValueEnum
 *   - DeviceInformationResponse -> telemetry -> driveMode -> content -> driveModeState -> driveMode
 *   - DeviceInformationResponse -> telemetry -> location -> content -> latitude/longitude -> ...Value
 *   - DeviceInformationResponse -> lastTripInfo -> currentTrip / myTrip / lastChargeTrip
 */
public final class TelemetryParser {

    public static VehicleTelemetry parse(String jsonString, String defaultVin, String defaultPlate) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return createDemoSnapshot(defaultVin, defaultPlate);
        }

        try {
            JSONObject root = new JSONObject(jsonString);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                data = root;
            }

            JSONObject telem = data.optJSONObject("telemetry");
            if (telem == null) {
                telem = data;
            }

            // VIN & Plate
            String vin = findString(data, defaultVin != null ? defaultVin : "NL1CFB0P800100522", "vin", "vinNumber", "vehicleId");
            String plate = findString(data, defaultPlate != null ? defaultPlate : "06GDU258", "plate", "licensePlate");

            // Sub-objects in telemetry
            JSONObject batteryObj = telem.optJSONObject("battery");
            JSONObject battPowerObj = telem.optJSONObject("batteryPowerStatus");
            JSONObject rangeObj = telem.optJSONObject("vehicleRange");
            JSONObject tpmsObj = telem.optJSONObject("tirePressure");
            JSONObject tpmsSensorsObj = telem.optJSONObject("tirePressureSensors");
            JSONObject ttObj = telem.optJSONObject("tireTemperature");
            JSONObject vehStatusObj = telem.optJSONObject("vehicleStatus");
            JSONObject odoObj = telem.optJSONObject("odometerStatus");
            JSONObject obcObj = telem.optJSONObject("obcState");
            JSONObject driveModeObj = telem.optJSONObject("driveMode");
            JSONObject locObj = telem.optJSONObject("location");
            JSONObject tripObj = data.optJSONObject("lastTripInfo");

            // 1. Batarya & Şarj
            // Real Togg schema: battery -> content -> batteryStateOfCharge -> batteryStateOfChargeValue
            double socDouble = findDouble(batteryObj, -1.0,
                    "batteryStateOfChargeValue", "batteryStateOfCharge", "soc", "batterySoc", "value");
            if (socDouble < 0) {
                socDouble = findDouble(telem, 82.0, "batteryStateOfChargeValue", "batterySoc", "soc");
            }
            int batterySoc = (int) Math.round(socDouble);

            // Estimated Range: vehicleRange -> content -> estRange -> range
            double rangeDouble = findDouble(rangeObj, -1.0,
                    "range", "estimatedRange", "estRange", "vehicleRange", "value");
            if (rangeDouble < 0) {
                rangeDouble = findDouble(telem, 426.0, "estimatedRange", "range");
            }
            int rangeKm = (int) Math.round(rangeDouble);

            // Charging State & Mode: obcState -> content -> chargeMode -> chargeModeEnumValue
            String chargeModeStr = findString(obcObj, null, "chargeModeEnumValue", "chargeMode", "chargingMode");
            if (chargeModeStr == null) {
                chargeModeStr = findString(telem, "Bağlı Değil", "chargeMode", "chargingMode");
            }

            boolean isCharging = false;
            double chargePowerKw = 0.0;
            String chargeModeDisplay = "Bağlı Değil";

            String cmUpper = chargeModeStr.toUpperCase();
            if (cmUpper.contains("DC") || cmUpper.contains("TRUGO") || cmUpper.contains("FAST") || cmUpper.contains("HIZLI")) {
                isCharging = true;
                chargePowerKw = findDouble(telem, 120.0, "chargePowerKw", "chargingPower", "power");
                chargeModeDisplay = "DC Hızlı Şarj (Trugo)";
            } else if (cmUpper.contains("AC") || cmUpper.contains("SLOW") || cmUpper.contains("YAVAS")) {
                isCharging = true;
                chargePowerKw = findDouble(telem, 11.0, "chargePowerKw", "chargingPower", "power");
                chargeModeDisplay = "AC Şarj (11 kW)";
            } else if (cmUpper.contains("CHARG") && !cmUpper.contains("NOT") && !cmUpper.contains("NO_")) {
                isCharging = true;
                chargePowerKw = findDouble(telem, 11.0, "chargePowerKw", "chargingPower", "power");
                chargeModeDisplay = "Şarj Oluyor (" + String.format(Locale.US, "%.1f kW", chargePowerKw) + ")";
            }

            // Allow direct isCharging boolean override if present
            Boolean directCharging = findBoolean(telem, null, "isCharging", "chargingStatus", "charging");
            if (directCharging != null) {
                isCharging = directCharging;
                if (!isCharging) {
                    chargeModeDisplay = "Bağlı Değil";
                    chargePowerKw = 0.0;
                } else if (chargePowerKw == 0.0) {
                    chargePowerKw = findDouble(telem, 11.0, "chargePowerKw", "chargingPower", "power");
                    chargeModeDisplay = "Şarj Oluyor (" + String.format(Locale.US, "%.1f kW", chargePowerKw) + ")";
                }
            }

            int chargeRemainingMin = (int) Math.round(findDouble(telem, isCharging ? 45.0 : 0.0, "chargeRemainingMinutes", "remainingChargingTime"));
            int targetChargeLimit = (int) Math.round(findDouble(telem, 80.0, "targetChargeLimitPercent", "targetSoc"));
            double batteryCapacity = findDouble(batteryObj, 88.5, "batteryTotalEnergyValue", "batteryTotalEnergy", "totalCapacity", "capacity");
            double lastChargeKwh = findDouble(data, 34.2, "lastChargeEnergyKwh", "lastChargeKwh", "energyConsumed");

            // 2. 12V Destek Aküsü
            // Real Togg schema: batteryPowerStatus -> batteryPowerStatusContent -> lowVoltageStateOfChargeValue -> lowVoltageStateOfChargeValue
            double lvSoc = findDouble(battPowerObj, -1.0, "lowVoltageStateOfChargeValue", "lowVoltageSoc", "auxBatterySoc");
            if (lvSoc < 0) {
                lvSoc = findDouble(vehStatusObj, -1.0, "lowVoltageStateOfChargeValue", "lowVoltageSoc");
            }
            if (lvSoc < 0) {
                lvSoc = findDouble(telem, 94.0, "lowVoltageSoc", "lowVoltageStateOfChargeValue");
            }
            int lowVoltageSoc = (int) Math.round(lvSoc);

            double lowVoltageVolts = findDouble(battPowerObj, -1.0, "lowVoltageVolts", "lowVoltageVoltage", "auxBatteryVolts", "voltage");
            if (lowVoltageVolts < 0) {
                lowVoltageVolts = findDouble(vehStatusObj, -1.0, "lowVoltageVolts", "lowVoltageVoltage");
            }
            if (lowVoltageVolts < 0) {
                lowVoltageVolts = findDouble(telem, 13.4, "lowVoltageVolts", "lowVoltageVoltage");
            }

            // 3. Lastikler ve TPMS
            // Real Togg schema: tirePressure -> content -> frontLeft -> frontLeftValue
            JSONObject tpContent = tpmsObj != null ? tpmsObj.optJSONObject("content") : null;
            if (tpContent == null && tpmsObj != null) tpContent = tpmsObj;

            double rawFL = findDouble(tpContent != null ? tpContent.optJSONObject("frontLeft") : null, -1.0, "frontLeftValue", "value", "pressure");
            if (rawFL < 0) rawFL = findDouble(tpmsObj, 2.5, "frontLeftValue", "frontLeft", "fl");

            double rawFR = findDouble(tpContent != null ? tpContent.optJSONObject("frontRight") : null, -1.0, "frontRightValue", "value", "pressure");
            if (rawFR < 0) rawFR = findDouble(tpmsObj, 2.5, "frontRightValue", "frontRight", "fr");

            double rawRL = findDouble(tpContent != null ? tpContent.optJSONObject("rearLeft") : null, -1.0, "rearLeftValue", "value", "pressure");
            if (rawRL < 0) rawRL = findDouble(tpmsObj, 2.6, "rearLeftValue", "rearLeft", "rl");

            double rawRR = findDouble(tpContent != null ? tpContent.optJSONObject("rearRight") : null, -1.0, "rearRightValue", "value", "pressure");
            if (rawRR < 0) rawRR = findDouble(tpmsObj, 2.6, "rearRightValue", "rearRight", "rr");

            double tpFL = normalizePressureToBar(rawFL);
            double tpFR = normalizePressureToBar(rawFR);
            double tpRL = normalizePressureToBar(rawRL);
            double tpRR = normalizePressureToBar(rawRR);

            // Tire Temperatures: tireTemperature -> tireTemperatureContent -> frontLeftTireTemp -> frontLeftTireTempValue
            JSONObject ttContent = ttObj != null ? ttObj.optJSONObject("tireTemperatureContent") : null;
            if (ttContent == null && ttObj != null) ttContent = ttObj.optJSONObject("content");
            if (ttContent == null && ttObj != null) ttContent = ttObj;

            int ttFL = (int) Math.round(findDouble(ttContent != null ? ttContent.optJSONObject("frontLeftTireTemp") : null, -1.0, "frontLeftTireTempValue", "value", "temp"));
            if (ttFL < 0) ttFL = (int) Math.round(findDouble(ttObj, 23.0, "frontLeftTireTempValue", "frontLeftTireTemp", "fl"));

            int ttFR = (int) Math.round(findDouble(ttContent != null ? ttContent.optJSONObject("frontRightTireTemp") : null, -1.0, "frontRightTireTempValue", "value", "temp"));
            if (ttFR < 0) ttFR = (int) Math.round(findDouble(ttObj, 23.0, "frontRightTireTempValue", "frontRightTireTemp", "fr"));

            int ttRL = (int) Math.round(findDouble(ttContent != null ? ttContent.optJSONObject("rearLeftTireTemp") : null, -1.0, "rearLeftTireTempValue", "value", "temp"));
            if (ttRL < 0) ttRL = (int) Math.round(findDouble(ttObj, 24.0, "rearLeftTireTempValue", "rearLeftTireTemp", "rl"));

            int ttRR = (int) Math.round(findDouble(ttContent != null ? ttContent.optJSONObject("rearRightTireTemp") : null, -1.0, "rearRightTireTempValue", "value", "temp"));
            if (ttRR < 0) ttRR = (int) Math.round(findDouble(ttObj, 24.0, "rearRightTireTempValue", "rearRightTireTemp", "rr"));

            String sensorFail = findString(tpmsSensorsObj, "OK", "frontLeftTireSensorFailureStatus", "status");
            boolean tpmsOk = !sensorFail.toUpperCase().contains("FAIL") && !sensorFail.toUpperCase().contains("ERROR");

            // 4. İklimlendirme (HVAC)
            JSONObject hvacObj = telem.optJSONObject("hvac");
            JSONObject hvacTarget = hvacObj != null ? hvacObj : telem;
            double cabinTemp = findDouble(hvacTarget, 22.0, "cabinTemperature", "insideTemperature", "cabinTemp", "temperature");
            double targetHvac = findDouble(hvacTarget, 21.5, "targetHvacTemperature", "targetTemperature", "setTemp");
            double outdoorTemp = findDouble(hvacTarget, 17.5, "outdoorTemperature", "ambientTemperature", "outsideTemp");
            boolean isClimateActive = findBoolean(hvacTarget, false, "isClimateActive", "hvacActive", "climateStatus");
            boolean isDefrostActive = findBoolean(hvacTarget, false, "isDefrostActive", "defrostActive");
            int seatDriver = (int) Math.round(findDouble(hvacTarget, 0.0, "seatHeaterDriver", "driverSeatHeater"));
            int seatPass = (int) Math.round(findDouble(hvacTarget, 0.0, "seatHeaterPassenger", "passengerSeatHeater"));
            boolean steeringHeater = findBoolean(hvacTarget, false, "steeringHeaterActive", "steeringWheelHeater");

            // 5. Güvenlik, Kapılar ve Erişim
            // Real Togg schema: vehicleStatus -> content -> vehicleStat -> vehicleStatusValueEnum
            String vStat = findString(vehStatusObj, "LOCKED", "vehicleStatusValueEnum", "vehicleStat", "status");
            boolean isLocked = !vStat.toUpperCase().contains("UNLOCK") && !vStat.toUpperCase().contains("OPEN");

            JSONObject secObj = telem.optJSONObject("security");
            JSONObject secTarget = secObj != null ? secObj : telem;
            boolean doorFL = findBoolean(secTarget, false, "doorOpenFL", "frontLeftDoorOpen");
            boolean doorFR = findBoolean(secTarget, false, "doorOpenFR", "frontRightDoorOpen");
            boolean doorRL = findBoolean(secTarget, false, "doorOpenRL", "rearLeftDoorOpen");
            boolean doorRR = findBoolean(secTarget, false, "doorOpenRR", "rearRightDoorOpen");
            boolean trunkOpen = findBoolean(secTarget, false, "trunkOpen", "tailgateOpen");
            boolean frunkOpen = findBoolean(secTarget, false, "frunkOpen", "hoodOpen");
            boolean windowsOpen = findBoolean(secTarget, false, "windowsOpen");
            boolean chargePortOpen = findBoolean(secTarget, false, "chargePortOpen", "chargingFlapOpen");
            boolean samConnected = findBoolean(secTarget, true, "samConnected", "cloudConnected");

            // 6. Sürüş İstatistikleri
            // Real Togg schema: odometerStatus -> odometerStatusContent -> odometer -> odometerValue
            double odoDouble = findDouble(odoObj, -1.0, "odometerValue", "odometer", "odometerKm", "totalDistance");
            if (odoDouble < 0) {
                odoDouble = findDouble(telem, 14280.0, "odometer", "odometerKm", "odometerValue");
            }
            long odometer = (long) Math.round(odoDouble);

            // Drive Mode: driveMode -> content -> driveModeState -> driveMode
            String driveMode = findString(driveModeObj, null, "driveMode", "driveModeState");
            if (driveMode == null) {
                driveMode = findString(telem, "Comfort", "driveMode", "driveModeState");
            }

            // Last Trip Info: tripObj has currentTrip, lastChargeTrip, myTrip
            JSONObject curTripObj = tripObj != null ? tripObj.optJSONObject("currentTrip") : null;
            JSONObject chargeTripObj = tripObj != null ? tripObj.optJSONObject("lastChargeTrip") : null;
            JSONObject myTripObj = tripObj != null ? tripObj.optJSONObject("myTrip") : null;

            VehicleTelemetry.TripData currentTrip = parseTripData(curTripObj, 3.0, 12, 22.0, 48);
            VehicleTelemetry.TripData lastChargeTrip = parseTripData(chargeTripObj, 85.0, 110, 17.2, 54);
            VehicleTelemetry.TripData myTrip = parseTripData(myTripObj, odoDouble > 0 ? odoDouble : 14280.0, 1450, 17.8, 56);

            double lastTripDist = currentTrip.distanceKm;
            int lastTripDur = currentTrip.durationMinutes;
            double lastTripAvgCons = currentTrip.avgConsumption;
            int lastTripAvgSpd = currentTrip.avgSpeed;

            // 7. Konum ve Park
            // Real Togg schema: location -> content -> latitude -> latitudeValue
            JSONObject locContent = locObj != null ? locObj.optJSONObject("content") : null;
            if (locContent == null && locObj != null) locContent = locObj;

            double lat = findDouble(locContent != null ? locContent.optJSONObject("latitude") : null, -1.0, "latitudeValue", "latitude", "lat");
            if (lat < 0) lat = findDouble(locObj, 39.925054, "latitudeValue", "latitude", "lat");

            double lon = findDouble(locContent != null ? locContent.optJSONObject("longitude") : null, -1.0, "longitudeValue", "longitude", "lon", "lng");
            if (lon < 0) lon = findDouble(locObj, 32.834755, "longitudeValue", "longitude", "lon", "lng");

            String parkAddr = findString(locObj, null, "parkAddress", "address");
            if (parkAddr == null || parkAddr.trim().isEmpty() || parkAddr.equalsIgnoreCase("Ankara, Çankaya")) {
                parkAddr = "Aracın konumu belirleniyor...";
            }
            int parkDur = (int) Math.round(findDouble(locObj, 0.0, "parkDurationMinutes", "parkDuration"));

            // Timestamp parsing
            long timestamp = parseTimestamp(telem != null ? telem.opt("updatedOn") : null,
                    parseTimestamp(root.opt("timestamp"), System.currentTimeMillis()));

            return new VehicleTelemetry(
                    vin, plate,
                    batterySoc, rangeKm, isCharging, chargePowerKw, chargeModeDisplay, chargeRemainingMin,
                    targetChargeLimit, batteryCapacity, lastChargeKwh,
                    lowVoltageSoc, lowVoltageVolts,
                    tpFL, ttFL, tpFR, ttFR, tpRL, ttRL, tpRR, ttRR, tpmsOk,
                    cabinTemp, targetHvac, outdoorTemp, isClimateActive, isDefrostActive,
                    seatDriver, seatPass, steeringHeater,
                    isLocked, doorFL, doorFR, doorRL, doorRR, trunkOpen, frunkOpen, windowsOpen, chargePortOpen, samConnected,
                    odometer, driveMode, lastTripDist, lastTripDur, lastTripAvgCons, lastTripAvgSpd,
                    lat, lon, parkAddr, parkDur,
                    timestamp, currentTrip, lastChargeTrip, myTrip
            );
        } catch (JSONException e) {
            return createDemoSnapshot(defaultVin, defaultPlate);
        }
    }

    private static VehicleTelemetry.TripData parseTripData(JSONObject tripObj, double fallbackDist, int fallbackDur, double fallbackCons, int fallbackSpd) {
        if (tripObj == null) {
            return new VehicleTelemetry.TripData(fallbackDist, fallbackDur, fallbackCons, fallbackSpd);
        }
        double rawDist = findDouble(tripObj, -1.0, "distance", "lastTripDistanceKm", "tripDistance");
        double distKm;
        if (rawDist > 50.0) {
            // Togg sends distance in meters (e.g. 3000m = 3.0 km)
            distKm = rawDist / 1000.0;
        } else if (rawDist >= 0) {
            distKm = rawDist;
        } else {
            distKm = fallbackDist;
        }

        // Duration: Duration(hours, minutes) or minutes
        JSONObject durObj = tripObj.optJSONObject("duration");
        int durationMin;
        if (durObj != null) {
            int h = durObj.optInt("hours", 0);
            int m = durObj.optInt("minutes", 0);
            // Togg sends minutes in tenths of a minute (e.g. 220 = 22 dk) or duration is 10x
            if (m > 59 || (h == 0 && m >= 10)) {
                m = (int) Math.round(m / 10.0);
            }
            durationMin = (h * 60) + m;
        } else {
            double rawH = findDouble(tripObj, 0.0, "hours");
            double rawM = findDouble(tripObj, -1.0, "minutes", "duration", "durationMinutes");
            if (rawM >= 0) {
                if (rawM > 59 || (rawH == 0 && rawM >= 10)) {
                    rawM = rawM / 10.0;
                }
                durationMin = (int) Math.round(rawH * 60 + rawM);
            } else {
                durationMin = fallbackDur;
            }
        }
        if (durationMin > 600 && durObj == null) {
            durationMin = durationMin / 10;
        }

        // Avg consumption: in 0.1 kWh/100km (e.g. 168 -> 16.8)
        double rawCons = findDouble(tripObj, -1.0, "avgConsumptions", "avgConsumption", "consumption");
        double cons;
        if (rawCons > 50.0) {
            cons = rawCons / 10.0;
        } else if (rawCons >= 0) {
            cons = rawCons;
        } else {
            cons = fallbackCons;
        }

        // Avg speed: km/h
        double rawSpd = findDouble(tripObj, -1.0, "avgSpeed", "speed");
        int spd;
        if (rawSpd > 250.0) {
            spd = (int) Math.round(rawSpd / 10.0);
        } else if (rawSpd >= 0) {
            spd = (int) Math.round(rawSpd);
        } else {
            spd = fallbackSpd;
        }

        return new VehicleTelemetry.TripData(distKm, durationMin, cons, spd);
    }

    /**
     * Converts raw pressure to bar.
     * If sensor outputs in kPa (> 100): divide by 100.
     * If sensor outputs in psi (> 15 && <= 100): convert to bar.
     * If sensor outputs in bar (<= 15): keep as bar.
     */
    private static double normalizePressureToBar(double raw) {
        if (raw <= 0) return 2.5;
        if (raw > 100.0) return raw / 100.0;
        if (raw > 15.0 && raw <= 100.0) return raw / 14.50377;
        return raw;
    }

    /**
     * Deep search for a numeric double across direct keys and recursively in child JSONObjects.
     */
    private static double findDouble(JSONObject obj, double fallback, String... keys) {
        if (obj == null) return fallback;
        Double found = searchDoubleRecursive(obj, keys, 0);
        return found != null ? found : fallback;
    }

    private static Double searchDoubleRecursive(JSONObject obj, String[] keys, int depth) {
        if (obj == null || depth > 5) return null;
        for (String k : keys) {
            if (obj.has(k)) {
                Object v = obj.opt(k);
                if (v instanceof Number) return ((Number) v).doubleValue();
                if (v instanceof String) {
                    try { return Double.parseDouble(((String) v).trim()); } catch (Exception ignored) {}
                }
            }
        }
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String childKey = it.next();
            JSONObject child = obj.optJSONObject(childKey);
            if (child != null) {
                Double res = searchDoubleRecursive(child, keys, depth + 1);
                if (res != null) return res;
            }
        }
        return null;
    }

    /**
     * Deep search for a string across direct keys and recursively in child JSONObjects.
     */
    private static String findString(JSONObject obj, String fallback, String... keys) {
        if (obj == null) return fallback;
        String found = searchStringRecursive(obj, keys, 0);
        return found != null ? found : fallback;
    }

    private static String searchStringRecursive(JSONObject obj, String[] keys, int depth) {
        if (obj == null || depth > 5) return null;
        for (String k : keys) {
            if (obj.has(k)) {
                Object v = obj.opt(k);
                if (v instanceof String && !((String) v).trim().isEmpty()) {
                    return ((String) v).trim();
                }
                if (v instanceof Number) return String.valueOf(v);
            }
        }
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String childKey = it.next();
            JSONObject child = obj.optJSONObject(childKey);
            if (child != null) {
                String res = searchStringRecursive(child, keys, depth + 1);
                if (res != null) return res;
            }
        }
        return null;
    }

    /**
     * Deep search for a boolean across direct keys and recursively in child JSONObjects.
     */
    private static Boolean findBoolean(JSONObject obj, Boolean fallback, String... keys) {
        if (obj == null) return fallback;
        Boolean found = searchBooleanRecursive(obj, keys, 0);
        return found != null ? found : fallback;
    }

    private static Boolean searchBooleanRecursive(JSONObject obj, String[] keys, int depth) {
        if (obj == null || depth > 5) return null;
        for (String k : keys) {
            if (obj.has(k)) {
                Object v = obj.opt(k);
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof Number) return ((Number) v).intValue() != 0;
                if (v instanceof String) return Boolean.parseBoolean(((String) v).trim());
            }
        }
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String childKey = it.next();
            JSONObject child = obj.optJSONObject(childKey);
            if (child != null) {
                Boolean res = searchBooleanRecursive(child, keys, depth + 1);
                if (res != null) return res;
            }
        }
        return null;
    }

    private static long parseTimestamp(Object val, long fallback) {
        if (val == null) return fallback;
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            String s = ((String) val).trim();
            if (s.isEmpty()) return fallback;
            try {
                return Long.parseLong(s);
            } catch (Exception ignored) {}
            try {
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                iso.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = iso.parse(s.replace("Z", ""));
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    public static VehicleTelemetry createDemoSnapshot(String vin, String plate) {
        return new VehicleTelemetry(
                vin != null ? vin : "NL1CFB0P800100522",
                plate != null ? plate : "06GDU258",
                // 1. Batarya & Şarj
                82,         // %82 Doluluk
                426,        // 426 km Menzil
                false,      // Şarjda Değil
                0.0,        // 0 kW
                "Bağlı Değil",
                0,
                80,         // Hedef şarj limiti %80
                88.5,       // 88.5 kWh Net Batarya
                34.2,       // Son şarjda 34.2 kWh alındı
                // 2. 12V Akü
                94,         // %94 (Sağlıklı)
                13.4,       // 13.4 Volt
                // 3. Lastikler & TPMS (2.5 bar = 36.3 PSI)
                2.5, 23,    // Ön Sol: 2.5 bar, 23°C
                2.5, 23,    // Ön Sağ: 2.5 bar, 23°C
                2.6, 24,    // Arka Sol: 2.6 bar, 24°C
                2.6, 24,    // Arka Sağ: 2.6 bar, 24°C
                true,       // TPMS Sensörleri Sağlıklı
                // 4. İklimlendirme (HVAC)
                22.0,       // Kabin İçi 22.0°C
                21.5,       // Hedef 21.5°C
                17.5,       // Dış Ortam 17.5°C
                false,      // Klima Kapalı
                false,      // Buğu Çözme Kapalı
                0,          // Sürücü Koltuk Isıtma (Kapalı)
                0,          // Yolcu Koltuk Isıtma (Kapalı)
                false,      // Direksiyon Isıtma Kapalı
                // 5. Güvenlik & Kapılar
                true,       // Merkezi Kilitli
                false,      // Ön Sol Kapı Kapalı
                false,      // Ön Sağ Kapı Kapalı
                false,      // Arka Sol Kapı Kapalı
                false,      // Arka Sağ Kapı Kapalı
                false,      // Bagaj Kapalı
                false,      // Ön Kaput (Frunk) Kapalı
                false,      // Camlar Kapalı
                false,      // Şarj Kapağı Kapalı
                true,       // SAM Bulut Bağlantısı Aktif
                // 6. Sürüş İstatistikleri
                14280L,     // 14.280 km Toplam Kilometre
                "Comfort",  // Sürüş Modu
                38.4,       // Son Sürüş 38.4 km
                42,         // 42 Dakika
                16.8,       // 16.8 kWh/100km Tüketim
                58,         // Ortalama 58 km/s
                // 7. Konum & Park
                39.925054, 32.834755, // Ankara Koordinatları
                "Aracın konumu belirleniyor...",
                0,        // Park süresi hesaplanır
                System.currentTimeMillis(),
                new VehicleTelemetry.TripData(38.4, 42, 16.8, 58),
                new VehicleTelemetry.TripData(85.0, 110, 17.2, 54),
                new VehicleTelemetry.TripData(14280.0, 14500, 17.8, 56)
        );
    }
}
