package com.togg.companion.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VehicleBleManagerTest {
    @Test public void transportReadinessDoesNotAuthorizeUnverifiedLiveCommands() {
        assertTrue(VehicleBleManager.isGattReady(false, VehicleBleManager.ConnectionState.CONNECTED, true));
        assertNotNull(VehicleBleManager.getCommandAccessError(false, false));
        assertNull(VehicleBleManager.getCommandAccessError(true, false));
        assertNull(VehicleBleManager.getCommandAccessError(false, true));
    }
    @Test public void liveConnectionWithoutGattCharacteristicIsNotReadyAndHasHelpfulError() {
        assertFalse(VehicleBleManager.isGattReady(false, VehicleBleManager.ConnectionState.CONNECTED, false));
        String error = VehicleBleManager.getCommandGattError(false);
        assertNotNull(error);
        assertTrue(error.contains("Smart Access BLE GATT"));
        assertTrue(error.contains("beacon"));
    }

    @Test public void demoModeRemainsReadyWithoutPhysicalGatt() {
        assertTrue(VehicleBleManager.isGattReady(true, VehicleBleManager.ConnectionState.CONNECTED, false));
        assertNull(VehicleBleManager.getCommandGattError(true));
    }

    @Test public void liveGattRequiresConnectedStateAndWriteCharacteristic() {
        assertTrue(VehicleBleManager.isGattReady(false, VehicleBleManager.ConnectionState.CONNECTED, true));
        assertFalse(VehicleBleManager.isGattReady(false, VehicleBleManager.ConnectionState.CONNECTING, true));
    }
}
