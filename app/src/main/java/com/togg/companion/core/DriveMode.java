package com.togg.companion.core;
import java.util.Locale;
/** Textual vehicle mode values only. Unknown numeric enums never become Comfort. */
public final class DriveMode {
    private DriveMode() { }
    public static String canonical(String raw) {
        if (raw == null) return "";
        String mode = raw.trim().toUpperCase(Locale.ROOT);
        int namespace = mode.lastIndexOf('.'); if (namespace >= 0) mode = mode.substring(namespace + 1);
        mode = mode.replaceFirst("^(?:DRIVEMODE_|DRIVE_MODE_)", "");
        switch (mode) {
            case "ECO": case "ECONOMY": return "Eco";
            case "COMFORT": case "KONFOR": return "Comfort";
            case "SPORT": case "SPOR": return "Sport";
            case "INDIVIDUAL": case "BIREYSEL": case "BİREYSEL": return "Individual";
            default: return "";
        }
    }
    public static String label(String raw) {
        switch (canonical(raw)) {
            case "Eco": return "Eco";
            case "Comfort": return "Konfor";
            case "Sport": return "Spor";
            case "Individual": return "Bireysel";
            default: return "Bilgi yok";
        }
    }
}
