package in.co.susiddhi.esp32_bluetooth_telemetery_ota;


import static in.co.susiddhi.esp32_bluetooth_telemetery_ota.MainActivity.BT_HEADER_END_INDEX;
import static in.co.susiddhi.esp32_bluetooth_telemetery_ota.MainActivity.BT_HEADER_PAYLOAD_LENGTH_INDEX2;
import static in.co.susiddhi.esp32_bluetooth_telemetery_ota.MainActivity.BT_HEADER_PAYLOAD_LENGTH_INDEX3;
import static in.co.susiddhi.esp32_bluetooth_telemetery_ota.MainActivity.DXE_FOLDER_PARENT_NAME;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class TelemetryDataDisplay extends AppCompatActivity {

    TextView tvVibration, tvSpeed, tvTemperature;
    TextView sensorData;
    Button btnSaveTeleData;
    ScrollView scrollView;
    private String TAG = "TelemetryDataDisplay";
    String DXE_SENSOR_DATA_FOLDER_NAME = "SensorData";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_telemetry_data_display);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvVibration = (TextView)findViewById(R.id.textViewVibration);
        tvSpeed = (TextView)findViewById(R.id.textViewSpeed);
        tvTemperature = (TextView)findViewById(R.id.textViewTemperature);
        sensorData = (TextView) findViewById(R.id.textViewSensorData);
        btnSaveTeleData = (Button)findViewById(R.id.buttonSaveTeleData);


        scrollView = (ScrollView) findViewById(R.id.scrollView2);
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        btnSaveTeleData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String dxeFolderPath = Environment.getExternalStorageDirectory().getPath()
                        + File.separator + DXE_FOLDER_PARENT_NAME + File.separator + DXE_SENSOR_DATA_FOLDER_NAME + File.separator;
                File sensorData = new File(dxeFolderPath);
                if (!sensorData.exists()) {
                    Log.e(TAG, "onClick: otaFolderPath doesn't Exist:" + dxeFolderPath);
                    sensorData.mkdirs();
                }
                Calendar cal = Calendar.getInstance();
                String fileName = "SensorData_"+ String.format("%04d%02d%02d_%02d%02d", cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR), cal.get(Calendar.MINUTE));
                fileName += ".txt";
                try {
                File gpxfile = new File(dxeFolderPath, fileName);
                FileWriter writer = new FileWriter(gpxfile);
                    writer.append(getSensorLogdata());
                    writer.flush();
                    writer.close();

                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "onClick: " + e);
                }

                Log.e(TAG, "TelemetryDataDisplay: FileName:::"+ fileName );
                Toast.makeText(getApplicationContext(), "Saved filename:"+fileName, Toast.LENGTH_SHORT).show();
                try {
                    sendStopTelemetry();

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            unregisterReceiver(telemetryUpdateReceiver);
                            gotoMainActivity();
                        }
                    });
                }catch (Exception e){
                    Log.e(TAG, "onClick: main.startTelemetryData(2);::" + e);
                }
            }
        });
        registerReceiver(telemetryUpdateReceiver, makeGattUpdateIntentFilter());
    }//onCreate

    void sendStopTelemetry()
    {
        MainActivity.bluetoothService.writeCharacteristics(null, MainActivity.getStopTelemetryBytes());
        MainActivity.bluetoothService.disconnect();
    }
    void setLogMessage(String msg){
        sensorData.append("\n"+msg);
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
    }

    String getSensorLogdata()
    {
        return sensorData.getText().toString();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_CHARACTERISTICS_CHANGED);
        return intentFilter;
    }


    private final BroadcastReceiver telemetryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive-telemetryUpdateReceiver ACTION:" + action);

             if(BluetoothLeService.ACTION_CHARACTERISTICS_CHANGED.equals(action)){
                //Log.d(TAG, "onReceive: DATA:"+intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                byte[] extras = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

                 Handler handler = new Handler(Looper.getMainLooper());
                 handler.post(new Runnable() {
                     @Override
                     public void run() {
                         displayTelemetryData(extras);
                     }
                 });
            }
        }
    };

    void displayTelemetryData(byte[] sensorData){
        int length_recvd = (sensorData[BT_HEADER_PAYLOAD_LENGTH_INDEX2] | (sensorData[BT_HEADER_PAYLOAD_LENGTH_INDEX3] << 8)) ;
        Log.d(TAG, "processRecvdData: len:"+ sensorData.length);
        byte[] bytePayload = new byte[length_recvd];
        for (int  i  = 0; i < (length_recvd); i++){
            bytePayload[i] = sensorData[BT_HEADER_END_INDEX+1+i];
        }
        String strPayload = new String(bytePayload);
        strPayload = strPayload.trim();
        Date date = new Date();
        Log.d(TAG, "processRecvdData:"+date+": TELEMETRY LEN:"+length_recvd+" DATA:"+strPayload);
        setLogMessage(strPayload);
        processTheTelemetryPayload(strPayload);
    }

    private void processTheTelemetryPayload(String strPayload) {
        Log.d(TAG, "processTheTelemetryPayload: Parsing JSON DATA");
        //{"temp":000003ad,"accel":000003ad}
        JSONObject jObject = null;
        try {
            jObject = new JSONObject(strPayload);
            String speed = jObject.getString("speed");
            String vibration = jObject.getString("bootc");
            String temp = jObject.getString("tempe");
//{"tempe":02905,"speed":00000,"bootc":00029}
            int lSpeed = Integer.parseInt(speed);
            int lVibration = Integer.parseInt(vibration);
            int lTemp = Integer.parseInt(temp);
            Log.d(TAG, "processTheTelemetryPayload: speed:"+ lSpeed + " Vibration:"+lVibration + " Temp:"+lTemp);
            displaySensorData(lSpeed, lVibration, lTemp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        catch (Exception e){
            Log.e(TAG, "processTheTelemetryPayload: " +e );
        }

    }

    String getFormatedText(String str1, long str2){
        return "<font color=#cc0029><b>"+str1+"</b></font> <br/><font color=#ffcc00>"+str2+"</font>";
    }
    void displaySensorData(long speed, long vibration, long temperature){
        float fTemp = (float)temperature/100;
        float fSpeed = speed/10;
        Log.d(TAG, "displaySensorData: fTemp:"+fTemp);
        tvTemperature.setText("Temperature\n"+fTemp+" \u2103");
        tvTemperature.setTypeface(null, Typeface.BOLD);
        tvTemperature.setTextColor(Color.BLUE);

        tvSpeed.setText("Speed\n"+fSpeed + "  1/min");
        tvSpeed.setTypeface(null, Typeface.ITALIC);
        tvSpeed.setTextColor(Color.MAGENTA);

        tvVibration.setText("Boot Count:\n"+vibration);
        tvVibration.setTypeface(null, Typeface.ITALIC);
        tvVibration.setTextColor(Color.GREEN);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                sendStopTelemetry();
                gotoMainActivity();
                unregisterReceiver(telemetryUpdateReceiver);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void gotoMainActivity(){
        finish();
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);

    }
}