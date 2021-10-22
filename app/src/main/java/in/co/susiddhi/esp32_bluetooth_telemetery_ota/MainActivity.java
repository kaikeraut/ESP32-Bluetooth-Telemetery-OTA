package in.co.susiddhi.esp32_bluetooth_telemetery_ota;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;

public class MainActivity extends AppCompatActivity {
    public static final int PICKFILE_RESULT_CODE            = 1;
    private static final int MY_PERMISSION_REQUEST_CODE     = 1001;
    public final static int MTU_SIZE                        = 500;
    private final static int TEXT_APPEND                    = 1;
    private final static int TEXT_NOT_APPEND                = 0;
    private final static int TEXT_OTA_PERCENT               = 2;
    private final static int FW_DETAILS_SIZE                = 256;
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int WRITE_STATUS_FAIL = 2;
    private static final int WRITE_STATUS_DEFAULT = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private final static String strScanStart = "Scan-START";
    private final static String strScanStop = "Scan-STOP";
    private final static String strDisconnect = "Disconnect";
    private final static String strConnect = "Connect";


    private final static String strTelemetryStart = "Telemetry-Start";
    private final static String strTelemetryStop = "Telemetry-Stop";

    private final static String strOtaStart = "Start OTA";
    private final static String strOtaStop = "Stop OTA";
    private static final String TAG = "MainActivity";

    private static int intScanning = 0, intTelemetryStarted = 0;

    Button btnScan, btnTelemetry, btnOTA;
    TextView textViewLog;
    EditText editTextMAC;
    private String connectedBluetoothMAC, connectedBluetoothName;
    private String esp32MACAddr;
    private Uri otaFileUri;
    private String otaFilePath;
    //***** Bluetooth  Details ****
    private static final long SCAN_PERIOD = 30000;
    private Handler handlerScanner = new Handler();
    Map<String, String> bleScanMacNameMap = new HashMap<>();
    private String screenLogMsgforOTA = "";
    long ota_started_time = 0;
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;
    BluetoothDevice myDevice = null;
    BluetoothGatt mBluetoothGatt = null;
    BluetoothGattCharacteristic characEsp32 = null;

    private final static String OTA_FILE_NAME = "ota_sample_bin.bin";
    private final static String ESP32_MAC = "24:0A:C4:FA:41:32";
    private final static String WRITE_SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb";
    private final static String WRITE_CHAR_UUID =  "0000ff01-0000-1000-8000-00805f9b34fb";
    private final static String WRITE_DESC_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    public static  final int MSG_SUB_NOTIFY = 10;
    public static  final int MSG_START_TELEMETRY = 12;
    public static  final int MSG_STOP_TELEMETRY = 13;
    public static  final int MSG_PROCESS_BLE_PACKETS = 14;
    public static  final int MSG_PROCESS_START_OTA = 15;
    public static  final int MSG_GET_FW_DETAILS = 16;

    /* Bluetooth message format index send to APP */
    public static  final int BT_HEADER_START_INDEX             =    0;
    public static  final int BT_HEADER_MSG_TYPE_INDEX          =    1;
    public static  final int BT_HEADER_MSG_ACTION_INDEX        =    2;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX0   =    3;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX1   =    4;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX2   =    5;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX3   =    6;
    public static  final int BT_HEADER_END_INDEX               =    7;

            /* Message Header informations */
/** HEADER_START -- MSG_TYPE -- MSG_ACTION -- HEADER_END */
    public static  final int  BT_MSG_HEADER_FIRST_BYTE            =       0xFE;
    public static  final int  BT_MSG_HEADER_LAST_BYTE             =       0xEF;

    public static  final int  BT_MSG_HEADER_MSG_TYPE_TELEMETRY    =       0x10;
    public static  final int  BT_MSG_HEADER_MSG_TYPE_OTA          =       0x11;
    public static  final int  BT_MSG_HEADER_MSG_TYPE_FIRM_VER     =       0x12;

    public static  final int  BT_MSG_HEADER_MSG_ACTION_DEFAULT     =       0x00;
    public static  final int  BT_MSG_HEADER_MSG_ACTION_START       =       0x01;
    public static  final int  BT_MSG_HEADER_MSG_ACTION_CONTINUE    =       0x02;
    public static  final int  BT_MSG_HEADER_MSG_ACTION_END         =       0x03;
    public static  final int  BT_MSG_HEADER_MSG_ACTION_ABORT       =       0x04;
    public static  final int  BT_MSG_HEADER_MSG_ACTION_FW_REQUEST  =       0x05;
    public static  final int  BT_MSG_HEADER_MSG_ACTION_FW_RESPONSE =       0x06;

    public static  final int  OTA_ERROR_DEFAULT                   =       0;
    public static  final int  OTA_ERROR_CODE_INTERNAL_ERROR       =       0x1000;
    public static  final int  OTA_ERROR_CODE_INVALID_CHECKSUM     =       0x1001;
    public static  final int  OTA_ERROR_CODE_INVALID_FW_VER       =       0x1002;
    public static  final int  OTA_ERROR_CODE_INVALID_HEADER       =       0x1003;
    public static  final int  OTA_ERROR_CODE_INVALID_FILESIZE     =       0x1004;
    public static  final int  OTA_ERROR_CODE_SAME_FW_VER          =       0x1005;

    public static  final int MSG_OTA_START                        =       0x12EC;
    public static  final int MSG_OTA_PROCESS	                  =       0x12ED;
    public static  final int MSG_OTA_COMPLETE                     =       0x12EF;

    public static  final int BLUETOOTH_CONNECTED = 1;
    public static  final int BLUETOOTH_DISCONNECTED = 2;
    private int esp32BluetoothStatus = 0;
    private int bleScanCount = 0;
    private int abortOTAProcess = 0;
    private String deviceAddress;
    private String deviceFirmwareVersion;


    void setOTA_Abort(int value){
        abortOTAProcess = value;
    }
    int getOTA_Abort(){
        return abortOTAProcess;
    }
    int currentWriteStatus = 0;
    CountDownTimer scannerTimer = null;

    long ota_total_file_length = 0;
    long ota_current_bytes_transferred = 0;
    long ota_delta_data_transferred = 0, ota_delta_duration = 0;
    int oncharactersticsWriteCount = 0;

    //***** Bluetooth  Details ****

    SharedPreferences sharedPreferences = null;
    String PREF_MAC_KEY =  "in.co.susiddhi.esp32_ble_tele_ota_MAC";
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = this.getSharedPreferences("in.co.susiddhi.esp32_ble_tele_ota", Context.MODE_PRIVATE);
        intTelemetryStarted = 0;
        intScanning = 0;
        textViewLog = (TextView)findViewById(R.id.textViewLogData);
        btnOTA = (Button) findViewById(R.id.button3OTA);
        btnTelemetry = (Button) findViewById(R.id.button2Telemetry);
        btnScan = (Button) findViewById(R.id.buttonScan);
        editTextMAC = (EditText)findViewById(R.id.editTextNumberMAC);
        editTextMAC.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        esp32BluetoothStatus = BLUETOOTH_DISCONNECTED;
        esp32MACAddr = sharedPreferences.getString(PREF_MAC_KEY, "");
        editTextMAC.setText(esp32MACAddr);

        ActivityCompat.requestPermissions( this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
        }, 0);

        ActivityCompat.requestPermissions( this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        }, MY_PERMISSION_REQUEST_CODE);
        InputFilter[] macAddressFilters = new InputFilter[1];

        // Bluetooth Start **********************************************
        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }
        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
        // Bluetooth End **********************************************
        macAddressFilters[0] = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
                    if (resultingTxt.matches("([0-9a-fA-F][0-9a-fA-F]:){0,5}[0-9a-fA-F]")) {

                    } else if (resultingTxt.matches("([0-9a-fA-F][0-9a-fA-F]:){0,4}[0-9a-fA-F][0-9a-fA-F]")) {
                        return source.subSequence(start, end) + ":";
                    } else if (resultingTxt.matches("([0-9a-fA-F][0-9a-fA-F]:){0,5}[0-9a-fA-F][0-9a-fA-F]")) {

                    } else {
                        return "";
                    }
                }
                return null;
            }
        };
        editTextMAC.setFilters(macAddressFilters);

        btnScan.setText(strScanStart);
        btnOTA.setText(strOtaStart);
        btnTelemetry.setText(strTelemetryStart);

        btnOTA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOTA_Abort(OTA_ERROR_DEFAULT);
                String otaFolderPath = "";
                File otaDir = null;
                try {
                    otaFolderPath = Environment.getExternalStorageDirectory().getPath()
                            + File.separator + "documents/" + File.separator;
                    otaDir = new File(otaFolderPath);
                    if (!otaDir.exists()) {
                        //otaDir.mkdir();
                        otaFolderPath = Environment.getExternalStorageDirectory().getPath()
                                + File.separator + "Documents/";
                        otaDir = new File(otaFolderPath);
                        if (otaDir.exists()) {

                        }
                    }
                }catch(Exception e){
                    Log.e(TAG, "onClick: " + e);
                }
                if(mConnected == false){
                    setLogMessage("Device not Connected !!!", TEXT_APPEND);
                    return;
                }
                setLogMessage("OTA TODO", TEXT_APPEND);
                Log.d(TAG, "onClick: OTA Click");

                //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                //intent.addCategory(Intent.CATEGORY_OPENABLE);
                //Intent intent = new Intent(Intent.ACTION_PICK);
                otaFolderPath += "DXe-OTA" + File.separator;
                Log.d(TAG, "onClick: otaFolderPath:"+otaFolderPath);

                otaDir = new File(otaFolderPath);
                if(!otaDir.exists()){
                    otaDir.mkdir();
                }
                File[] files = otaDir.listFiles();
                int filesFound = 0;
                if(files != null){
                    filesFound = files.length;
                }
                Log.d("Files", "Size: "+ filesFound);
                for (int i = 0; i < filesFound; i++)
                {
                    Log.d("Files", "FileName:" + files[i].getName());
                    Log.d(TAG, "onClick: path:"+ files[i].getPath() + " Can Read:"+ files[i].canRead());
                    Log.d(TAG, "onClick: file len:"+ files[i].length());
                }
                if(filesFound == 0) {
                    setLogMessage("Copy OTA File to  below location and Start Again:" + otaFolderPath, TEXT_APPEND);
                }else {
                    Message msg = new Message();
                    msg.what = MSG_GET_FW_DETAILS;
                    //msg.obj = otaFolderPath;
                    mHandler.sendMessage(msg);

//                    String otaFilePath = otaFolderPath + "/" + OTA_FILE_NAME;
//                    File file = new File(otaFilePath);
//                    Log.d(TAG, "onClick: file length: " + file.length());
//                    byte[] fileData = new byte[(int) file.length()];
//                    DataInputStream dis = null;
//                    try {
//                        dis = new DataInputStream(new FileInputStream(file));
//                        dis.readFully(fileData);
//                        dis.close();
//                        Log.d(TAG, "onClick: FILE DATA LEN:" + fileData.length);
//                        //String data = String.format("%x")
//                        //Log.d(TAG, "onClick: ");
//
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                }


/*
                if(!otaDir.exists()){
                    otaDir.mkdir();
                    Log.d(TAG, "onCli!ck: Creating Directory:"+ otaFolderPath);
                    setLogMessage("Creating Directory:"+otaFolderPath, TEXT_APPEND);

                    File[] files = otaDir.listFiles();
                    Log.d("Files", "Size: "+ files.length);
                    if(files.length == 0) {
                        setLogMessage("Copy OTA File to  below location and Start Again:" + otaFolderPath, TEXT_APPEND);
                    }else{
                        Uri uri = Uri.parse(otaFolderPath);
                        intent.setDataAndType(uri, "application/octet-stream");
                        startActivityForResult(Intent.createChooser(intent, "Open folder"), PICKFILE_RESULT_CODE);
                    }
                }else{
                    File[] files = otaDir.listFiles();
                    Log.d("Files", "Size: "+ files.length);
                    if(files.length == 0) {
                        setLogMessage("Copy OTA File to below location and Start Again:" + otaFolderPath, TEXT_APPEND);
                    }else{
                        Uri uri = Uri.parse(otaFolderPath);
                        intent.setDataAndType(uri, "application/octet-stream");
                        startActivityForResult(Intent.createChooser(intent, "Open folder"), PICKFILE_RESULT_CODE);
                    }
                }
*/

            }
        });

        btnTelemetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("TAG", "btnTelemetryt onClick: " + intTelemetryStarted);
                if(mConnected == false){
                    setLogMessage("Device not Connected !!!", TEXT_APPEND);
                    return;
                }
                if (intTelemetryStarted == 0) {
                    btnTelemetry.setText(strTelemetryStop);
                    setLogMessage("Telemetry Started", TEXT_APPEND);
                    intTelemetryStarted = 1;
                    Message msg = new Message();
                    msg.what = MSG_START_TELEMETRY;
                    mHandler.sendMessage(msg);

                } else {
                    btnTelemetry.setText(strTelemetryStart);
                    setLogMessage("Telemetry Stopped", TEXT_APPEND);
                    intTelemetryStarted = 0;
                    Message msg = new Message();
                    msg.what = MSG_STOP_TELEMETRY;
                    mHandler.sendMessage(msg);
                }


            }
        });
        btnScan.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                /*if(mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                    Log.d(TAG, "onLongClick: DISCONNECTING BLE");
                }
                setLogMessage("Clearing..", TEXT_NOT_APPEND);
                stopScanning();*/
                bluetoothService.disconnect();
                Toast.makeText(getApplicationContext(), "DISCONNECTING BLE", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onLongClick: DISCONNECT BLE END ");
                return false;
            }
        });
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mConnected == true){
                    bluetoothService.disconnect();
                    return;
                }
                bleScanCount = 0;
                String mac = editTextMAC.getText().toString();
                if(intScanning == 0) {
                    setLogMessage("Validating MAC Address...:" + mac, TEXT_APPEND);
                    Log.d(TAG, "onClick: mac len:" + mac.length());
                    if (mac.length() == 17) {
                        esp32MACAddr = mac;
                        setLogMessage("MAC Address: VALID", TEXT_APPEND);
                    } else {
                        esp32MACAddr = ESP32_MAC;
                        setLogMessage("MAC Address: NOT VALID", TEXT_APPEND);
                        //return;
                    }
                }
                sharedPreferences.edit().putString(PREF_MAC_KEY, esp32MACAddr).apply();

                editTextMAC.setText(esp32MACAddr);
                if(intScanning == 0){
                    intScanning = 1;
                    btnScan.setText(strScanStop);
                    setLogMessage("Scanning Started for 30 seconds...", TEXT_APPEND);
                    startScanning();
                    scannerTimer = new CountDownTimer(30000, 1000) {

                        public void onTick(long millisUntilFinished) {
                            //String btnText = btnScan.getText().toString();
                            btnScan.setText(strScanStop+":"+ millisUntilFinished / 1000);
                            Log.d(TAG, "onTick: seconds remaining: " + millisUntilFinished / 1000);
                            //here you can have your logic to set text to edittext
                        }

                        public void onFinish() {
                            intScanning = 0;
                            Log.d(TAG, "onFinish: Timer Expired \n");
                            setLogMessage("Timer Expired.. Scanning Stopped !!", TEXT_APPEND);
                            setLogMessage("Device Found:"+bleScanCount, TEXT_APPEND);
                            btnScan.setText(strScanStart);
                            stopScanning();
                        }
                    }.start();
                }else{
                    intScanning = 0;
                    scannerTimer.cancel();
                    btnScan.setText(strScanStart);
                    setLogMessage("Scanning Stopped...", TEXT_APPEND);
                    setLogMessage("Device Found:"+bleScanCount, TEXT_APPEND);
                    stopScanning();
                }
            }
        });

        textViewLog.setMovementMethod(new ScrollingMovementMethod());
        esp32MessageHandler();
        verifyStoragePermissions(MainActivity.this);
    }//OnCreate


    public String getPath1(Uri uri) {

        String path = null;
        String[] projection = { MediaStore.Files.FileColumns.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if(cursor == null){
            path = uri.getPath();
        }
        else{
            cursor.moveToFirst();
            int column_index = cursor.getColumnIndexOrThrow(projection[0]);
            path = cursor.getString(column_index);
            cursor.close();
        }

        return ((path == null || path.isEmpty()) ? (uri.getPath()) : path);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PICKFILE_RESULT_CODE:
                if (resultCode == -1) {
                    otaFileUri = data.getData();
                    Log.d(TAG, "onActivityResult: type:"+data.getType() + " uri:"+otaFileUri);
                    otaFilePath = otaFileUri.getPath();
                    Log.e(TAG, "onActivityResult: path:"+getPath1(otaFileUri) );
                    setLogMessage("PATH:" + otaFilePath, TEXT_APPEND);

                    String src = otaFileUri.getPath();
                    //String src = getPath1(otaFileUri);
                    File source = new File(src);
                    Log.d(TAG, "onActivityResult: FILE EXITS:"+ source.exists());

                    //String filename = otaFileUri.getLastPathSegment();
                    //File destination = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/CustomFolder/" + filename);

                   /*
                    Message msg = new Message();
                    msg.what = MSG_PROCESS_START_OTA;
                    msg.obj = otaFilePath;
                    mHandler.sendMessage(msg);
                    /*
                    */
                }

                break;
        }
    }

    /********* PERMISSIONS ENDS **********/

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //if(grantResults.length == 0) return;
        Log.d(TAG, "onRequestPermissionsResult: requestCode:"+ requestCode + " grantResults.length:"+grantResults.length);
        switch (requestCode) {
            case MY_PERMISSION_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED)
                    //Do your work
                    Log.d(TAG, "onRequestPermissionsResult: STORAGE access granted");
                    break;
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, ("coarse location permission granted"));
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    /********* PERMISSIONS ENDS **********/


    /******* SCANNING STARTS ***************/

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            //setLogMessage("Device Name: " + result.getDevice().getName()+" MAC:" +result.getDevice().getAddress()+ " rssi: " + result.getRssi(), TEXT_APPEND);
            boolean res = bleScanMacNameMap.containsKey(result.getDevice().getAddress());
            Log.d(TAG, "onScanResult: res:"+ res);
            if(res == false) {
                bleScanMacNameMap.put(result.getDevice().getAddress(), result.getDevice().getName());
                setLogMessage("Device Name: " + result.getDevice().getName() + " MAC:" + result.getDevice().getAddress(), TEXT_APPEND);
                setLogMessage("", TEXT_APPEND);
                bleScanCount++;
                Log.d("BLE SCANING", "Device Name: " + result.getDevice().getName() + " MAC:" + result.getDevice().getAddress() + " rssi: " + result.getRssi());
            }
            if(result.getDevice().getAddress().equalsIgnoreCase(esp32MACAddr))
            {
                scannerTimer.cancel();
                setLogMessage("Device Found:"+bleScanCount, TEXT_APPEND);
                btnScan.setText(strScanStart);
                stopScanning();
                startGATTbacllbackService();

               /* myDevice = result.getDevice();
                Toast.makeText(getApplicationContext(), "FOUND Device:"+ myDevice.getName() + "="+myDevice.getAddress(), Toast.LENGTH_SHORT).show();
                setLogMessage("Connecting to Device :"+myDevice.getAddress(), TEXT_APPEND);
                Log.d(TAG, "LIST of UUIDs:"+ myDevice.getUuids());
                scannerTimer.cancel();
                mBluetoothGatt = myDevice.connectGatt(getApplicationContext(), false, mGattCallback);
                Log.d(TAG, "Trying to create a new connection.");
                connectedBluetoothMAC = myDevice.getAddress();
                connectedBluetoothName = myDevice.getName();
                mBluetoothGatt.connect();
                stopScanning();*/
            }else{
                Log.d(TAG, "NOT MATCHED:"+result.getDevice().getAddress() +"!="+ esp32MACAddr);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "onScanFailed: ");
        }
    };

    public void startScanning() {
        Log.d(TAG,("start scanning"));
        // Stops scanning after a predefined scan period.
        handlerScanner.postDelayed(new Runnable() {
            @Override
            public void run() {

                btScanner.stopScan(leScanCallback);
            }
        }, SCAN_PERIOD);
        btScanner.startScan(leScanCallback);
    }

    public void stopScanning() {
        Log.d(TAG,("stopping scanning"));
        bleScanMacNameMap.clear();
        btScanner.stopScan(leScanCallback);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }
    /******* SCANNING ENDS ***************/

    /************** OTA STARTS *****************/


    void startOtaRequestAndFilePath()
    {
        setOTA_Abort(OTA_ERROR_DEFAULT);
        String otaFolderPath = "";
        File otaDir = null;
        try {
            otaFolderPath = Environment.getExternalStorageDirectory().getPath()
                    + File.separator + "documents/" + File.separator;
            otaDir = new File(otaFolderPath);
            if (!otaDir.exists()) {
                //otaDir.mkdir();
                otaFolderPath = Environment.getExternalStorageDirectory().getPath()
                        + File.separator + "Documents/";
                otaDir = new File(otaFolderPath);
                if (otaDir.exists()) {

                }
            }
        }catch(Exception e){
            Log.e(TAG, "onClick: " + e);
        }
        if(mConnected == false){
            setLogMessage("Device not Connected !!!", TEXT_APPEND);
            return;
        }
        setLogMessage("OTA TODO", TEXT_APPEND);
        Log.d(TAG, "onClick: OTA Click");

        otaFolderPath += "DXe-OTA" + File.separator;
        Log.d(TAG, "onClick: otaFolderPath:"+otaFolderPath);

        otaDir = new File(otaFolderPath);
        if(!otaDir.exists()){
            otaDir.mkdir();
        }
        File[] files = otaDir.listFiles();
        int filesFound = 0;
        if(files != null){
            filesFound = files.length;
        }
        Log.d("Files", "Size: "+ filesFound);
        for (int i = 0; i < filesFound; i++)
        {
            Log.d("Files", "FileName:" + files[i].getName());
            Log.d(TAG, "onClick: path:"+ files[i].getPath() + " Can Read:"+ files[i].canRead());
            Log.d(TAG, "onClick: file len:"+ files[i].length());
        }
        if(filesFound == 0) {
            setLogMessage("Copy OTA File to  below location and Start Again:" + otaFolderPath, TEXT_APPEND);
        }else {
            Message msg = new Message();
            msg.what = MSG_PROCESS_START_OTA;
            msg.obj = otaFolderPath;
            mHandler.sendMessage(msg);
        }
    }

    long otaStartTime = 0;
    long otaPacketCount = 0;
    void sendOTA_Packet(int otaStatus, int fileSize, byte[] fileData)
    {
        byte[] data = null;
        if(otaStatus == MSG_OTA_START) {
            otaStartTime = System.currentTimeMillis();
            otaPacketCount++;
            data = new byte[BT_HEADER_END_INDEX+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_START;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            Log.d(TAG, "sendOTA_startPacket: 0" + size[0] + " 1:" + size[1] + " 2:" + size[2] + " 3:" + size[3] + " TotalSize:" + fileSize);
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];
            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;

        }else if(otaStatus == MSG_OTA_COMPLETE){
            otaPacketCount++;
            long otaEndtime = System.currentTimeMillis();
            Log.d(TAG, "sendOTA_Packet: TIME TO COMPLETE(sec):"+ (otaEndtime - otaStartTime)/1000);
            setLogMessage("TOTAL OTA TIME:"+(otaEndtime - otaStartTime)/1000, TEXT_APPEND);
            data = new byte[BT_HEADER_END_INDEX+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_END;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            Log.d(TAG, "sendOTA_CompletePacket: 0" + size[0] + " 1:" + size[1] + " 2:" + size[2] + " 3:" + size[3] + " TotalSize:" + fileSize);
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];
            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;
        }else if(otaStatus == MSG_OTA_PROCESS){
            otaPacketCount++;
            data = new byte[BT_HEADER_END_INDEX+fileSize+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_CONTINUE;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            //Log.d(TAG, "sendOTA_ProcessPacket: 0" + size[0] + " 1:" + size[1] + " 2:" + size[2] + " 3:" + size[3] + " TotalSize:" + fileSize);
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];
            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;
            for (int k = 0; k < fileSize; k++) {
                data[BT_HEADER_END_INDEX + 1 + k] = (byte) (fileData[k] & 0xff);
            }
        }else{
            Log.e(TAG, "sendOTA_Packet: WRONG OTA PACKETS"+ otaStatus );
        }
        Log.d(TAG, "sendOTA_Packet: Packet Count:"+ otaPacketCount);
       /* if (characEsp32 == null) {
            Log.e(TAG, "OTA characEsp32 not found!");
            return;
        }*/
        //characEsp32.setValue(data);

        oncharactersticsWriteCount = 1;
        //boolean status1 = mBluetoothGatt.writeCharacteristic(characEsp32);
        boolean status1 = bluetoothService.writeCharacteristics(characEsp32, data);
        if(otaStatus == MSG_OTA_COMPLETE){
            Log.d(TAG, "OTA SENT CHAR: []:" + bytesToHexString(data));
            setLogMessage(bytesToHexString(data), TEXT_APPEND);
        }
        if (status1) {
            assert data != null;
            //Log.d(TAG, "OTA SENT CHAR: [" + characEsp32.getUuid() + "]:" + bytesToHexString(data));
            Log.d(TAG, "sendOTA_Packet: DATA WROTE:" + System.currentTimeMillis());
            setLogMessage("", TEXT_OTA_PERCENT);
        } else {
            Log.e(TAG, "writeControlCharacteristic: Failed to write []:" + bytesToHexString(data));
            currentWriteStatus = WRITE_STATUS_FAIL;
        }

    }

    void startOTAProcess(String filePathOta1) throws InterruptedException {
        ota_total_file_length = 0;
        ota_current_bytes_transferred = 0;
        ota_started_time = 0;
        screenLogMsgforOTA = "";

        //Thread.sleep(100);
        String root = Environment.getExternalStorageDirectory().toString();
        Log.d(TAG, "startOTAProcess: root:"+root);
        String[] localFilePaths= filePathOta1.split(":");
        Log.d(TAG, "startOTAProcess: len:"+localFilePaths.length);
        String filePathOta = "";
        if(localFilePaths.length == 1){
            Log.d(TAG, "startOTAProcess: localFilePaths:"+ localFilePaths[0]);
            //filePathOta = localFilePaths[0];
            filePathOta = root+localFilePaths[0];
        }
        if(localFilePaths.length == 2){
            Log.d(TAG, "startOTAProcess: [0]:"+ localFilePaths[0]);
            Log.d(TAG, "startOTAProcess: [1]:"+ localFilePaths[1]);
            filePathOta = root+"/"+localFilePaths[1];
        }
        filePathOta = filePathOta1 + OTA_FILE_NAME;
        File file = new File(filePathOta);
        int fileSize = (int) file.length();
        ota_total_file_length = fileSize;
        currentWriteStatus = WRITE_STATUS_DEFAULT;
        byte[] fileBytes = new byte[fileSize];
        byte[] bytesMtu = new byte[MTU_SIZE];
        Log.d(TAG, "startOTAProcess: FileSize:"+fileSize + " FILE:"+filePathOta);
        sendOTA_Packet(MSG_OTA_START, fileSize, null);
        //Thread.sleep(500);
        int totalBytesSent = 0;
        boolean validFirmwareVerCheck = false;
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(filePathOta));
            try {
                buf.read(fileBytes, 0, fileBytes.length);
                int bytesOffset = 0;
                String downloadFwVer = checkDownloadedFirmwareDetails(Arrays.copyOf(fileBytes, 256));
                downloadFwVer = downloadFwVer.replaceAll("\0+$", "");
                deviceFirmwareVersion = deviceFirmwareVersion.replaceAll("\0+$", "");
                Log.d(TAG, "startOTAProcess: DOWNLOADED FW:"+ downloadFwVer );
                Log.d(TAG, "startOTAProcess: DXE firmware:"+ deviceFirmwareVersion);

                if(deviceFirmwareVersion.equals(downloadFwVer)){
                    Log.e(TAG, "startOTAProcess:  FIRMWARE MATCHED");
                    setLogMessage("SAME FIRMWARE VERSIONS \n" +
                            "DEVICE FIRMWARE VERSION:"+deviceFirmwareVersion+ "\n"+
                            "DOWNLOADED FIRMWARE VERSION:"+downloadFwVer, TEXT_APPEND);
                    validFirmwareVerCheck = false;
                }else{
                    Log.e(TAG, "startOTAProcess:  FIRMWARE NOT MATCHED");
                    setLogMessage("LATEST FIRMWARE AVAILABLE \n" +
                            "DEVICE FIRMWARE VERSION:"+deviceFirmwareVersion+ "\n"+
                            "DOWNLOADED FIRMWARE VERSION:"+downloadFwVer, TEXT_APPEND);
                    validFirmwareVerCheck = false;
                }
                if(validFirmwareVerCheck == true) {
                    while (totalBytesSent < fileSize) {
                        Log.d(TAG, "startOTAProcess: While START:" + System.currentTimeMillis());
                        int bytesToSent = 0;
                        for (int i = 0; i < MTU_SIZE - BT_HEADER_END_INDEX; i++) {
                            if ((bytesOffset + i) >= fileSize) {
                                break;
                            }
                            bytesMtu[i] = fileBytes[bytesOffset + i];
                            bytesToSent++;
                            totalBytesSent++;
                        }
                        ota_current_bytes_transferred = totalBytesSent;
                        bytesOffset = bytesOffset + MTU_SIZE - BT_HEADER_END_INDEX;
                        //Log.d(TAG, "startOTAProcess: Total Bytes sent:" + totalBytesSent +" bytesToSent:"+bytesToSent);
                        Log.d(TAG, "startOTAProcess: WAIT CALLBACK:" + System.currentTimeMillis());
                        while (true) {
                            if (oncharactersticsWriteCount == 0) break;
                            else {
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }//while(1)
                        Log.d(TAG, "startOTAProcess: BEFORE SEND:" + System.currentTimeMillis());
                        sendOTA_Packet(MSG_OTA_PROCESS, bytesToSent, bytesMtu);
                        //Thread.sleep(100);
                        if ((OTA_ERROR_DEFAULT != getOTA_Abort()) || (currentWriteStatus == WRITE_STATUS_FAIL)) {
                            Log.e(TAG, "startOTAProcess: ABORTING OTA");
                            break;
                        }
                    }//while
                }
                //Log.d(TAG, "startOTAProcess: DATA:"+bytesToHexString(fileBytes));
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "startOTAProcess: WAIT CALLBACK:"+System.currentTimeMillis());
            if(validFirmwareVerCheck == true) {
                while (true) {
                    if (oncharactersticsWriteCount == 0) break;
                    else {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }//while(1)
                sendOTA_Packet(MSG_OTA_COMPLETE, totalBytesSent, null);
                buf.close();
            }
            // Thread.sleep(500);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d(TAG, "startOTAProcess: OTA PROCESS COMPLETES ************");
    }

    /************** OTA ENDS ********************/


    /******* TELEMETRY STARTS ***************/

    void updateTelemetryButtonOnFailure(int startFailed)
    {
        if(startFailed == 1){
            //Start has failed, rollback
            btnTelemetry.setText(strTelemetryStart);
            setLogMessage("Telemetry Start: Message failed", TEXT_APPEND);
            intTelemetryStarted = 0;
        }
        else{
            btnTelemetry.setText(strTelemetryStop);
            setLogMessage("Telemetry Stop: Failed", TEXT_APPEND);
            intTelemetryStarted = 1;
        }
    }

    public  boolean sendRequest(int request) {
        byte[] data = new byte[BT_HEADER_END_INDEX+1];
        switch(request){
            case BT_MSG_HEADER_MSG_TYPE_FIRM_VER:
                deviceFirmwareVersion = "";
                data[BT_HEADER_START_INDEX] = (byte)BT_MSG_HEADER_FIRST_BYTE;
                data[BT_HEADER_MSG_TYPE_INDEX] =BT_MSG_HEADER_MSG_TYPE_FIRM_VER;
                data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_FW_REQUEST;
                data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = 0;
                data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = 0;
                data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = 0;
                data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = 0;
                data[BT_HEADER_END_INDEX] = (byte)BT_MSG_HEADER_LAST_BYTE;
                break;
        }
        boolean status1 = bluetoothService.writeCharacteristics(characEsp32, data);
        if (status1) {
            Log.d(TAG, "sendRequest SENT:" +bytesToHexString(data));
        } else{
            Log.e(TAG, "sendRequest writeControlCharacteristic: Failed to write []:" + bytesToHexString(data));
        }
        return status1;
    }

    public  boolean startTelemetryData(int start) {

        byte[] data = new byte[BT_HEADER_END_INDEX+1];
        if(1 == start) {
            data[BT_HEADER_START_INDEX] = (byte)BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] =BT_MSG_HEADER_MSG_TYPE_TELEMETRY;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_START;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = 0;
            data[BT_HEADER_END_INDEX] = (byte)BT_MSG_HEADER_LAST_BYTE;
        }else{
            data[BT_HEADER_START_INDEX] = (byte)BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] =BT_MSG_HEADER_MSG_TYPE_TELEMETRY;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_END;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = 0;
            data[BT_HEADER_END_INDEX] = (byte)BT_MSG_HEADER_LAST_BYTE;
        }
        boolean status1 = bluetoothService.writeCharacteristics(characEsp32, data);
        if (status1) {
            Log.d(TAG, "TELE SENT:" +bytesToHexString(data));
        } else{
            Log.e(TAG, "startTelemetryData writeControlCharacteristic: Failed to write []:" + bytesToHexString(data));
            currentWriteStatus = WRITE_STATUS_FAIL;
            updateTelemetryButtonOnFailure(start);
        }
        return status1;
    }

    /******* TELEMETRY ENDS ***************/
    public  String bytesToHexString(byte[] data){
        StringBuffer result = new StringBuffer();
        for (byte b : data) {
            result.append(String.format("%02X ", b));
            result.append(" "); // delimiter
        }
        return result.toString();
    }
    public  String bytesToHexString(short[] data){
        StringBuffer result = new StringBuffer();
        for (short b : data) {
            result.append(String.format("%02X ", b));
            result.append(" "); // delimiter
        }
        return result.toString();
    }


    void setLogMessage(String Message, int append){
        String currMsg = textViewLog.getText().toString()+"\n";
        //Log.d(TAG, "setLogMessage: "+Message);
        if(append == TEXT_APPEND) {
            textViewLog.setText(currMsg + Message);
        }else if(append == TEXT_OTA_PERCENT){
            if(screenLogMsgforOTA.length() == 0){
                ota_started_time = System.currentTimeMillis();
                screenLogMsgforOTA = currMsg;
            }
            long percent = (long) (((float)ota_current_bytes_transferred/(float)ota_total_file_length)*100);
            //Log.d(TAG, "setLogMessage: PERCENTAGE:"+percent);
            float fspeed = (float) 1000 * ((ota_current_bytes_transferred -  ota_delta_data_transferred) / (float)((System.currentTimeMillis()-ota_delta_duration)));
            /*
            Log.d(TAG, "setLogMessage: bytes::" + String.format("%d-%d=%d",
                    ota_current_bytes_transferred, ota_delta_data_transferred, (ota_current_bytes_transferred -  ota_delta_data_transferred)));
            Log.d(TAG, "setLogMessage: Time:"+ String.format("%d=%d",
                    ota_delta_duration, ((System.currentTimeMillis()-ota_delta_duration)/1000)));
             */
            /*
            Log.d(TAG, "setLogMessage:Delta Transfer:"
                    +((ota_current_bytes_transferred -  ota_delta_data_transferred))+ " SPEED:" + fspeed
                    + " Time Delta:" + (System.currentTimeMillis()-ota_delta_duration));
                    */

            long speed = (long)fspeed;
            String strOtaProgress = String.format("Bytes Transferred:%d/%d=%d \nTransfer Speed:%d Bytes/Sec \nTime:%d Seconds",
                    ota_current_bytes_transferred, ota_total_file_length, percent, speed, (int)(System.currentTimeMillis() - ota_started_time)/1000);

            textViewLog.setText(screenLogMsgforOTA + strOtaProgress);
            Log.e(TAG, "OTA Progress:"+ strOtaProgress );
            ota_delta_duration = System.currentTimeMillis();
            ota_delta_data_transferred = ota_current_bytes_transferred;
        }
        else{
            textViewLog.setText(Message);
        }
    }

    String checkDownloadedFirmwareDetails(byte [] value)
    {
        String fw_Details = "";
        int skipByteLen = 4 + 4 + 8 + 32; //int magic word, int secure_ver, int reser[2]
        byte[] bytePayload = new byte[32];
        for (int  i  = 0; i < (32); i++){// Firmware version
            bytePayload[i] = value[skipByteLen+i];
        }
        Log.d(TAG, "checkDownloadedFirmwareDetails: "+ bytesToHexString(bytePayload));
        String strPayload = new String(bytePayload);
        strPayload = strPayload.replaceAll("\0+$", "");
        String firmware_version = strPayload;
        fw_Details += "FIRMWARE VERSION:"+ strPayload;
        Log.d(TAG, "DownloadedFirware: FW VER:" + strPayload);

        skipByteLen = 4 + 4 + 8 + 32 + 32; //int magic word, int secure_ver, int reser[2] + char ver[32]
        for (int  i  = 0; i < (32); i++){// Project Name
            bytePayload[i] = value[skipByteLen+i];
        }
        strPayload = new String(bytePayload);
        strPayload = strPayload.replaceAll("\0+$", "");
        fw_Details += "\nPROJECT NAME:"+ strPayload;
        Log.d(TAG, "DownloadedFirware: PROJECT NAME:" + strPayload);

        //int magic word, int secure_ver, int reser[2] + char ver[32] + char prj_name[32]
        skipByteLen = 4 + 4 + 8 + 32 + 32 + 32;
        for (int  i  = 0; i < (32); i++){// Date and Time
            bytePayload[i] = value[skipByteLen+i];
        }
        bytePayload[14] = ' ';
        bytePayload[15] = ' ';
        strPayload = new String(bytePayload);
        strPayload = strPayload.replaceAll("\0+$", "");
        fw_Details += "\nBUILD DATE:"+ strPayload;
        Log.d(TAG, "DownloadedFirware: DATE TIME:" + strPayload);

        //int magic word, int secure_ver, int reser[2] + char ver[32] + char prj_name[32] + char date[16] + char time[16]
        skipByteLen = 4 + 4 + 8 + 32 + 32 + 32+32;
        for (int  i  = 0; i < (32); i++){// Date and Time
            bytePayload[i] = value[skipByteLen+i];
        }
        strPayload = new String(bytePayload);
        strPayload = strPayload.replaceAll("\0+$", "");
        fw_Details += "\nIDF VERSION:"+ strPayload;
        Log.d(TAG, "DownloadedFirware: IDF VER:" + strPayload);
        //setLogMessage(fw_Details, TEXT_APPEND);
        return firmware_version;
    }

    private void processRecvdData(byte[] value) {

        for(int i = 0; i< value.length; i++){
            // Log.d(TAG, "processRecvdData: "+i+":"+value[i]);
            value[i] = (byte) (value[i] & 0xFF);
        }
        Log.d(TAG, "processRecvdData: "+String.format("%x %x %x %x %x %x %x %x", value[0], value[1],value[2],value[3],value[4],value[5],value[6],value[7]));

        if(((value[BT_HEADER_START_INDEX] & 0XFF) == BT_MSG_HEADER_FIRST_BYTE) &&  ((value[BT_HEADER_END_INDEX] & 0xFF)== BT_MSG_HEADER_LAST_BYTE)){
            if((value[BT_HEADER_MSG_TYPE_INDEX] == BT_MSG_HEADER_MSG_TYPE_TELEMETRY) && (value[BT_HEADER_MSG_ACTION_INDEX] == BT_MSG_HEADER_MSG_ACTION_CONTINUE)){
                int length_recvd = (value[BT_HEADER_PAYLOAD_LENGTH_INDEX2] | (value[BT_HEADER_PAYLOAD_LENGTH_INDEX3] << 8)) ;
                Log.d(TAG, "processRecvdData: len:"+ value.length);
                byte[] bytePayload = new byte[length_recvd];
                for (int  i  = 0; i < (length_recvd); i++){
                    bytePayload[i] = value[BT_HEADER_END_INDEX+1+i];
                }
                String strPayload = new String(bytePayload);
                strPayload = strPayload.trim();
                Date date = new Date();
                Log.d(TAG, "processRecvdData:"+date+": TELEMETRY LEN:"+length_recvd+" DATA:"+strPayload);
                setLogMessage("TELEMETRY["+length_recvd+"]:\n"+strPayload, TEXT_APPEND);
            }
            else if((value[BT_HEADER_MSG_TYPE_INDEX] == BT_MSG_HEADER_MSG_TYPE_OTA) && (value[BT_HEADER_MSG_ACTION_INDEX] == BT_MSG_HEADER_MSG_ACTION_ABORT))
            {
                Log.e(TAG, "processRecvdData: OTA ABORT MESSAGE");
                int errorCode =  (value[BT_HEADER_END_INDEX+1] | (value[BT_HEADER_END_INDEX+2] << 8)) ;
                Log.e(TAG, "processRecvdData: Error Code"+ String.format("%x", errorCode));
                setOTA_Abort(OTA_ERROR_CODE_INTERNAL_ERROR);
            }
            else if((value[BT_HEADER_MSG_TYPE_INDEX] == BT_MSG_HEADER_MSG_TYPE_FIRM_VER) && (value[BT_HEADER_MSG_ACTION_INDEX] == BT_MSG_HEADER_MSG_ACTION_FW_RESPONSE))
            {
                String fw_Details = "";
                int skipByteLen = 4 + 4 + 8; //int magic word, int secure_ver, int reser[2]
                byte[] bytePayload = new byte[32];
                for (int  i  = 0; i < (32); i++){// Firmware version
                    bytePayload[i] = value[BT_HEADER_END_INDEX+1+skipByteLen+i];
                }
                String strPayload = new String(bytePayload);
                strPayload = strPayload.replaceAll("\0+$", "");
                fw_Details += "FIRMWARE VERSION:"+ strPayload;
                Log.d(TAG, "processRecvdData: FW VER:" + strPayload);
                deviceFirmwareVersion = strPayload;
                skipByteLen = 4 + 4 + 8 + 32; //int magic word, int secure_ver, int reser[2] + char ver[32]
                for (int  i  = 0; i < (32); i++){// Project Name
                    bytePayload[i] = value[BT_HEADER_END_INDEX+1+skipByteLen+i];
                }
                strPayload = new String(bytePayload);
                strPayload = strPayload.replaceAll("\0+$", "");
                fw_Details += "\nPROJECT NAME:"+ strPayload;
                Log.d(TAG, "processRecvdData: PROJECT NAME:" + strPayload);

                //int magic word, int secure_ver, int reser[2] + char ver[32] + char prj_name[32]
                skipByteLen = 4 + 4 + 8 + 32 + 32;
                for (int  i  = 0; i < (32); i++){// Date and Time
                    bytePayload[i] = value[BT_HEADER_END_INDEX+1+skipByteLen+i];
                }
                bytePayload[14] = ' ';
                bytePayload[15] = ' ';
                strPayload = new String(bytePayload);
                strPayload = strPayload.replaceAll("\0+$", "");
                fw_Details += "\nBUILD DATE:"+ strPayload;
                Log.d(TAG, "processRecvdData: DATE TIME:" + strPayload);

                //int magic word, int secure_ver, int reser[2] + char ver[32] + char prj_name[32] + char date[16] + char time[16]
                skipByteLen = 4 + 4 + 8 + 32 + 32 + 32;
                for (int  i  = 0; i < (32); i++){// Date and Time
                    bytePayload[i] = value[BT_HEADER_END_INDEX+1+skipByteLen+i];
                }
                strPayload = new String(bytePayload);
                strPayload = strPayload.replaceAll("\0+$", "");
                fw_Details += "\nIDF VERSION:"+ strPayload;
                Log.d(TAG, "processRecvdData: IDF VER:" + strPayload);
                setLogMessage(fw_Details, TEXT_APPEND);

                startOtaRequestAndFilePath();
            }
        }else{
            Log.e(TAG, "processRecvdData: Wrong Header");
        }
    }
    /******************* Message Handler ************************/
    Handler mHandler = null;
    BluetoothGattDescriptor descriptor = null;
    void esp32MessageHandler() {
        HandlerThread handlerThread = new HandlerThread("esp32BluetoothHandler");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();

        if (mHandler == null) {
            mHandler = new Handler(looper) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    super.handleMessage(msg);
                    Log.d(TAG, "handleMessage: Message msgId:"+msg.what);
                    switch (msg.what){
                        case MSG_SUB_NOTIFY:
                            bluetoothService.setCharacteristicNotification(null, true);
                          /*  descriptor = characEsp32.getDescriptor(UUID.fromString(WRITE_DESC_UUID));
                            if (descriptor == null) {
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
                            descriptor.setValue(finalValue);
                            boolean result;
                            result = mBluetoothGatt.writeDescriptor(descriptor);
                            Log.d(TAG, "setNotify: " + result);
                            if(!result)

                            {
                                Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                                //completedCommand();
                            }else{
                                Log.d(TAG, "Notification Enabled for "+descriptor.getUuid() + " Val:"+finalValue);

                            }*/
                            break;
                        case MSG_START_TELEMETRY:
                            startTelemetryData(1);
                            break;
                        case MSG_STOP_TELEMETRY:
                            startTelemetryData(2);
                            break;
                        case MSG_PROCESS_BLE_PACKETS:
                            processRecvdData((byte[])msg.obj);
                            break;
                        case MSG_GET_FW_DETAILS:
                            sendRequest(BT_MSG_HEADER_MSG_TYPE_FIRM_VER);
                            break;
                        case MSG_PROCESS_START_OTA:
                            try {
                                startOTAProcess((String) msg.obj);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "handleMessage: ", e);
                            }
                            break;
                    }
                }
            };
        }
    }//esp32MessageHandler


    /***** SERVICE **/

    private BluetoothLeService bluetoothService;

    void startGATTbacllbackService()
    {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bluetoothService = ((BluetoothLeService.LocalBinder) service).getService();
            Log.d(TAG, "onServiceConnected: ");
            if (bluetoothService != null) {
                // call functions on service to check connection and connect to devices
                if (!bluetoothService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }
                // perform device connection
                bluetoothService.connect(esp32MACAddr);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
            Log.e(TAG, "onServiceDisconnected: " );
        }
    };
    private boolean mConnected;
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive-BroadcastReceiver ACTION:" + action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                setLogMessage("BROADCAST CONNECTED", TEXT_APPEND);
                btnScan.setText(strDisconnect);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                setLogMessage("BROADCAST DIS CONNECTED", TEXT_APPEND);
                btnScan.setText(strConnect);
                bleScanMacNameMap.clear();
            }
            else if(BluetoothLeService.ACTION_CHARACTERISTICS_CHANGED.equals(action)){
                //Log.d(TAG, "onReceive: DATA:"+intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                byte[] extras = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                Message msg = new Message();
                msg.what = MSG_PROCESS_BLE_PACKETS;
                msg.obj = extras;
                mHandler.sendMessage(msg);
            }
            else if(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){

            }
            else if(BluetoothLeService.ACTION_DATA_WRITE_CALLBACK.equals(action)){
                oncharactersticsWriteCount--;
            }else if(BluetoothLeService.ACTION_MTU_CHANGED.equals(action)){
                Message msg = new Message();
                msg.what = MSG_SUB_NOTIFY;
                mHandler.sendMessage(msg);
            }
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Connect back to the MAC Address ");
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothService != null) {
            final boolean result = bluetoothService.connect(esp32MACAddr);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_CHARACTERISTICS_CHANGED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_CALLBACK);
        intentFilter.addAction(BluetoothLeService.ACTION_MTU_CHANGED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }


/*    private final BluetoothGattCallback mGattCallback  = new BluetoothGattCallback() {
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
            Log.d(TAG, "onConnectionStateChange: "+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                esp32BluetoothStatus = BLUETOOTH_CONNECTED;
                mBluetoothGatt = gatt;
                //bluetooth is connected so discover services
                Log.d(TAG, "onConnectionStateChange bluetooth connected ");
                mBluetoothGatt.discoverServices();
                gatt.requestMtu(MTU_SIZE);
                setLogMessage("DEVICE CONNECTED:"+connectedBluetoothMAC, TEXT_APPEND);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //Bluetooth is disconnected
                Log.d(TAG, "onConnectionStateChange bluetooth DISCONNECTED");
                mBluetoothGatt.close();
                esp32BluetoothStatus = BLUETOOTH_DISCONNECTED;
                setLogMessage("DEVICE DISCONNECTED:"+connectedBluetoothMAC, TEXT_APPEND);
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            Log.d(TAG, "onServicesDiscovered called:"+status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mBluetoothGatt = gatt;
                // services are discoverd
                //Log.d(TAG, "SERVICES DISCOVERED:"+ mBluetoothGatt.getServices());
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService s : services) {
                    Log.d(TAG, "onServicesDiscovered: service uuid:"+s.getUuid().toString());
                }
                BluetoothGattService Service = mBluetoothGatt.getService(UUID.fromString(WRITE_SERVICE_UUID));
                List<BluetoothGattCharacteristic>  characteristics = Service.getCharacteristics();
                for (BluetoothGattCharacteristic ch : characteristics) {
                    Log.d(TAG, "onServicesDiscovered: characteristics uuid:"+ch.getUuid().toString());
                }
                characEsp32 = Service.getCharacteristic(UUID.fromString(WRITE_CHAR_UUID));
                if (characEsp32 == null) {
                    Log.e(TAG, "characEsp32 not found!");
                }
                List<BluetoothGattDescriptor>  descriptors = characEsp32.getDescriptors();
                for (BluetoothGattDescriptor desc : descriptors) {
                    Log.d(TAG, "onServicesDiscovered: descriptors uuid:"+desc.getUuid().toString());
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead: ");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            //Log.d(TAG, "onCharacteristicWrite: UUID:"+characteristic.getUuid());
            Log.d(TAG, "onCharacteristicWrite: CALLBACK:"+System.currentTimeMillis());
            //Log.d(TAG, "onCharacteristicWrite[" + characteristic.getUuid()+"] Value:"+bytesToHexString(characteristic.getValue()));
            oncharactersticsWriteCount --;
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged: VAL:"+bytesToHexString(characteristic.getValue()) );
            byte [] newValue = characteristic.getValue();
            StringBuffer result = new StringBuffer();
            for (byte b : newValue) {
                result.append(String.format("%02X ", b));
                result.append(" "); // delimiter
            }
            Log.d(TAG, "onCharacteristicChanged[RCVD:"+newValue.length+"]: "+result.toString());
            Message msg = new Message();
            msg.what = MSG_PROCESS_BLE_PACKETS;
            msg.obj = characteristic.getValue();
            mHandler.sendMessage(msg);
            //processRecvdData(characteristic.getValue());

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
            mBluetoothGatt = gatt;
            Log.d(TAG, "onMtuChanged: mtu:"+mtu+ " status:"+ status);

            BluetoothGattService Service = mBluetoothGatt.getService(UUID.fromString(WRITE_SERVICE_UUID));
            characEsp32 = Service.getCharacteristic(UUID.fromString(WRITE_CHAR_UUID));
            if (characEsp32 == null) {
                Log.e(TAG, "characEsp32 not found!");
            }
            Message msg = new Message();
            msg.what = MSG_SUB_NOTIFY;
            mHandler.sendMessage(msg);
        }
    };*/
}
