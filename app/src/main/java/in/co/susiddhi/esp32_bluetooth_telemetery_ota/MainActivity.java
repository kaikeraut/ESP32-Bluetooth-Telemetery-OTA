package in.co.susiddhi.esp32_bluetooth_telemetery_ota;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
import android.location.LocationManager;
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
import android.widget.ProgressBar;
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

import javax.security.auth.login.LoginException;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;

public class MainActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_CODE = 101;

    public static final int PICKFILE_RESULT_CODE            = 1;
    private static final int MY_PERMISSION_REQUEST_CODE     = 1001;
    public final static int MTU_SIZE_REQUESTED              = 500;
    public static int MTU_SIZE_GOT                          = 0;
    public static int MTU_SIZE_REDUCTION_AT_DEVICE_FOR_WRITE_NR =   5;
    private final static int TEXT_APPEND                    = 1;
    private final static int TEXT_NOT_APPEND                = 0;
    private final static int TEXT_OTA_PERCENT               = 2;
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
    ProgressBar progressBarScan, progressBarOTA;
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

    private int OtaTransferSuccessCheckUpdateStatus = 0;
    private final static String OTA_FILE_NAME = "dxe_ota_file.bin";
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
    public static  final int MSG_START_CONNECTION = 17;
    public static  final int MSG_WAIT_OTA_COMPLETION = 18;


    /* Bluetooth message format index send to APP */
    public static  final int BT_HEADER_START_INDEX             =    0;
    public static  final int BT_HEADER_MSG_TYPE_INDEX          =    1;
    public static  final int BT_HEADER_MSG_ACTION_INDEX        =    2;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX0   =    3;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX1   =    4;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX2   =    5;
    public static  final int BT_HEADER_PAYLOAD_LENGTH_INDEX3   =    6;
/*
    public static  final int BT_HEADER_PAYLOAD_PCKT_CNT_INDEX0   =    7;
    public static  final int BT_HEADER_PAYLOAD_PCKT_CNT_INDEX1   =    8;
    public static  final int BT_HEADER_PAYLOAD_PCKT_CNT_INDEX2   =    9;
    public static  final int BT_HEADER_PAYLOAD_PCKT_CNT_INDEX3   =    10;
    public static  final int BT_HEADER_END_INDEX                 =    11;
    */
public static  final int BT_HEADER_END_INDEX                 =    7;

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
    public static  final int  BT_MSG_HEADER_MSG_ACTION_OTA_DUMMY   =       0x07;

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
    public static  final int MSG_OTA_PROCESS_DUMMY                =       0xDEAD;

    public static  final int BLUETOOTH_CONNECTED = 1;
    public static  final int BLUETOOTH_DISCONNECTED = 2;
    private int esp32BluetoothStatus = 0;
    private int bleScanCount = 0;
    private int abortOTAProcess = 0;
    private String deviceAddress;
    private String deviceFirmwareVersion="";
    private String downloadFwVer="";
    long totalOtaTime = 0;

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

        //Progress bar
        progressBarScan = (ProgressBar) findViewById(R.id.progressBarScan);
        progressBarOTA = (ProgressBar) findViewById(R.id.progressBar2Ota);

        progressBarScan.setVisibility(View.GONE);
        progressBarOTA.setVisibility(View.GONE);

        esp32BluetoothStatus = BLUETOOTH_DISCONNECTED;
        esp32MACAddr = sharedPreferences.getString(PREF_MAC_KEY, "");
        editTextMAC.setText(esp32MACAddr);
/*
        ActivityCompat.requestPermissions( this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
        }, 0);
*/
        //checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, MY_PERMISSION_REQUEST_CODE);
        //checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, MY_PERMISSION_REQUEST_CODE);
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);

        InputFilter[] macAddressFilters = new InputFilter[1];

        // Bluetooth Start **********************************************
        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }
 /*       // Make sure we have access coarse location enabled, if not, prompt the user to enable it
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
        }*/
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
        progressBarOTA.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            progressBarOTA.setMin(0x0);
        }
        btnOTA.setVisibility(View.GONE);
        btnTelemetry.setVisibility(View.GONE);
        btnOTA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLogMessage("OTA PROCESS: START", TEXT_NOT_APPEND);
                OtaTransferSuccessCheckUpdateStatus = 0;
                setOTA_Abort(OTA_ERROR_DEFAULT);
                String otaFolderPath = "";
                File otaDir = null;
                try {
                    otaFolderPath = Environment.getExternalStorageDirectory().getPath()
                            + File.separator + "documents" + File.separator;
                    otaDir = new File(otaFolderPath);
                    if (!otaDir.exists()) {
                        Log.e(TAG, "onClick: otaFolderPath doesn't Exist:"+ otaFolderPath);
                        //otaDir.mkdir();
                        otaFolderPath = Environment.getExternalStorageDirectory().getPath()
                                + File.separator + "Documents" + File.separator;
                        otaDir = new File(otaFolderPath);
                        if (!otaDir.exists()) {
                            Log.e(TAG, "onClick: otaFolderPath doesn't Exist:"+ otaFolderPath);
                        }
                    }
                }catch(Exception e){
                    Log.e(TAG, "onClick: " + e);
                }
                if(!mConnected){
                    setLogMessage("Device not Connected !!!", TEXT_APPEND);
                    return;
                }
                progressBarOTA.setVisibility(View.VISIBLE);
                Log.d(TAG, "onClick: OTA Click");

                otaFolderPath += "DXe-OTA" + File.separator;
                Log.d(TAG, "onClick: otaFolderPath:"+otaFolderPath);

                otaDir = new File(otaFolderPath);
                if(!otaDir.exists()){
                    Log.d(TAG, "onClick: Creating Folder otaFolderPath:"+otaFolderPath);
                    otaDir.mkdir();
                }
                Log.d(TAG, "onClick: len:" + otaDir.length() + " isDir:"+otaDir.isDirectory());
                Log.e(TAG, "startOtaRequestAndFilePath: canRead:" + otaDir.canRead());
                Log.e(TAG, "startOtaRequestAndFilePath: canWrite:" + otaDir.canWrite());
                Log.e(TAG, "startOtaRequestAndFilePath: canExecute:" + otaDir.canExecute());
                if(otaDir.canRead() == false){
                    setLogMessage("OTA DIR: READ ACCESS DENIED", TEXT_APPEND);
                }else{
                    setLogMessage("OTA DIR: READ ACCESS PRESENT", TEXT_APPEND);
                }
                File[] files = otaDir.listFiles();
                int filesFound = 0;
                if(files != null){
                    filesFound = files.length;
                }
                Log.d("Files", "Found Size: "+ filesFound);
                for (int i = 0; i < filesFound; i++)
                {
                    Log.d("Files", "FileName:" + files[i].getName());
                    Log.d(TAG, "onClick: path:"+ files[i].getPath() + " Can Read:"+ files[i].canRead());
                    Log.d(TAG, "onClick: file len:"+ files[i].length());
                }
                if(filesFound == 0) {
                    setLogMessage("Copy OTA File to  below location and Start Again:\n" + otaFolderPath, TEXT_APPEND);
                    setLogMessage("\nRename file OTA file to: "+OTA_FILE_NAME, TEXT_APPEND);
                }else {
                    Message msg = new Message();
                    msg.what = MSG_GET_FW_DETAILS;
                    mHandler.sendMessage(msg);
                }
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
                setLogMessage("Clearing..", TEXT_NOT_APPEND);
                if(bluetoothService != null) {
                    bluetoothService.disconnect();
                }
                Toast.makeText(getApplicationContext(), "DISCONNECTING BLE", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onLongClick: DISCONNECT BLE END ");
                return false;
            }
        });
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick BTN SCAN:mConnected: " + mConnected);
                if(mConnected == true){
                    bluetoothService.disconnect();
                    return;
                }
                bleScanCount = 0;
                progressBarScan.setVisibility(View.VISIBLE);
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
                            Log.d(TAG, "onTick:SCAN: seconds remaining: " + millisUntilFinished / 1000);
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
        //checkDownloadedFirmwareDetails();
        statusCheck();
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

    /********* PERMISSIONS ENDS **********/
    public void statusCheck() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();

        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }
    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            //ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
            String[] PERMISSIONS = {

                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS, requestCode);
            Log.e(TAG, "checkPermission: Requesting permission:"+permission);
        }
        else {
            Toast.makeText(MainActivity.this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //if(grantResults.length == 0) return;
        Log.e(TAG, "onRequestPermissionsResult: requestCode:"+ requestCode + " grantResults.length:"+grantResults.length);
        switch (requestCode) {
            case MY_PERMISSION_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED)
                    //Do your work
                    Log.d(TAG, "onRequestPermissionsResult:  access granted");
                    break;
            case STORAGE_PERMISSION_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, ("coarse location permission granted"));
                } else {

                }
                return;
            }
            default:
                Log.e(TAG, "onRequestPermissionsResult: DEFAULT REQUEST CODE"+ requestCode );
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
            Log.d(TAG, "onScanResult: Type:"+result.getDevice().getType() + " Alias:" + result.getDevice().getAlias());
            switch (result.getDevice().getType()){
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    Log.d(TAG, "onScanResult: DEVICE_TYPE_CLASSIC");
                    break;
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    Log.d(TAG, "onScanResult: DEVICE_TYPE_DUAL");
                    break;
                case BluetoothDevice.DEVICE_TYPE_LE:
                    Log.d(TAG, "onScanResult: DEVICE_TYPE_LE");
                    break;
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                    Log.d(TAG, "onScanResult: DEVICE_TYPE_UNKNOWN");
                    break;
            }
            Log.d(TAG, "onScanResult: res:"+ res);
            if(res == false) {
                bleScanMacNameMap.put(result.getDevice().getAddress(), result.getDevice().getName());
                setLogMessage("Device Name: " + result.getDevice().getName() + "\n MAC:" + result.getDevice().getAddress(), TEXT_APPEND);
                setLogMessage("", TEXT_APPEND);
                bleScanCount++;
                Log.d("BLE SCANING", "Device Name: " + result.getDevice().getName() + " MAC:" + result.getDevice().getAddress() + " rssi: " + result.getRssi());
            }
            if((res == false) && result.getDevice().getAddress().equalsIgnoreCase(esp32MACAddr))
            {
                stopScanning();
                scannerTimer.cancel();
                setLogMessage("Device Found:"+bleScanCount+"\n Matching MAC found !!", TEXT_APPEND);
                Log.e(TAG, "DEVICE FOUND MESSSAGE: " + esp32MACAddr);
                btnScan.setText(strScanStart);

                Message msg = new Message();
                msg.what = MSG_START_CONNECTION;
                mHandler.sendMessage(msg);

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
        bleScanMacNameMap.clear();
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
        progressBarScan.setVisibility(View.GONE);
        Log.d(TAG,("stopping scanning"));
        //bleScanMacNameMap.clear();
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
        if(!mConnected){
            setLogMessage("Device not Connected !!!", TEXT_APPEND);
            return;
        }


        otaFolderPath += "DXe-OTA" + File.separator;
        Log.d(TAG, "onClick: otaFolderPath:"+otaFolderPath);

        otaDir = new File(otaFolderPath);
        Log.e(TAG, "startOtaRequestAndFilePath: canRead:" + otaDir.canRead());
        Log.e(TAG, "startOtaRequestAndFilePath: canWrite:" + otaDir.canWrite());
        Log.e(TAG, "startOtaRequestAndFilePath: canExecute:" + otaDir.canExecute());
        if(!otaDir.exists()){
            otaDir.mkdir();
            Log.d(TAG, "startOtaRequestAndFilePath: CREATING OTA DIR" + otaFolderPath);
        }
        File[] files = otaDir.listFiles();
        int filesFound = 0;
        if(files != null){
            filesFound = files.length;
        }
        Log.d("Files", "Files Found: "+ filesFound);
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
        if(otaStatus == MSG_OTA_PROCESS_DUMMY){
            //otaPacketCount;
            data = new byte[BT_HEADER_END_INDEX+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_OTA_DUMMY;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];
/*
            byte [] packetCnt = ByteBuffer.allocate(4).putInt((int) otaPacketCount).array();
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX0] = packetCnt[0];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX1] = packetCnt[1];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX2] = packetCnt[2];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX3] = packetCnt[3];
*/
            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;
            Log.e(TAG, "sendOTA_Packet: DUMMY WRITES");
        }else  if(otaStatus == MSG_OTA_START) {
            otaStartTime = System.currentTimeMillis();
            otaPacketCount++;
            data = new byte[BT_HEADER_END_INDEX+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_START;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];

            /*
            byte [] packetCnt = ByteBuffer.allocate(4).putInt((int) otaPacketCount).array();
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX0] = packetCnt[0];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX1] = packetCnt[1];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX2] = packetCnt[2];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX3] = packetCnt[3];
*/
            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;
        }else if(otaStatus == MSG_OTA_COMPLETE){
            otaPacketCount++;
            long otaEndtime = System.currentTimeMillis();
            Log.d(TAG, "sendOTA_Packet: TIME TO COMPLETE(sec):"+ (otaEndtime - otaStartTime)/1000);
            totalOtaTime = (otaEndtime - otaStartTime)/1000;

            data = new byte[BT_HEADER_END_INDEX+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_END;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];
/*

            byte [] packetCnt = ByteBuffer.allocate(4).putInt((int) otaPacketCount).array();
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX0] = packetCnt[0];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX1] = packetCnt[1];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX2] = packetCnt[2];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX3] = packetCnt[3];
*/

            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;
        }else if(otaStatus == MSG_OTA_PROCESS){
            otaPacketCount++;
            data = new byte[BT_HEADER_END_INDEX+fileSize+1];
            data[BT_HEADER_START_INDEX] = (byte) BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] = BT_MSG_HEADER_MSG_TYPE_OTA;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_CONTINUE;
            byte[] size = ByteBuffer.allocate(4).putInt(fileSize).array();
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = size[0];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = size[1];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = size[2];
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = size[3];
            //setLogMessage("Total Bytes Sending:"+(BT_HEADER_END_INDEX+fileSize+1), TEXT_APPEND);

/*
            byte [] packetCnt = ByteBuffer.allocate(4).putInt((int) otaPacketCount).array();
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX0] = packetCnt[0];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX1] = packetCnt[1];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX2] = packetCnt[2];
            data[BT_HEADER_PAYLOAD_PCKT_CNT_INDEX3] = packetCnt[3];
*/

            data[BT_HEADER_END_INDEX] = (byte) BT_MSG_HEADER_LAST_BYTE;
            int k = 0;
            for (k = 0; k < fileSize; k++) {
                data[BT_HEADER_END_INDEX + 1 + k] = (byte) (fileData[k] & 0xff);
                //data[BT_HEADER_END_INDEX + 1 + k] = (byte) (k);
            }
            //setLogMessage("K="+k, TEXT_APPEND);
        }else{
            Log.e(TAG, "sendOTA_Packet: WRONG OTA PACKETS"+ otaStatus );
        }
        Log.d(TAG, "sendOTA_Packet: Packet Count:"+ otaPacketCount);

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
            //Log.d(TAG, "sendOTA_Packet: DATA WROTE:" + System.currentTimeMillis());
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
        otaPacketCount = 0;

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
        byte[] bytesMtu = new byte[MTU_SIZE_GOT];
        Log.d(TAG, "startOTAProcess: FileSize:"+fileSize + " FILE:"+filePathOta);
        sendOTA_Packet(MSG_OTA_START, fileSize, null);

        btnOTA.setClickable(false);
        btnTelemetry.setClickable(false);
        btnScan.setClickable(false);
        btnScan.setVisibility(View.INVISIBLE);


        int totalBytesSent = 0;
        boolean validFirmwareVerCheck = true;
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(filePathOta));
            try {
                buf.read(fileBytes, 0, fileBytes.length);
                int bytesOffset = 0;

                if(validFirmwareVerCheck == true) {
                    //int forceQuit = 0;
                    int lenToSend = MTU_SIZE_GOT - BT_HEADER_END_INDEX - MTU_SIZE_REDUCTION_AT_DEVICE_FOR_WRITE_NR;
                    while (totalBytesSent < fileSize) {

                        Log.d(TAG, "startOTAProcess: While START:" + System.currentTimeMillis());
                        int bytesToSent = 0;
                        for (int i = 0; i < lenToSend; i++) {
                            if ((bytesOffset + i) >= fileSize) {
                                break;
                            }
                            bytesMtu[i] = fileBytes[bytesOffset + i];
                            bytesToSent++;
                            totalBytesSent++;
                        }
                        ota_current_bytes_transferred = totalBytesSent;
                        bytesOffset = bytesOffset + lenToSend;
                        Log.d(TAG, "0 startOTAProcess: WAIT CALLBACK:" + System.currentTimeMillis());
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
                        if ((OTA_ERROR_DEFAULT != getOTA_Abort()) || (currentWriteStatus == WRITE_STATUS_FAIL)) {
                            Log.e(TAG, "startOTAProcess: ABORTING OTA");
                            break;
                        }

                    }//while
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            for(int i = 0; i < 3; i++){
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
                sendOTA_Packet(MSG_OTA_PROCESS_DUMMY, 0, null);
            }//for loop

            Log.d(TAG, "1 startOTAProcess: WAIT CALLBACK:"+System.currentTimeMillis());
            if(validFirmwareVerCheck == true) {
                for(int i = 0; i < 2; i++) {
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
                    Thread.sleep(1000);

                    sendOTA_Packet(MSG_OTA_COMPLETE, totalBytesSent, null);
                }
                Log.d(TAG, "startOTAProcess: MSG_OTA_COMPLETE sent ");
                buf.close();/*
                while (true) { // Wait for final Success full Write callback
                    if (oncharactersticsWriteCount == 0) break;
                    else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }//while(1)*/
                Log.d(TAG, "startOTAProcess: Wait for DXe Reboot");
                Message msg = new Message();
                msg.what = MSG_WAIT_OTA_COMPLETION;
                mHandler.sendMessage(msg);
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(TAG, "startOTAProcess: CRASH" +e );
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(TAG, "startOTAProcess: CRASH" +e );
        }
        Log.d(TAG, "startOTAProcess: OTA PROCESS COMPLETES ************");
        setLogMessage("TOTAL OTA TIME:"+totalOtaTime, TEXT_APPEND);
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
            btnOTA.setClickable(false);
        }else{
            data[BT_HEADER_START_INDEX] = (byte)BT_MSG_HEADER_FIRST_BYTE;
            data[BT_HEADER_MSG_TYPE_INDEX] =BT_MSG_HEADER_MSG_TYPE_TELEMETRY;
            data[BT_HEADER_MSG_ACTION_INDEX] = BT_MSG_HEADER_MSG_ACTION_END;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX0] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX1] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX2] = 0;
            data[BT_HEADER_PAYLOAD_LENGTH_INDEX3] = 0;
            data[BT_HEADER_END_INDEX] = (byte)BT_MSG_HEADER_LAST_BYTE;
            btnOTA.setClickable(true);
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
            progressBarOTA.setProgress((int)percent);
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
            int seconds = (int)(System.currentTimeMillis() - ota_started_time)/1000;
            String strOtaProgress = "Bytes Transferred:"+ota_current_bytes_transferred+"/"+ota_total_file_length+"" +
                    "="+percent+"\nTransfer Speed:"+speed+" Bytes/Sec \nTime:"+seconds+" Seconds";
            textViewLog.setText(screenLogMsgforOTA + strOtaProgress);
            Log.e(TAG, "OTA Progress:"+ strOtaProgress );
            ota_delta_duration = System.currentTimeMillis();
            ota_delta_data_transferred = ota_current_bytes_transferred;
        }
        else{
            textViewLog.setText(Message);
        }
    }

    String checkDownloadedFirmwareDetails() {
        String otaFolderPath = null;
        File otaDir;
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

        otaFolderPath += "DXe-OTA" + File.separator;
        otaFolderPath = otaFolderPath + OTA_FILE_NAME;
        Log.d(TAG, "checkDownloadedFirmwareDetails: OTA file PAth::"+ otaFolderPath);
        File file = new File(otaFolderPath);
        int fileSize = (int) file.length();

        byte[] value = new byte[fileSize];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(otaFolderPath));
            try {
                buf.read(value, 0, value.length);
            } catch (Exception e) {

            }
        }catch (Exception e){
            
        }

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
        Log.d(TAG, "processRecvdData: [RCVD]::" + bytesToHexString(value));
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
                Log.e(TAG, "processRecvdData: RECVD OTA ABORT MESSAGE");
                int errorCode =  (value[BT_HEADER_END_INDEX+1] | (value[BT_HEADER_END_INDEX+2] << 8)) ;
                Log.e(TAG, "processRecvdData: Error Code:"+ String.format("%x", errorCode));
                setOTA_Abort(OTA_ERROR_CODE_INTERNAL_ERROR);
                setLogMessage("RECVD OTA ABORT MESSAGE: 0x"+String.format("%x", errorCode), TEXT_APPEND);
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

                if(OtaTransferSuccessCheckUpdateStatus == 0){
                    setLogMessage(fw_Details, TEXT_APPEND);
                }
                if(downloadFwVer.length() == 0) {
                    downloadFwVer = checkDownloadedFirmwareDetails();
                }
                downloadFwVer = downloadFwVer.replaceAll("\0+$", "");
                deviceFirmwareVersion = deviceFirmwareVersion.replaceAll("\0+$", "");
                Log.d(TAG, "startOTAProcess: DOWNLOADED FW:"+ downloadFwVer );
                Log.d(TAG, "startOTAProcess: DXE firmware:"+ deviceFirmwareVersion);
                setLogMessage("Ota Flag:"+ OtaTransferSuccessCheckUpdateStatus, TEXT_APPEND);
                if(OtaTransferSuccessCheckUpdateStatus >= 1){
                    btnOTA.setClickable(true);
                    btnTelemetry.setClickable(true);
                    if(deviceFirmwareVersion.equals(downloadFwVer)){
                        setLogMessage("\nOTA : SUCCESS\n", TEXT_APPEND);
                        Toast.makeText(getApplicationContext(), "OTA: SUCCESS", Toast.LENGTH_SHORT).show();
                    }else{
                        setLogMessage("\nOTA : FAILURE,  FW Mismatch", TEXT_APPEND);
                        setLogMessage("Running:"+deviceFirmwareVersion + "\nDowloaded:"+downloadFwVer+"\n", TEXT_APPEND);
                        Toast.makeText(getApplicationContext(), "OTA: FAILURE", Toast.LENGTH_SHORT).show();
                    }
                }else {
                    if(deviceFirmwareVersion.equals(downloadFwVer)){
                        Log.e(TAG, "startOTAProcess:  FIRMWARE MATCHED");
                        setLogMessage("SAME FIRMWARE VERSIONS .. Skipping OTA \n" +
                                "DEVICE FIRMWARE VERSION:"+deviceFirmwareVersion+ "\n"+
                                "DOWNLOADED FIRMWARE VERSION:"+downloadFwVer, TEXT_APPEND);
                        Toast.makeText(getApplicationContext(), "SAME FIRMWARE", Toast.LENGTH_SHORT).show();
                    }else{
                        Log.e(TAG, "startOTAProcess:  FIRMWARE NOT MATCHED");
                        setLogMessage("LATEST FIRMWARE AVAILABLE \n" +
                                "DEVICE FIRMWARE VERSION:"+deviceFirmwareVersion+ "\n"+
                                "DOWNLOADED FIRMWARE VERSION:"+downloadFwVer, TEXT_APPEND);
                        startOtaRequestAndFilePath();
                        Toast.makeText(getApplicationContext(), "Starting OTA", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }else{
            Log.e(TAG, "processRecvdData: Wrong Header");
            setLogMessage("WRONG HEADER:"+bytesToHexString(value), TEXT_APPEND);
        }
        if(OtaTransferSuccessCheckUpdateStatus == 1){
            setLogMessage("OTA: DXe connected.. Checking Firmware", TEXT_APPEND);
            Message msg = new Message();
            msg.what = MSG_GET_FW_DETAILS;
            mHandler.sendMessage(msg);
            OtaTransferSuccessCheckUpdateStatus++;
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
                        case MSG_START_CONNECTION:
                            startGATTbacllbackService();
                            break;
                        case MSG_WAIT_OTA_COMPLETION:
                            //Start A 30 Seconds Counter and Try Reconnecting to DXes
                            bluetoothService.disconnect();
                            setLogMessage("Waiting 30 Seconds for DXe to Reboot", TEXT_APPEND);
                            CountDownTimer cnt = new CountDownTimer(30000, 1000) {
                                @Override
                                public void onTick(long l) {
                                    Log.d(TAG, "onTick: REBOOT COUNTER:"+ l/1000);
                                }

                                @Override
                                public void onFinish() {
                                    setLogMessage("Trying to Connect to DXe...", TEXT_APPEND);
                                    OtaTransferSuccessCheckUpdateStatus = 1;
                                    bluetoothService.connect(esp32MACAddr);
                                    btnScan.setVisibility(View.VISIBLE);
                                }
                            }.start();
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
                setLogMessage("DXE:BLUETOOTH CONNECTED", TEXT_APPEND);
                btnScan.setText(strDisconnect);
                Toast.makeText(getApplicationContext(), "DXE Connected", Toast.LENGTH_SHORT).show();
                btnOTA.setVisibility(View.VISIBLE);
                btnTelemetry.setVisibility(View.VISIBLE);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                setLogMessage("DXE:BLUETOOTH DISCONNECTED", TEXT_APPEND);
                btnScan.setText(strConnect);
                bleScanMacNameMap.clear();
                Toast.makeText(getApplicationContext(), "DXE DISCONNECTED", Toast.LENGTH_SHORT).show();
                btnOTA.setVisibility(View.GONE);
                btnTelemetry.setVisibility(View.GONE);
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
                setLogMessage("MTU GOT:"+MTU_SIZE_GOT, TEXT_APPEND);
            }
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Registering broadcaster ");
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        /*
        if (bluetoothService != null) {
            final boolean result = bluetoothService.connect(esp32MACAddr);
            Log.d(TAG, "Connect request result=" + result);
        }*/
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

}
