package com.togg.companion.widget;
import org.junit.Test;
import static org.junit.Assert.*;
public class WidgetMapSnapshotStoreTest {
    @Test public void otherVehicleOrLocationCannotReuseMap() {
        assertTrue(WidgetMapSnapshotStore.matches("vehicle-a", 41.1, 29.2, "vehicle-a", 41.1, 29.2));
        assertFalse(WidgetMapSnapshotStore.matches("vehicle-a", 41.1, 29.2, "vehicle-b", 41.1, 29.2));
        assertFalse(WidgetMapSnapshotStore.matches("vehicle-a", 41.1, 29.2, "vehicle-a", 40.1, 29.2));
    }
    @Test public void missingAndInvalidCoordinatesNeverReuseMap() {
        assertFalse(WidgetMapSnapshotStore.matches("", 41.1, 29.2, "", 41.1, 29.2));
        assertFalse(WidgetMapSnapshotStore.matches("vehicle", 0, 0, "vehicle", 0, 0));
        assertFalse(WidgetMapSnapshotStore.matches("vehicle", Double.NaN, 29.2, "vehicle", 41.1, 29.2));
    }
}
