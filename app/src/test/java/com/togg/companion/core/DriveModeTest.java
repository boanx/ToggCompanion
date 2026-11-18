package com.togg.companion.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class DriveModeTest {
    @Test public void unavailableModeCannotMatchEcoBySubstring() {
        assertEquals("Bilgi yok", DriveMode.label("DRIVEMODE_ECO_NOT_AVAILABLE"));
        assertEquals("Bilgi yok", DriveMode.label(null));
        assertEquals("Bilgi yok", DriveMode.label("1"));
    }
    @Test public void namespacedAndStoredModesHaveConsistentLabels() {
        assertEquals("Eco", DriveMode.label("DxDriveMode.eco"));
        assertEquals("Konfor", DriveMode.label("DRIVEMODE_COMFORT"));
        assertEquals("Spor", DriveMode.label("Sport"));
        assertEquals("Bireysel", DriveMode.label("Individual"));
    }
}
