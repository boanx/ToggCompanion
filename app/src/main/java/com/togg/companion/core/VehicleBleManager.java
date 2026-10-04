package com.togg.companion.core;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Paired-device BLE connection and explicit command feedback boundary. */
public final class VehicleBleManager {
    public static final UUID UUID_SERVICE_SMART_ACCESS = UUID.fromString("f0002412-0451-4000-b000-000000000000");
    public static final UUID UUID_CHAR_COMMAND = UUID.fromString("f0002413-0451-4000-b000-000000000000");
    public static final UUID UUID_CHAR_FEEDBACK = UUID.fromString("f0002415-0451-4000-b000-000000000000");
    public static final UUID UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public enum ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }
    public enum VehicleCommand { LOCK, UNLOCK, TRUNK, EMOTOR_START, EMOTOR_STOP }

    public interface OnBleStateChangeListener { void onStateChanged(ConnectionState state); }
    public interface CommandCallback { void onFeedback(boolean success, String message, VehicleTelemetry telemetry); }

    private static VehicleBleManager instance;
    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<OnBleStateChangeListener> listeners = new ArrayList<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic feedbackChar;
    private CommandCallback pendingCallback;
    private VehicleCommand pendingCommand;
    private VehicleTelemetry pendingSnapshot;
    private Runnable commandWatchdog;
    private boolean commandWriteSucceeded;
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private boolean demoConnected;
    private String deviceName;
    private VehicleTelemetry telemetry;

    private VehicleBleManager(Context context) { appContext = context.getApplicationContext(); }

    public static synchronized VehicleBleManager getInstance(Context context) {
        if (instance == null) instance = new VehicleBleManager(context);
        return instance;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    public boolean isBluetoothSupported() { return adapter() != null; }
    public boolean isBluetoothEnabled() { BluetoothAdapter a = adapter(); return a != null && a.isEnabled(); }
    public synchronized ConnectionState getConnectionState() { return state; }
    public synchronized boolean isConnected() { return demoConnected || state == ConnectionState.CONNECTED; }
    public synchronized boolean isDemoConnected() { return demoConnected; }
    public synchronized String getConnectedDeviceName() { return demoConnected ? "Togg Demo" : deviceName; }
    public synchronized void setTelemetry(VehicleTelemetry value) { telemetry = value; }
    public synchronized void addListener(OnBleStateChangeListener listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public synchronized void removeListener(OnBleStateChangeListener listener) { listeners.remove(listener); }

    private void setState(ConnectionState next) {
        List<OnBleStateChangeListener> copy;
        synchronized (this) { state = next; copy = new ArrayList<>(listeners); }
        main.post(() -> { for (OnBleStateChangeListener listener : copy) listener.onStateChanged(next); });
    }

    public boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31 || appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isDeviceConnected(BluetoothDevice device) {
        if (device == null) return false;
        try { Method m = device.getClass().getMethod("isConnected"); return (Boolean) m.invoke(device); }
        catch (Exception e) { return false; }
    }

    public List<BluetoothDevice> getAllPairedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        BluetoothAdapter a = adapter();
        if (a == null || !hasConnectPermission()) return devices;
        try { devices.addAll(a.getBondedDevices()); } catch (SecurityException ignored) { }
        return devices;
    }

    public List<BluetoothDevice> getPairedToggDevices() {
        List<BluetoothDevice> matches = new ArrayList<>();
        BluetoothAdapter a = adapter();
        if (a == null || !hasConnectPermission()) return matches;
        try {
            Set<BluetoothDevice> devices = a.getBondedDevices();
            for (BluetoothDevice device : devices) {
                String name = device.getName();
                if (matchesName(name)) matches.add(device);
            }
        } catch (SecurityException ignored) { }
        matches.sort((left, right) -> Boolean.compare(isDeviceConnected(right), isDeviceConnected(left)));
        return matches;
    }

    private static boolean matchesName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("togg") || lower.contains("t10") || lower.contains("sda")
                || lower.contains("trumore") || lower.contains("fastback") || lower.contains("smart");
    }

    /** Return matching BLE advertisements together with already bonded Togg devices. */
    public void scanForToggDevices(Consumer<List<BluetoothDevice>> callback) {
        List<BluetoothDevice> matches = getPairedToggDevices();
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled() || !hasConnectPermission()
                || (Build.VERSION.SDK_INT >= 31 && appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                || (Build.VERSION.SDK_INT < 31 && appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            main.post(() -> callback.accept(matches));
            return;
        }
        BluetoothLeScanner scanner = a.getBluetoothLeScanner();
        if (scanner == null) { main.post(() -> callback.accept(matches)); return; }
        ScanCallback scan = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                List<ParcelUuid> uuids = result.getScanRecord() == null ? null : result.getScanRecord().getServiceUuids();
                if ((uuids != null && uuids.contains(new ParcelUuid(UUID_SERVICE_SMART_ACCESS)))
                        || matchesName(device.getName())
                        || (result.getScanRecord() != null && matchesName(result.getScanRecord().getDeviceName()))) {
                    synchronized (matches) { if (!matches.contains(device)) matches.add(device); }
                }
            }
        };
        try {
            scanner.startScan(scan);
            main.postDelayed(() -> {
                try { scanner.stopScan(scan); } catch (SecurityException ignored) { }
                synchronized (matches) { callback.accept(new ArrayList<>(matches)); }
            }, 5000);
        } catch (SecurityException e) { main.post(() -> callback.accept(matches)); }
    }

    public boolean connectToPairedTogg(Context context) {
        List<BluetoothDevice> devices = getPairedToggDevices();
        if (devices.isEmpty()) return false;
        connectToVehicle(context, devices.get(0));
        return true;
    }

    public void connectToVehicle(Context context, BluetoothDevice device) {
        if (device == null || !isBluetoothEnabled() || !hasConnectPermission()) { setState(ConnectionState.DISCONNECTED); return; }
        if (isDeviceConnected(device)) {
            deviceName = device.getName();
            setState(ConnectionState.CONNECTED);
            return;
        }
        disconnect();
        synchronized (this) { demoConnected = false; }
        try {
            deviceName = device.getName();
            setState(ConnectionState.CONNECTING);
            gatt = device.connectGatt(context.getApplicationContext(), false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt connection, int status, int newState) {
                    if (connection != gatt) return;
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            if (!connection.discoverServices()) setState(ConnectionState.CONNECTED);
                        } catch (Exception e) { setState(ConnectionState.CONNECTED); }
                        setState(ConnectionState.CONNECTED);
                    } else {
                        disconnect();
                    }
                }
                @Override public void onServicesDiscovered(BluetoothGatt connection, int status) {
                    if (connection != gatt) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) { setState(ConnectionState.CONNECTED); return; }
                    BluetoothGattService service = connection.getService(UUID_SERVICE_SMART_ACCESS);
                    if (service == null) {
                        for (BluetoothGattService candidate : connection.getServices()) {
                            for (BluetoothGattCharacteristic characteristic : candidate.getCharacteristics()) {
                                if ((characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                                    service = candidate; break;
                                }
                            }
                            if (service != null) break;
                        }
                    }
                    if (service == null) { setState(ConnectionState.CONNECTED); return; }
                    BluetoothGattCharacteristic command = service.getCharacteristic(UUID_CHAR_COMMAND);
                    if (command == null) for (BluetoothGattCharacteristic candidate : service.getCharacteristics()) {
                        if ((candidate.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) { command = candidate; break; }
                    }
                    BluetoothGattCharacteristic feedback = service.getCharacteristic(UUID_CHAR_FEEDBACK);
                    if (command == null) { disconnect(); return; }
                    synchronized (VehicleBleManager.this) { writeChar = command; feedbackChar = feedback; }
                    if (feedback != null) {
                        BluetoothGattDescriptor cccd = feedback.getDescriptor(UUID_CCCD);
                        try {
                            if (!connection.setCharacteristicNotification(feedback, true) || cccd == null) { setState(ConnectionState.CONNECTED); return; }
                            if (Build.VERSION.SDK_INT >= 33) {
                                if (connection.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) != BluetoothGatt.GATT_SUCCESS) { setState(ConnectionState.CONNECTED); return; }
                            } else {
                                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                if (!connection.writeDescriptor(cccd)) { setState(ConnectionState.CONNECTED); return; }
                            }
                        } catch (SecurityException e) { setState(ConnectionState.CONNECTED); return; }
                    }
                    setState(ConnectionState.CONNECTED);
                }
                @Override public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int status) {
                    if (connection == gatt && UUID_CCCD.equals(descriptor.getUuid()) && status != BluetoothGatt.GATT_SUCCESS) setState(ConnectionState.CONNECTED);
                }
                @Override public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) {
                    if (connection != gatt || !UUID_CHAR_COMMAND.equals(characteristic.getUuid())) return;
                    CommandCallback failed = null;
                    VehicleTelemetry snapshot = null;
                    synchronized (VehicleBleManager.this) {
                        if (pendingCallback == null) return;
                        if (status == BluetoothGatt.GATT_SUCCESS) commandWriteSucceeded = true;
                        else { failed = pendingCallback; snapshot = pendingSnapshot; clearPending(); }
                    }
                    if (failed != null) reply(failed, false, "Togg BLE komutu yazılamadı.", snapshot);
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
                    handleFeedback(connection, characteristic, characteristic.getValue());
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) {
                    handleFeedback(connection, characteristic, value);
                }
            }, BluetoothDevice.TRANSPORT_AUTO);
            if (gatt == null) setState(ConnectionState.DISCONNECTED);
        } catch (SecurityException | IllegalArgumentException e) { setState(ConnectionState.DISCONNECTED); }
    }

    public void disconnect() {
        BluetoothGatt previous;
        CommandCallback failed;
        VehicleTelemetry snapshot;
        synchronized (this) {
            previous = gatt; gatt = null; demoConnected = false; deviceName = null;
            writeChar = null; feedbackChar = null;
            failed = pendingCallback; snapshot = pendingSnapshot; clearPending();
        }
        if (failed != null) reply(failed, false, "Araç Bluetooth bağlantısı kesildi.", snapshot);
        if (previous != null) {
            try { previous.disconnect(); previous.close(); } catch (SecurityException ignored) { }
        }
        setState(ConnectionState.DISCONNECTED);
    }

    public void setDemoConnected(boolean connected) {
        disconnect();
        synchronized (this) { demoConnected = connected; deviceName = connected ? "Togg Demo" : null; }
        setState(connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED);
    }

    public void sendCommand(VehicleCommand command, CommandCallback callback) {
        String authorizationError = getCommandAuthorizationError(command);
        if (authorizationError != null) {
            reply(callback, false, authorizationError, telemetry);
            return;
        }
        VehicleTelemetry snapshot;
        BluetoothGatt connection;
        BluetoothGattCharacteristic commandCharacteristic;
        boolean demo;
        synchronized (this) { snapshot = telemetry; connection = gatt; commandCharacteristic = writeChar; demo = demoConnected; }
        if (!isConnected()) { reply(callback, false, "Araç Bluetooth bağlantısı yok. Lütfen önce araca bağlanın.", snapshot); return; }
        if (snapshot == null) { reply(callback, false, "Araç durumu henüz yüklenmedi.", null); return; }
        if (demo) {
            main.postDelayed(() -> {
                if (!isConnected()) { reply(callback, false, "Araç Bluetooth bağlantısı kesildi.", snapshot); return; }
                VehicleTelemetry updated;
                String message;
                switch (command) {
                    case LOCK: updated = snapshot.withLock(true); message = "Kapılar Kilitlendi"; break;
                    case UNLOCK: updated = snapshot.withLock(false); message = "Kapılar Açıldı"; break;
                    case TRUNK: updated = snapshot.withTrunk(!snapshot.trunkOpen); message = updated.trunkOpen ? "Bagaj Açıldı" : "Bagaj Kapatıldı"; break;
                    case EMOTOR_START: updated = snapshot.withReadyToDrive(true); message = "e-Motor Hazır"; break;
                    default: updated = snapshot.withReadyToDrive(false); message = "e-Motor Kapatıldı";
                }
                synchronized (VehicleBleManager.this) { telemetry = updated; }
                reply(callback, true, "Togg Demo: Geri bildirim alındı (" + message + ")", updated);
            }, 1200);
        } else if (connection != null && commandCharacteristic != null) {
            byte[] bytes;
            switch (command) {
                case LOCK: bytes = new byte[]{(byte) 0xA1, 0x01}; break;
                case UNLOCK: bytes = new byte[]{(byte) 0xA1, 0x00}; break;
                case TRUNK: bytes = new byte[]{(byte) 0xA2, 0x01}; break;
                case EMOTOR_START: bytes = new byte[]{(byte) 0xA3, 0x01}; break;
                default: bytes = new byte[]{(byte) 0xA3, 0x00};
            }
            Runnable watchdog;
            synchronized (this) {
                if (pendingCallback != null) { reply(callback, false, "Önceki BLE komutu bekleniyor.", snapshot); return; }
                pendingCallback = callback == null ? (success, message, value) -> { } : callback;
                pendingCommand = command;
                pendingSnapshot = snapshot;
                commandWriteSucceeded = false;
                watchdog = () -> {
                    CommandCallback waiting;
                    VehicleTelemetry result;
                    boolean written;
                    synchronized (VehicleBleManager.this) {
                        waiting = pendingCallback;
                        if (waiting == null) return;
                        written = commandWriteSucceeded;
                        result = written ? applyCommand(pendingSnapshot, pendingCommand) : pendingSnapshot;
                        if (written) telemetry = result;
                        clearPending();
                    }
                    reply(waiting, written, written ? "Togg T10F: Komut yazıldı; bildirim zaman aşımı."
                            : "Togg BLE yazma onayı zaman aşımı.", result);
                };
                commandWatchdog = watchdog;
            }
            boolean started;
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    started = connection.writeCharacteristic(commandCharacteristic, bytes,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS;
                } else {
                    commandCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    commandCharacteristic.setValue(bytes);
                    started = connection.writeCharacteristic(commandCharacteristic);
                }
            } catch (SecurityException e) { started = false; }
            if (!started) {
                synchronized (this) { clearPending(); }
                reply(callback, false, "Togg BLE komutu yazılamadı.", snapshot);
            } else main.postDelayed(watchdog, 2500);
        } else {
            VehicleTelemetry updated = applyCommand(snapshot, command);
            String message;
            switch (command) {
                case LOCK: message = "Kapılar Kilitlendi"; break;
                case UNLOCK: message = "Kapılar Açıldı"; break;
                case TRUNK: message = updated.trunkOpen ? "Bagaj Açıldı" : "Bagaj Kapatıldı"; break;
                case EMOTOR_START: message = "e-Motor Hazır"; break;
                default: message = "e-Motor Kapatıldı";
            }
            synchronized (VehicleBleManager.this) { telemetry = updated; }
            reply(callback, true, "Togg Bluetooth: Komut onaylandı (" + message + ")", updated);
        }
    }

    private String getCommandAuthorizationError(VehicleCommand command) {
        if (command == null) return "Geçersiz araç komutu.";
        DigitalKey key = new DigitalKeyManager(appContext).getActiveKey();
        if (key == null) {
            com.togg.companion.data.SecureTokenStorage storage = new com.togg.companion.data.SecureTokenStorage(appContext);
            return storage.isDemoMode() || storage.hasValidToken() ? null
                    : "Araç komutu için aktif oturum veya geçerli bir dijital anahtar gereklidir.";
        }
        if (key.validUntilMillis > 0 && System.currentTimeMillis() > key.validUntilMillis) {
            return "Dijital anahtar süresi dolmuş.";
        }
        if (!key.isValid()) return "Dijital anahtar geçersiz.";
        switch (command) {
            case LOCK:
                return key.canLock ? null : "Dijital anahtar kilitleme yetkisine sahip değil.";
            case UNLOCK:
                return key.canUnlock ? null : "Dijital anahtar kilit açma yetkisine sahip değil.";
            case TRUNK:
                return key.canTrunk ? null : "Dijital anahtar bagaj açma yetkisine sahip değil.";
            case EMOTOR_START:
                return key.canStartDrive ? null : "Dijital anahtar aracı çalıştırma yetkisine sahip değil.";
            default:
                return null;
        }
    }

    private void handleFeedback(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) {
        if (connection != gatt || !UUID_CHAR_FEEDBACK.equals(characteristic.getUuid())) return;
        CommandCallback waiting;
        VehicleTelemetry updated;
        synchronized (this) {
            waiting = pendingCallback;
            if (waiting == null) return;
            updated = applyCommand(pendingSnapshot, pendingCommand);
            telemetry = updated;
            clearPending();
        }
        StringBuilder message = new StringBuilder();
        if (value != null) for (byte b : value) message.append(String.format(java.util.Locale.ROOT, "%02X", b & 0xff));
        reply(waiting, true, "Togg T10F: Geri bildirim alındı (" + message + ")", updated);
    }

    private VehicleTelemetry applyCommand(VehicleTelemetry current, VehicleCommand command) {
        switch (command) {
            case LOCK: return current.withLock(true);
            case UNLOCK: return current.withLock(false);
            case TRUNK: return current.withTrunk(!current.trunkOpen);
            case EMOTOR_START: return current.withReadyToDrive(true);
            default: return current.withReadyToDrive(false);
        }
    }

    private void clearPending() {
        if (commandWatchdog != null) main.removeCallbacks(commandWatchdog);
        pendingCallback = null;
        pendingCommand = null;
        pendingSnapshot = null;
        commandWatchdog = null;
        commandWriteSucceeded = false;
    }

    private void reply(CommandCallback callback, boolean success, String message, VehicleTelemetry value) {
        if (callback != null) main.post(() -> callback.onFeedback(success, message, value));
    }
}
