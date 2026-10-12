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
import android.content.SharedPreferences;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import android.util.Base64;
import org.json.JSONObject;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.SmartAccessCertificateClient;

/** Paired-device BLE connection and explicit command feedback boundary. */
public final class VehicleBleManager {
    public static final UUID UUID_SERVICE_SMART_ACCESS = UUID.fromString("f0002412-0451-4000-b000-000000000000");
    public static final UUID UUID_CHAR_COMMAND = UUID.fromString("f0002413-0451-4000-b000-000000000000");
    public static final UUID UUID_CHAR_FEEDBACK = UUID.fromString("f0002415-0451-4000-b000-000000000000");
    public static final UUID UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public enum ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }
    public enum VehicleCommand { LOCK, UNLOCK, TRUNK, TRUNK_OPEN, TRUNK_CLOSE, EMOTOR_START, EMOTOR_STOP }
    public enum AccessState { DISCONNECTED, TRANSPORT_READY, PREPARING_KEY, FETCHING_CERTIFICATE,
        AUTHENTICATING, READY, COMMAND_PENDING, ERROR }
    public interface OnAccessStateChangeListener { void onAccessStateChanged(AccessState state); }
    public interface AccessCallback { void onResult(boolean success, String message); }

    public interface OnBleStateChangeListener { void onStateChanged(ConnectionState state); }
    public interface OnBleTelemetryListener { void onTelemetryUpdated(VehicleTelemetry telemetry); }
    public interface CommandCallback { void onFeedback(boolean success, String message, VehicleTelemetry telemetry); }

    private static VehicleBleManager instance;
    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<OnBleStateChangeListener> listeners = new ArrayList<>();
    private final List<OnBleTelemetryListener> telemetryListeners = new ArrayList<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic feedbackChar;
    private volatile String connectionError;
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private boolean demoConnected;
    private String deviceName;
    private VehicleTelemetry telemetry;
    private final SecureTokenStorage storage;
    private final SmartAccessKeyStore keyStore;
    private final SmartAccessCertificateClient certificateClient;
    private final SharedPreferences trustPrefs;
    private final List<OnAccessStateChangeListener> accessListeners = new ArrayList<>();
    private final ArrayDeque<String> diagnostics = new ArrayDeque<>();
    private final ArrayDeque<byte[]> writeQueue = new ArrayDeque<>();
    private final ArrayDeque<byte[]> deferredFrames = new ArrayDeque<>();
    private volatile AccessState accessState = AccessState.DISCONNECTED;
    private volatile String accessStatus = "BLE bağlı değil.";
    private volatile String lastCommandResult = "Henüz komut denenmedi.";
    private volatile SmartAccessCertificate certificate;
    private volatile SmartAccessSession session;
    private AccessCallback authenticationCallback;
    private CommandCallback commandCallback;
    private boolean writeBusy, noResponseWrite, commandPending, servicesStarted;
    private int negotiatedMtu = 23, generation, setupGeneration;
    private long txCount, rxCount, lastStatusAt;
    private String sessionVin = "", sessionTruId = "", sessionAccount = "";
    private Runnable authTimeout, commandTimeout, writeTimeout, statusPoll, connectionTimeout;

    private VehicleBleManager(Context context) {
        appContext = context.getApplicationContext();
        storage = new SecureTokenStorage(appContext);
        keyStore = new SmartAccessKeyStore(appContext);
        certificateClient = new SmartAccessCertificateClient(appContext, storage);
        SharedPreferences prefs;
        try {
            MasterKey master = new MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(appContext, "smart_access_session_prefs", master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception error) { prefs = null; }
        trustPrefs = prefs;
    }

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
    public synchronized boolean isConnected() { return isGattReady(demoConnected, state, writeChar != null); }
    public synchronized boolean isGattReady() { return isGattReady(demoConnected, state, writeChar != null); }
    static boolean isGattReady(boolean demo, ConnectionState connectionState, boolean hasWriteCharacteristic) {
        return demo || connectionState == ConnectionState.CONNECTED && hasWriteCharacteristic;
    }
    static String getCommandGattError(boolean ready) {
        return ready ? null : "Togg Smart Access BLE GATT hazır değil. Medya Bluetooth bağlantısı komut gönderemez; araç yakınında Smart Access BLE beacon'ını tarayın.";
    }
    public String getConnectionError() { return connectionError; }
    public synchronized boolean isDemoConnected() { return demoConnected; }
    public synchronized String getConnectedDeviceName() { return demoConnected ? "Togg Demo" : deviceName; }
    public synchronized void setTelemetry(VehicleTelemetry value) {
        telemetry = value != null && !demoConnected && !storage.getVin().equalsIgnoreCase(value.vin) ? null : value;
        if (telemetry == null) return;
        if (value != null && session != null && session.isReady() && sessionVin.equalsIgnoreCase(value.vin)
                && System.currentTimeMillis() - session.getStatusAt() < 20000)
            telemetry = value.withSmartAccessStatus(session.getStatus());
    }
    public synchronized void addListener(OnBleStateChangeListener listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public synchronized void removeListener(OnBleStateChangeListener listener) { listeners.remove(listener); }
    public synchronized void addTelemetryListener(OnBleTelemetryListener listener) { if (!telemetryListeners.contains(listener)) telemetryListeners.add(listener); }
    public synchronized void removeTelemetryListener(OnBleTelemetryListener listener) { telemetryListeners.remove(listener); }
    public synchronized void addAccessListener(OnAccessStateChangeListener listener) { if (!accessListeners.contains(listener)) accessListeners.add(listener); }
    public synchronized void removeAccessListener(OnAccessStateChangeListener listener) { accessListeners.remove(listener); }
    public AccessState getAccessState() { return accessState; }
    public String getAccessStatusText() { return accessStatus; }
    public String getLastCommandResult() { return lastCommandResult; }
    public boolean isCommandReady() {
        SmartAccessSession active = session;
        return demoConnected || active != null && active.isReady() && validSessionBinding()
                && System.currentTimeMillis() - active.getStatusAt() < 20000;
    }
    public SmartAccessProtocol.RawStatus getSmartAccessStatus() {
        SmartAccessSession active = session;
        return active != null && active.isReady() && validSessionBinding()
                && System.currentTimeMillis() - active.getStatusAt() < 20000 ? active.getStatus() : null;
    }
    public String getConfiguredTruId() {
        return trustPrefs != null && storage.hasValidToken()
                && currentAccount().equals(trustPrefs.getString("account", ""))
                && storage.getVin().equals(trustPrefs.getString("vin", ""))
                ? trustPrefs.getString("tru_id", "") : "";
    }
    public String getSuggestedTruId() { return certificateClient.getSuggestedTruId(storage.getVin()); }

    /** Public certificate metadata for the existing phone-relayed watch UI; no private key leaves AndroidKeyStore. */
    public DigitalKey getSmartAccessKeyMetadata() {
        if (!isCommandReady() || demoConnected || certificate == null) return null;
        return new DigitalKey(certificate.truId, "", certificate.vin,
                telemetry != null && certificate.vin.equalsIgnoreCase(telemetry.vin) ? telemetry.plate : "",
                "Telefon üzerinden Smart Access", certificate.validFromMillis, certificate.validUntilMillis,
                certificate.canDoorOpen, certificate.canDoorOpen, certificate.canDrive, false,
                certificate.canTrunkOpen, Base64.encodeToString(certificate.getRawCertificate(), Base64.NO_WRAP),
                true, certificate.timestampMillis);
    }

    private void setAccessState(AccessState next, String message) {
        boolean changed = accessState != next;
        accessState = next; accessStatus = message;
        record("ACCESS " + next.name());
        List<OnAccessStateChangeListener> copy;
        synchronized (this) { copy = new ArrayList<>(accessListeners); }
        main.post(() -> { for (OnAccessStateChangeListener listener : copy) listener.onAccessStateChanged(next); });
        if (changed && !demoConnected)
            com.togg.companion.wearable.WearSyncManager.syncDigitalKey(appContext, getSmartAccessKeyMetadata());
    }
    private synchronized void record(String message) {
        if (diagnostics.size() >= 80) diagnostics.removeFirst();
        diagnostics.addLast(System.currentTimeMillis() + " " + message);
    }
    public synchronized String getDiagnosticsText() {
        StringBuilder text = new StringBuilder("Togg Companion Smart Access 2026-10-12 tanı\n");
        text.append("Android: ").append(Build.VERSION.SDK_INT).append("\nGATT: ").append(state)
                .append("\nOturum: ").append(accessState).append("\nMTU: ").append(negotiatedMtu)
                .append("\nGönderilen/Alınan: ").append(txCount).append('/').append(rxCount)
                .append("\nSertifika: ").append(certificate != null ? "alındı" : "yok")
                .append("\nİmzalı durum yaşı (ms): ").append(lastStatusAt == 0 ? -1 : System.currentTimeMillis()-lastStatusAt)
                .append("\n");
        if (session != null && session.getStatus() != null) {
            SmartAccessProtocol.RawStatus s = session.getStatus();
            text.append("Araç durumu: kilit=").append(s.lockState).append(", bagaj=").append(s.ajarTrunkLid)
                    .append(", güç=").append(s.vehStat).append(", başlatılabilir=").append(s.canStart)
                    .append(", menzil=").append(s.isInRange).append('\n');
        }
        for (String event : diagnostics) text.append(event).append('\n');
        return text.toString();
    }

    private void notifyTelemetryUpdated(VehicleTelemetry updated) {
        List<OnBleTelemetryListener> copy;
        synchronized (this) { copy = new ArrayList<>(telemetryListeners); }
        main.post(() -> { for (OnBleTelemetryListener listener : copy) listener.onTelemetryUpdated(updated); });
    }

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
        disconnect();
        synchronized (this) { demoConnected = false; }
        connectionError = null;
        try {
            deviceName = device.getName();
            setState(ConnectionState.CONNECTING);
            gatt = device.connectGatt(context.getApplicationContext(), false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt connection, int status, int newState) {
                    main.post(() -> handleConnectionState(connection, status, newState));
                }
                private void handleConnectionState(BluetoothGatt connection, int status, int newState) {
                    if (connection != gatt) return;
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        setState(ConnectionState.CONNECTING);
                        try {
                            negotiatedMtu = 23;
                            if (!connection.requestMtu(250)) discoverServicesOnce(connection);
                            else main.postDelayed(() -> {
                                if (connection == gatt && state == ConnectionState.CONNECTING && writeChar == null)
                                    discoverServicesOnce(connection);
                            }, 2500);
                        } catch (Exception e) { failSmartAccessConnection(connection); }
                    } else {
                        connectionError = "Togg Smart Access BLE GATT bağlantısı kurulamadı. Araç yakınında Smart Access BLE beacon'ını tarayın.";
                        disconnect();
                    }
                }
                @Override public void onMtuChanged(BluetoothGatt connection, int mtu, int status) {
                    main.post(() -> {
                        if (connection != gatt) return;
                        if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu;
                        record("MTU " + negotiatedMtu);
                        if (state == ConnectionState.CONNECTING && writeChar == null) {
                            try { discoverServicesOnce(connection); }
                            catch (SecurityException error) { failSmartAccessConnection(connection); }
                        }
                    });
                }
                @Override public void onServicesDiscovered(BluetoothGatt connection, int status) {
                    main.post(() -> handleServicesDiscovered(connection, status));
                }
                private void handleServicesDiscovered(BluetoothGatt connection, int status) {
                    if (connection != gatt) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) { failSmartAccessConnection(connection); return; }
                    BluetoothGattService service = connection.getService(UUID_SERVICE_SMART_ACCESS);
                    if (service == null) { failSmartAccessConnection(connection); return; }
                    BluetoothGattCharacteristic command = service.getCharacteristic(UUID_CHAR_COMMAND);
                    BluetoothGattCharacteristic feedback = service.getCharacteristic(UUID_CHAR_FEEDBACK);
                    if (command == null || (command.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
                        failSmartAccessConnection(connection); return;
                    }
                    if (feedback == null) { failSmartAccessConnection(connection); return; }
                    synchronized (VehicleBleManager.this) { writeChar = command; feedbackChar = feedback; }
                    {
                        BluetoothGattDescriptor cccd = feedback.getDescriptor(UUID_CCCD);
                        byte[] subscription = (feedback.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                : (feedback.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                    ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : null;
                        try {
                            if (subscription == null || cccd == null || !connection.setCharacteristicNotification(feedback, true)) { failSmartAccessConnection(connection); return; }
                            if (Build.VERSION.SDK_INT >= 33) {
                                if (connection.writeDescriptor(cccd, subscription) != BluetoothGatt.GATT_SUCCESS) { failSmartAccessConnection(connection); return; }
                            } else {
                                cccd.setValue(subscription);
                                if (!connection.writeDescriptor(cccd)) { failSmartAccessConnection(connection); return; }
                            }
                        } catch (SecurityException e) { failSmartAccessConnection(connection); return; }
                    }
                }
                @Override public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int status) {
                    main.post(() -> {
                        if (connection != gatt || !UUID_CCCD.equals(descriptor.getUuid())) return;
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            cancel(connectionTimeout);
                            setState(ConnectionState.CONNECTED);
                            setAccessState(AccessState.TRANSPORT_READY, "BLE bağlı. Smart Access kurulumu ile oturumu yetkilendirin.");
                        } else failSmartAccessConnection(connection);
                    });
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) {
                    if (UUID_CHAR_FEEDBACK.equals(characteristic.getUuid())) {
                        byte[] copy = value.clone(); main.post(() -> receiveFrame(connection, copy));
                    }
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
                    if (Build.VERSION.SDK_INT < 33 && UUID_CHAR_FEEDBACK.equals(characteristic.getUuid())) {
                        byte[] value = characteristic.getValue();
                        if (value != null) { byte[] copy = value.clone(); main.post(() -> receiveFrame(connection, copy)); }
                    }
                }
                @Override public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) {
                    main.post(() -> {
                        if (connection != gatt || !UUID_CHAR_COMMAND.equals(characteristic.getUuid())) return;
                        if (noResponseWrite) {
                            if (status != BluetoothGatt.GATT_SUCCESS && session != null)
                                sessionFailure("BLE yazma başarısız (" + status + ").");
                            return;
                        }
                        if (!writeBusy) return;
                        cancel(writeTimeout);
                        if (status != BluetoothGatt.GATT_SUCCESS) { sessionFailure("BLE yazma başarısız (" + status + ")."); return; }
                        writeBusy = false; pumpWrites();
                    });
                }
            }, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) { connectionError = "BLE GATT bağlantısı başlatılamadı."; setState(ConnectionState.DISCONNECTED); }
            else {
                BluetoothGatt connecting = gatt;
                connectionTimeout = () -> {
                    if (connecting == gatt && state == ConnectionState.CONNECTING) {
                        connectionError = "Smart Access BLE bağlantısı zaman aşımına uğradı; tekrar tarayıp bağlanın.";
                        disconnect();
                    }
                };
                main.postDelayed(connectionTimeout, 15000);
            }
        } catch (SecurityException | IllegalArgumentException e) { setState(ConnectionState.DISCONNECTED); }
    }

    private void failSmartAccessConnection(BluetoothGatt connection) {
        if (connection != gatt) return;
        connectionError = "Bu cihazda Togg Smart Access BLE servisi bulunamadı. Medya Bluetooth eşleşmesi komut gönderemez; araç yakınında Smart Access BLE beacon'ını tarayın.";
        disconnect();
    }

    private void discoverServicesOnce(BluetoothGatt connection) {
        if (connection != gatt || servicesStarted) return;
        servicesStarted = true;
        try { if (!connection.discoverServices()) failSmartAccessConnection(connection); }
        catch (SecurityException error) { failSmartAccessConnection(connection); }
    }

    public void disconnect() {
        disconnect("BLE bağlantısı kesildi.");
    }

    private void disconnect(String reason) {
        cancel(connectionTimeout);
        generation++;
        clearSession(reason);
        certificate = null;
        deferredFrames.clear(); writeQueue.clear(); writeBusy = false; servicesStarted = false; negotiatedMtu = 23;
        BluetoothGatt previous;
        synchronized (this) {
            previous = gatt; gatt = null; demoConnected = false; deviceName = null;
            writeChar = null; feedbackChar = null;
        }
        if (previous != null) {
            try { previous.disconnect(); previous.close(); } catch (SecurityException ignored) { }
        }
        setState(ConnectionState.DISCONNECTED);
        setAccessState(AccessState.DISCONNECTED, "BLE bağlı değil.");
    }

    public void setDemoConnected(boolean connected) {
        disconnect();
        synchronized (this) { demoConnected = connected; deviceName = connected ? "Togg Demo" : null; }
        setState(connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED);
        setAccessState(connected ? AccessState.READY : AccessState.DISCONNECTED,
                connected ? "Demo: araç işlemleri simülasyondur." : "BLE bağlı değil.");
    }

    public void prepareSmartAccess(String truId, boolean isOwner, AccessCallback callback) {
        prepareCertificate(truId, isOwner, false, callback);
    }
    public void createSmartAccessCertificate(String truId, AccessCallback callback) {
        prepareCertificate(truId, true, true, callback);
    }
    private void prepareCertificate(String userId, boolean owner, boolean create, AccessCallback callback) {
        main.post(() -> {
            if (writeBusy || commandPending) { accessReply(callback, false, "Önce devam eden BLE işleminin tamamlanmasını bekleyin."); return; }
            if (trustPrefs == null) { accessReply(callback, false, "Şifreli Smart Access kayıt alanı açılamadı."); return; }
            if (storage.isDemoMode() || !storage.hasValidToken()) { accessReply(callback, false, "Gerçek Togg ID oturumu ile giriş yapın."); return; }
            final String vin, truId;
            try { vin = SmartAccessKeyStore.normalizeVin(storage.getVin()); truId = SmartAccessKeyStore.normalizeTruId(userId); }
            catch (IllegalArgumentException error) { accessReply(callback, false, error.getMessage()); return; }
            clearSession("Smart Access kurulumu yenileniyor.");
            int setup = ++setupGeneration;
            String account = currentAccount();
            setAccessState(AccessState.PREPARING_KEY, "Kullanıcı/VIN anahtarı hazırlanıyor.");
            new Thread(() -> {
                try {
                    keyStore.ensureKey(truId, vin);
                    main.post(() -> {
                        if (!setupMatches(setup, vin, account)) { accessReply(callback, false, "Kurulum sırasında araç veya oturum değişti."); return; }
                        setAccessState(AccessState.FETCHING_CERTIFICATE, create ? "Araç sahibi sertifikası oluşturuluyor." : "Yetkili araç sertifikası alınıyor.");
                        SmartAccessCertificateClient.Callback result = new SmartAccessCertificateClient.Callback() {
                            @Override public void onSuccess(SmartAccessCertificate value) {
                                main.post(() -> {
                                    if (!setupMatches(setup, vin, account)) { accessReply(callback, false, "Kurulum sırasında araç veya oturum değişti."); return; }
                                    certificate = value;
                                    trustPrefs.edit().putString("tru_id", truId).putString("vin", vin)
                                            .putBoolean("owner", owner).putString("account", account).apply();
                                    record("CERTIFICATE_ACCEPTED");
                                    if (isGattReady()) beginAuthentication(callback);
                                    else {
                                        setAccessState(AccessState.TRANSPORT_READY, "Sertifika alındı. BLE bağlanıp oturumu açın.");
                                        accessReply(callback, true, "Sertifika alındı. Smart Access BLE bağlantısı kurup oturumu açın.");
                                    }
                                });
                            }
                            @Override public void onError(String message) {
                                main.post(() -> {
                                    if (!setupMatches(setup, vin, account)) { accessReply(callback, false, "Kurulum değişti."); return; }
                                    record("CERTIFICATE_REJECTED" + httpCode(message));
                                    setAccessState(AccessState.ERROR, message);
                                    accessReply(callback, false, message);
                                });
                            }
                        };
                        if (create) certificateClient.createOwner(truId, vin, result);
                        else certificateClient.fetch(truId, vin, owner, result);
                    });
                } catch (Exception error) {
                    main.post(() -> {
                        if (setup == setupGeneration) setAccessState(AccessState.ERROR, "Android anahtar kaydı hazırlanamadı.");
                        accessReply(callback, false, "Android anahtar kaydı hazırlanamadı.");
                    });
                }
            }, "smart-access-key").start();
        });
    }
    private boolean setupMatches(int setup, String vin, String account) {
        return setup == setupGeneration && vin.equalsIgnoreCase(storage.getVin())
                && account.equals(currentAccount()) && storage.hasValidToken() && !storage.isDemoMode();
    }
    private static String httpCode(String message) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("HTTP\\s*(\\d{3})").matcher(message == null ? "" : message);
        return match.find() ? " HTTP=" + match.group(1) : "";
    }
    public void authenticateSmartAccess(AccessCallback callback) {
        main.post(() -> {
            if (writeBusy || commandPending) { accessReply(callback, false, "Önce devam eden BLE işleminin tamamlanmasını bekleyin."); return; }
            if (!isGattReady() || isDemoConnected()) { accessReply(callback, false, "Önce gerçek Smart Access BLE bağlantısı kurun."); return; }
            if (trustPrefs == null || getConfiguredTruId().isEmpty()) { accessReply(callback, false, "Önce Smart Access sertifika kurulumunu tamamlayın."); return; }
            if (!storage.hasValidToken() || !currentAccount().equals(trustPrefs.getString("account", ""))) {
                accessReply(callback, false, "Togg ID oturumu değişti; sertifikayı yeniden alın."); return;
            }
            certificate = certificateClient.getCached(getConfiguredTruId(), storage.getVin());
            beginAuthentication(callback);
        });
    }
    private void beginAuthentication(AccessCallback callback) {
        if (!isGattReady() || certificate == null) { accessReply(callback, false, "BLE bağlantısı veya sertifika hazır değil."); return; }
        if (writeBusy || commandPending) { accessReply(callback, false, "Devam eden BLE işlemi var; oturumu daha sonra açın."); return; }
        generation++;
        clearSession("Oturum yeniden başlatılıyor.");
        String truId = getConfiguredTruId(), vin = storage.getVin();
        try {
            if (!certificate.isValidFor(truId, vin, keyStore.getPublicKey(truId, vin)))
                throw new IllegalArgumentException("Sertifika kullanıcı/VIN/telefon anahtarı ile eşleşmiyor veya süresi dolmuş.");
            sessionTruId = truId; sessionVin = vin; sessionAccount = currentAccount();
            int epoch = generation;
            String pinKey = "sam_key." + digest(truId + "|" + vin);
            byte[] pinned = Base64.decode(trustPrefs.getString(pinKey, ""), Base64.NO_WRAP);
            authenticationCallback = callback;
            session = new SmartAccessSession(certificate, pinned, data -> keyStore.sign(truId, vin, data),
                    this::enqueueWrite, new SmartAccessSession.Listener() {
                @Override public void onStage(String message) {
                    if (epoch == generation) setAccessState(commandPending ? AccessState.COMMAND_PENDING
                            : session != null && session.isReady() ? AccessState.READY : AccessState.AUTHENTICATING, message);
                }
                @Override public boolean onCarPublicKey(byte[] key) {
                    if (epoch != generation || trustPrefs == null || !validSessionBinding()) return false;
                    // Same initial SAM key acquisition as Trumore. Bind the first
                    // manually selected connection to this user/VIN; never replace a pin.
                    String existing = trustPrefs.getString(pinKey, "");
                    byte[] normalized = key.length == 65 && key[0] == 4 ? Arrays.copyOfRange(key, 1, 65) : key;
                    if (normalized.length != 64) return false;
                    if (!existing.isEmpty() && !MessageDigest.isEqual(normalized, Base64.decode(existing, Base64.NO_WRAP))) return false;
                    return trustPrefs.edit().putString(pinKey, Base64.encodeToString(normalized, Base64.NO_WRAP)).commit();
                }
                @Override public void onReady() {
                    if (epoch != generation) return;
                    cancel(authTimeout);
                    setAccessState(AccessState.READY, "Smart Access oturumu yetkilendirildi; araç durumu imzayla doğrulandı.");
                    AccessCallback pending = authenticationCallback; authenticationCallback = null;
                    accessReply(pending, true, "Smart Access hazır. Kapı, bagaj ve başlatma komutları denenebilir.");
                    startStatusPolling(epoch);
                }
                @Override public void onStatus(SmartAccessProtocol.RawStatus status, long counter) {
                    if (epoch != generation || !validSessionBinding()) { sessionFailure("Oturum veya araç değişti."); return; }
                    lastStatusAt = System.currentTimeMillis();
                    record("SIGNED_STATUS lock=" + status.lockState + " trunk=" + status.ajarTrunkLid + " power=" + status.vehStat);
                    if (telemetry != null && vin.equalsIgnoreCase(telemetry.vin)) {
                        telemetry = telemetry.withSmartAccessStatus(status);
                        notifyTelemetryUpdated(telemetry);
                    }
                    if (!commandPending && session != null && session.isReady())
                        setAccessState(AccessState.READY, "Smart Access hazır; imzalı araç durumu güncel.");
                }
                @Override public void onCommandResult(boolean success, String message) {
                    if (epoch != generation) return;
                    cancel(commandTimeout);
                    lastCommandResult = message;
                    record(success ? "COMMAND_RESULT_CONFIRMED" : "COMMAND_RESULT_UNCONFIRMED");
                    CommandCallback pending = commandCallback; commandCallback = null; commandPending = false;
                    reply(pending, success, message, telemetry);
                    if (session != null && session.isReady()) setAccessState(AccessState.READY, message);
                }
                @Override public void onError(String message) { if (epoch == generation) sessionFailure(message); }
            });
            session.setPacketCapacity(Math.min(247, Math.max(20, negotiatedMtu - 3)));
            setAccessState(AccessState.AUTHENTICATING, "Araç sertifika isteği bekleniyor.");
            session.start();
            authTimeout = () -> sessionFailure("Yetkilendirme yanıtı alınamadı. Araçtaki erişim cihazları ekranını ve BLE bağlantısını kontrol edin.");
            main.postDelayed(authTimeout, 30000);
            while (session != null && !deferredFrames.isEmpty()) session.receive(deferredFrames.removeFirst());
        } catch (Exception error) {
            authenticationCallback = callback;
            sessionFailure("Smart Access oturumu açılamadı: " + error.getMessage());
        }
    }
    private void startStatusPolling(int epoch) {
        cancel(statusPoll);
        statusPoll = () -> {
            if (epoch != generation || session == null) return;
            if (!validSessionBinding()) { sessionFailure("Togg ID oturumu veya VIN değişti."); return; }
            if (!session.isReady()) { sessionFailure("Smart Access oturumu artık hazır değil; yeniden yetkilendirin."); return; }
            if (System.currentTimeMillis() - session.getStatusAt() > 20000) {
                sessionFailure("İmzalı araç durum bildirimi kesildi; BLE bağlantısını yeniden kurun."); return;
            }
            if (!commandPending && !writeBusy && writeQueue.isEmpty()) session.requestStatus();
            main.postDelayed(statusPoll, 5000);
        };
        main.postDelayed(statusPoll, 5000);
    }
    private boolean validSessionBinding() {
        return trustPrefs != null && certificate != null && certificate.isValidAt(System.currentTimeMillis())
                && !storage.isDemoMode() && storage.hasValidToken()
                && sessionVin.equalsIgnoreCase(storage.getVin()) && sessionTruId.equals(getConfiguredTruId())
                && sessionAccount.equals(currentAccount());
    }
    private String currentAccount() {
        String token = storage.getAccessToken();
        try {
            String[] segments = token.split("\\.");
            if (segments.length == 3) {
                JSONObject claims = new JSONObject(new String(Base64.decode(segments[1], Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8));
                String subject = claims.optString("sub", "");
                if (!subject.isEmpty()) return digest(subject);
            }
        } catch (Exception ignored) { }
        return digest(token == null ? "" : token);
    }
    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte value : bytes) output.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return output.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private void receiveFrame(BluetoothGatt connection, byte[] bytes) {
        if (connection != gatt) return;
        rxCount++;
        record("RX type=" + (bytes.length == 0 ? -1 : bytes[0] & 255) + " bytes=" + bytes.length);
        try {
            if (bytes.length == 8 && (bytes[0] & 255) >= 0x10 && (bytes[0] & 255) <= 0x14) {
                SmartAccessProtocol.RequestFrame request = SmartAccessProtocol.decodeRequest(bytes);
                record("RX_REQUEST state=" + request.state + " length=" + request.rawIuu + " next=" + request.rawJuu);
            } else if (bytes.length >= 8) {
                SmartAccessProtocol.DataFrame frame = SmartAccessProtocol.decodeDataFrame(bytes);
                record("RX_DATA block=" + frame.block + "/" + frame.totalBlocks + " total=" + frame.totalLength);
            }
        } catch (IllegalArgumentException invalid) { record("RX_INVALID_HEADER"); }
        if (session == null) {
            if (bytes.length == 8 && (bytes[0] & 255) == SmartAccessProtocol.REQUEST_USER_CERTIFICATE
                    && (bytes[1] & 255) == SmartAccessProtocol.RequestState.FIRST_PACK_WAITING.ordinal()) {
                deferredFrames.clear();
                deferredFrames.add(bytes.clone());
            }
            return;
        }
        if (!validSessionBinding()) { sessionFailure("Oturum veya VIN değişti."); return; }
        session.receive(bytes);
    }
    private void enqueueWrite(byte[] bytes) {
        if (!isGattReady() || gatt == null || writeChar == null) { sessionFailure("BLE GATT yazma kanalı hazır değil."); return; }
        if (bytes.length > Math.min(247, Math.max(20, negotiatedMtu - 3)) || writeQueue.size() >= 32) {
            sessionFailure("BLE paket kapasitesi veya kuyruk sınırı aşıldı."); return;
        }
        writeQueue.add(bytes.clone()); pumpWrites();
    }
    private void pumpWrites() {
        if (writeBusy || writeQueue.isEmpty()) return;
        if (gatt == null || writeChar == null) { sessionFailure("BLE bağlantısı kesildi."); return; }
        byte[] bytes = writeQueue.removeFirst();
        noResponseWrite = (writeChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0;
        int type = noResponseWrite ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        writeBusy = true;
        try {
            boolean accepted;
            if (Build.VERSION.SDK_INT >= 33) accepted = gatt.writeCharacteristic(writeChar, bytes, type) == BluetoothGatt.GATT_SUCCESS;
            else {
                writeChar.setWriteType(type); writeChar.setValue(bytes);
                accepted = gatt.writeCharacteristic(writeChar);
            }
            if (!accepted) { sessionFailure("BLE yazma isteği kabul edilmedi."); return; }
            txCount++; record("TX type=" + (bytes[0] & 255) + " bytes=" + bytes.length);
            int epoch = generation;
            writeTimeout = () -> {
                if (epoch != generation || !writeBusy) return;
                if (noResponseWrite) { writeBusy = false; pumpWrites(); }
                else sessionFailure("BLE yazma onayı zaman aşımına uğradı.");
            };
            main.postDelayed(writeTimeout, noResponseWrite ? 40 : 5000);
        } catch (SecurityException error) { sessionFailure("Bluetooth erişim izni kaybedildi."); }
    }
    private void sessionFailure(String message) {
        record("SESSION_ERROR"); disconnect(message);
        setAccessState(AccessState.ERROR, message);
    }
    private void clearSession(String message) {
        cancel(authTimeout); cancel(commandTimeout); cancel(writeTimeout); cancel(statusPoll);
        if (session != null) session.close(); session = null;
        writeQueue.clear(); writeBusy = false; lastStatusAt = 0;
        AccessCallback auth = authenticationCallback; authenticationCallback = null;
        accessReply(auth, false, message);
        CommandCallback command = commandCallback; commandCallback = null; commandPending = false;
        if (command != null) { lastCommandResult = message; reply(command, false, message, telemetry); }
    }
    private void cancel(Runnable runnable) { if (runnable != null) main.removeCallbacks(runnable); }
    private void accessReply(AccessCallback callback, boolean success, String message) {
        if (callback != null) main.post(() -> callback.onResult(success, message));
    }

    public void sendCommand(VehicleCommand command, CommandCallback callback) {
        main.post(() -> {
            if (command == null) { reply(callback, false, "Geçersiz araç komutu.", telemetry); return; }
            if (isDemoConnected()) { sendDemoCommand(command, callback); return; }
            String accessError = getCommandAccessError(false, isCommandReady());
            if (accessError != null) { reply(callback, false, accessError, telemetry); return; }
            if (commandPending) { reply(callback, false, "Önceki araç komutu tamamlanmadı.", telemetry); return; }
            if (command == VehicleCommand.EMOTOR_STOP) { reply(callback, false, "READY'den aksesuar moduna dönüş komutu henüz teyit edilmedi; gönderilmedi.", telemetry); return; }
            try {
                SmartAccessProtocol.CarCommand value;
                switch (command) {
                    case LOCK: value = SmartAccessProtocol.CarCommand.LOCK; break;
                    case UNLOCK: value = SmartAccessProtocol.CarCommand.UNLOCK; break;
                    case TRUNK_OPEN: value = SmartAccessProtocol.CarCommand.TRUNK_OPEN; break;
                    case TRUNK_CLOSE: value = SmartAccessProtocol.CarCommand.TRUNK_CLOSE; break;
                    case TRUNK: value = session.getStatus().ajarTrunkLid ? SmartAccessProtocol.CarCommand.TRUNK_CLOSE : SmartAccessProtocol.CarCommand.TRUNK_OPEN; break;
                    default: value = SmartAccessProtocol.CarCommand.EMOTOR_START;
                }
                commandCallback = callback; commandPending = true;
                lastCommandResult = "Komut sonucu bekleniyor.";
                session.sendCommand(value);
                setAccessState(AccessState.COMMAND_PENDING, "İmzalı komut ve araç yanıtı bekleniyor.");
                SmartAccessSession active = session;
                commandTimeout = () -> { if (session == active) active.timeoutCommand(); };
                main.postDelayed(commandTimeout, 8000);
            } catch (Exception error) {
                commandCallback = null; commandPending = false; lastCommandResult = error.getMessage();
                reply(callback, false, error.getMessage(), telemetry);
            }
        });
    }
    private void sendDemoCommand(VehicleCommand command, CommandCallback callback) {
        VehicleTelemetry snapshot;
        boolean demo;
        synchronized (this) { snapshot = telemetry; demo = demoConnected; }
        String authorizationError = getCommandAuthorizationError(command);
        if (authorizationError != null) {
            reply(callback, false, authorizationError, snapshot);
            return;
        }
        if (snapshot == null) { reply(callback, false, "Araç durumu henüz yüklenmedi.", null); return; }
        main.postDelayed(() -> {
            if (!isDemoConnected()) { reply(callback, false, "Demo bağlantısı kesildi.", snapshot); return; }
            VehicleTelemetry updated;
            String message;
            switch (command) {
                case LOCK: updated = snapshot.withLock(true); message = "Kapılar Kilitlendi"; break;
                case UNLOCK: updated = snapshot.withLock(false); message = "Kapılar Açıldı"; break;
                case TRUNK: updated = snapshot.withTrunk(!snapshot.trunkOpen); message = updated.trunkOpen ? "Bagaj Açıldı" : "Bagaj Kapatıldı"; break;
                case TRUNK_OPEN: updated = snapshot.withTrunk(true); message = "Bagaj Açıldı"; break;
                case TRUNK_CLOSE: updated = snapshot.withTrunk(false); message = "Bagaj Kapatıldı"; break;
                case EMOTOR_START: updated = snapshot.withReadyToDrive(true); message = "e-Motor Hazır"; break;
                default: updated = snapshot.withReadyToDrive(false); message = "e-Motor Kapatıldı";
            }
            synchronized (VehicleBleManager.this) { telemetry = updated; }
            reply(callback, true, "Togg Demo: Simülasyon (" + message + ")", updated);
        }, 1200);
    }

    static String getCommandAccessError(boolean demo, boolean authenticated) {
        return demo || authenticated ? null : "Önce Smart Access kurulumunu tamamlayıp oturumu yetkilendirin.";
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
            case TRUNK_OPEN:
            case TRUNK_CLOSE:
                return key.canTrunk ? null : "Dijital anahtar bagaj açma yetkisine sahip değil.";
            case EMOTOR_START:
                return key.canStartDrive ? null : "Dijital anahtar aracı çalıştırma yetkisine sahip değil.";
            default:
                return null;
        }
    }

    private void reply(CommandCallback callback, boolean success, String message, VehicleTelemetry value) {
        if (callback != null) main.post(() -> callback.onFeedback(success, message, value));
    }
}
