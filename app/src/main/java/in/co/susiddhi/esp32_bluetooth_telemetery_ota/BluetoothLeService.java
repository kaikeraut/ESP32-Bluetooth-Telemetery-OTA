package in.co.susiddhi.esp32_bluetooth_telemetery_ota;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import androidx.annotation.Nullable;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;


public class BluetoothLeService extends Service {
    public static final String TAG = "BluetoothLeService";
    public final static String ACTION_GATT_CONNECTED =
            "com.example.dxe.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.dxe.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.dxe.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_WRITE_CALLBACK = "com.example.dxe.le.ACTION_DATA_WRITE_CALLBACK";
    public static final String ACTION_CHARACTERISTICS_CHANGED = "com.example.dxe.le.ACTION_CHARACTERISTICS_CHANGED";
    public static final String ACTION_MTU_CHANGED = "com.example.dxe.le.ACTION_MTU_CHANGED";;

    public static final String ACTION_DATA_AVAILABLE = "com.example.dxe.le.ACTION_DATA_AVAILABLE";
    public static final String EXTRA_DATA = "com.example.dxe.le.EXTRA_DATA";

    private final static String DXE_WRITE_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
    private final static String DXE_WRITE_CHAR_UUID =  "0000ff01-0000-1000-8000-00805f9b34fb";
    private final static String DXE_WRITE_DESC_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;


    private int mConnectionState;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private Binder binder = new LocalBinder();
    private String mBluetoothDeviceAddress;
    private BluetoothGattDescriptor mDescriptor;

    public BluetoothLeService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        close();
    }

    private void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
    class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
    public boolean initialize() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        Log.d(TAG, "initialize: bluetooth Adapter done ");
        return true;
    }
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        try {

            Log.d(TAG, "Trying to create a new connection. Address:"+address);
            final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            // connect to the GATT server on the device
            //mBluetoothGatt = device.connectGatt(this, true, bluetoothGattCallback);
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {


                    if (device != null) {
                        Log.d(TAG, "run: connectGatt");
                        mBluetoothGatt = device.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                        //scanLeDevice(false);// will stop after first device detection
                    }
                }
            });
            mBluetoothDeviceAddress = address;
            mConnectionState = STATE_CONNECTING;
            return true;
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Device not found with provided address.");
            return false;
        }
        // connect to the GATT server on the device
    }
    private BluetoothGattCharacteristic characEsp32;
    private final BluetoothGattCallback bluetoothGattCallback;
{
        bluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                super.onPhyUpdate(gatt, txPhy, rxPhy, status);
                Log.d(TAG, "onPhyUpdate: ");
                        
            }

            @Override
            public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                super.onPhyRead(gatt, txPhy, rxPhy, status);
                Log.d(TAG, "onPhyRead: ");
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.d(TAG, "onConnectionStateChange: ");
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // successfully connected to the GATT Server
                    mConnectionState = STATE_CONNECTED;
                    broadcastUpdate(ACTION_GATT_CONNECTED);
                    mBluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // disconnected from the GATT Server
                    mConnectionState = STATE_DISCONNECTED;
                    broadcastUpdate(ACTION_GATT_DISCONNECTED);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.d(TAG, "onServicesDiscovered: ");
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    List<BluetoothGattService> services = gatt.getServices();
                    for (BluetoothGattService s : services) {
                        Log.d(TAG, "onServicesDiscovered: service uuid:"+s.getUuid().toString());
                    }
                    BluetoothGattService Service = mBluetoothGatt.getService(UUID.fromString(DXE_WRITE_SERVICE_UUID));
                    List<BluetoothGattCharacteristic>  characteristics = Service.getCharacteristics();
                    for (BluetoothGattCharacteristic ch : characteristics) {
                        Log.d(TAG, "onServicesDiscovered: characteristics uuid:"+ch.getUuid().toString());
                    }
                    characEsp32 = Service.getCharacteristic(UUID.fromString(DXE_WRITE_CHAR_UUID));
                    if (characEsp32 == null) {
                        Log.e(TAG, "characEsp32 not found!");
                    }
                    List<BluetoothGattDescriptor>  descriptors = characEsp32.getDescriptors();
                    for (BluetoothGattDescriptor desc : descriptors) {
                        Log.d(TAG, "onServicesDiscovered: descriptors uuid:"+desc.getUuid().toString());
                    }
                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                    mBluetoothGatt.requestMtu(MainActivity.MTU_SIZE_REQUESTED);
                } else {
                    Log.w(TAG, "onServicesDiscovered received: " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.d(TAG, "onCharacteristicRead: ");
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                Log.d(TAG, "onCharacteristicWrite:WRITE CALLBACK status:"+status+" Time:"+ System.currentTimeMillis());
                //if (status == BluetoothGatt.GATT_SUCCESS)

                {
                    broadcastUpdate(ACTION_DATA_WRITE_CALLBACK, characteristic);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.d(TAG, "onCharacteristicChanged: len:"+characteristic.getValue().length);
                broadcastUpdate(ACTION_CHARACTERISTICS_CHANGED, characteristic);

            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                Log.d(TAG, "onDescriptorRead: ");
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                Log.d(TAG, "onDescriptorWrite: ");
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                Log.d(TAG, "onReliableWriteCompleted: ");
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                Log.d(TAG, "onReadRemoteRssi: ");
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                Log.d(TAG, "onMtuChanged: mtu:"+mtu+ " status:"+ status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mBluetoothGatt = gatt;
                    BluetoothGattService Service = mBluetoothGatt.getService(UUID.fromString(DXE_WRITE_SERVICE_UUID));
                    characEsp32 = Service.getCharacteristic(UUID.fromString(DXE_WRITE_CHAR_UUID));
                    if (characEsp32 == null) {
                        Log.e(TAG, "characEsp32 not found!");
                    }
                    MainActivity.MTU_SIZE_GOT = mtu;
                    broadcastUpdate(ACTION_MTU_CHANGED);
                }
            }
        };
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        // For all other profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();
        intent.putExtra(EXTRA_DATA, data);
        /*if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
        }*/
        sendBroadcast(intent);
    }
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        mDescriptor = characEsp32.getDescriptor(UUID.fromString(DXE_WRITE_DESC_UUID));
        if (mDescriptor == null) {
            Log.e(TAG, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characEsp32.getUuid()));
            return ;
        }
        Log.d(TAG, "setNotify: notif:"+ mBluetoothGatt.setCharacteristicNotification(characEsp32, true));
        byte[] value;
        int properties = characEsp32.getProperties();
        if ((properties & PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characEsp32.getUuid()));
            return ;
        }
        final byte[] finalValue = true ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        // Then write to descriptor
        mDescriptor.setValue(finalValue);
        boolean result;
        result = mBluetoothGatt.writeDescriptor(mDescriptor);
        Log.d(TAG, "setNotify: " + result);
        if(!result)

        {
            Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", mDescriptor.getUuid()));
            //completedCommand();
        }else{
            Log.d(TAG, "Notification Enabled for "+mDescriptor.getUuid() + " Val:"+finalValue);

        }
    }

    public boolean writeCharacteristics(BluetoothGattCharacteristic characteristic, byte[] dataToSend){
        if (characEsp32 == null) {
            Log.e(TAG, "characEsp32 not found!");
            return false;
        }
        characEsp32.setValue(dataToSend);
        boolean status1 = mBluetoothGatt.writeCharacteristic(characEsp32);
        if(status1 == false) Log.e(TAG, "writeCharacteristics: failed to write");
        return status1;
    }
}