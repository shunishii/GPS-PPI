package com.example.gpsppi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.location.LocationListener;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends WearableActivity implements SensorEventListener, View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = "MainActivity";    //Log用のタグ名
    private TextView textView;
    private SensorManager sensorManager;
    private int cnt = 0;
    private ArrayList<Float> ppiData;                   //測定値を格納する配列
    private ArrayList<Float> timeData;                  //測定時間を格納する配列
    private int ppi_state = 0;                              //測定中(1) or 待機中(0)
    private int register = 0;                           //位置登録するなら(1)
    private static final float NS2MS = 1.0f / 1000000.0f;
    private float time;
    private float timestamp;

    private GoogleApiClient mGoogleApiClient;
    private static final long UPDATE_INTERVAL_MS = 5000;
    private static final long FASTEST_INTERVAL_MS = 5000;

    private double spot[] = new double[2];

    public void init() {
        spot[0] = 0;
        spot[1] = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        textView = findViewById(R.id.state);

        /*buttonに関する動作の設定*/
        Button ppi_button = findViewById(R.id.ppi_button);
        ppi_button.setOnClickListener(this);

        Button location_button = findViewById(R.id.location_button);
        location_button.setOnClickListener(this);

        if (!hasGPS()) {
            Log.d(TAG, "no GPS");
        } else {
            Log.d(TAG, "has GPS");
        }
        mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        mGoogleApiClient.connect();

        init();

        Log.d(TAG, "onCreated");
    }

    private boolean hasGPS() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    @Override
    public void onClick(View v) {                    //ボタンが押された場合の処理
        switch (v.getId()) {
            case R.id.ppi_button:
                if (ppi_state == 0) {                               // 1回目のクリックで測定を開始
                    startPPI();
                } else {                                        // 2回目のクリックでデータを保存
                    stopPPI();
                }
                break;
            case R.id.location_button:
                registerLocation();
        }
    }

    public void registerLocation() {
        register = 1;
    }

    public void startPPI() {
        ppi_state = 1;
        cnt = 0;
        time = 0;
        timestamp = 0;
        ppiData = new ArrayList();
        timeData = new ArrayList();
        textView.setText(String.valueOf(cnt));
        Log.d(TAG, "Start PPI");
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        List<Sensor> ppi_sensor = sensorManager.getSensorList(65547);
        sensorManager.registerListener(this, ppi_sensor.get(0), SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stopPPI() {
        ppi_state = 0;
        textView.setText(R.string.waiting);
        Log.d(TAG, "StopPPI");
        createFile();
        sensorManager.unregisterListener(this);
    }

    private void createFile() {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_kkmmss");
        String filename = sdf.format(date) + ".csv";
        Log.d(TAG, filename);
        try {
            FileOutputStream fout = openFileOutput(filename, MODE_PRIVATE);
            int i;
            String comma = ",";
            String newline = "\n";
            for (i = 0; i < ppiData.size(); i++) {
                fout.write(String.valueOf(ppiData.get(i)).getBytes());
                fout.write(comma.getBytes());
                fout.write(String.valueOf(timeData.get(i)).getBytes());
                fout.write(newline.getBytes());
            }
            fout.close();
            Log.d(TAG, "File created.");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Cannot open file.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "Cannot write string.");
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        Log.d(TAG, "Destroy");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (ppi_state == 1) {
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2MS;
                time += dT;
            }
            timestamp = event.timestamp;
            cnt++;
            textView.setText(String.valueOf(cnt));
            Log.d(TAG, "ppi:"+ cnt + ", 0:" + event.values[0] + "(stored) " + Math.round(time));
            ppiData.add(event.values[0]);
            timeData.add(time);
        } else {
            Log.d(TAG, "ppi:"+ cnt + ", 0:" + event.values[0] + "(no)");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onConnected(/*@Nullable*/ Bundle bundle) {
        Log.i(TAG, "onConnected");
        LocationRequest locationRequest = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(UPDATE_INTERVAL_MS).setFastestInterval(FASTEST_INTERVAL_MS);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            Log.i(TAG, "No permission.");
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "onConnectionFailed");
    }

    @Override
    public void onLocationChanged(Location location) {
        double lati = location.getLatitude();
        double longi = location.getLongitude();
        Log.i(TAG, "Latitude=" + lati + ", Longitude=" + longi);
        if (register == 1) {
            register = 0;
            spot[0] = lati;
            spot[1] = longi;
            Toast.makeText(this, "registered location", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "spot: ("+spot[0] + ", " + spot[1] + ")");
        } else {
            if (judge(spot, lati, longi)) {
                if (ppi_state == 0) {
                    startPPI();
                    Log.i(TAG, "in");
                    Toast.makeText(this, "in", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (ppi_state == 1) {
                    stopPPI();
                    Log.i(TAG, "out");
                    Toast.makeText(this, "out", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public boolean judge (double[] spot, double lati, double longi) {
        double r = 0.0002000;
        if (spot[0] - r < lati && spot[0] + r > lati && spot[1] - r < longi && spot[1] + r > longi) {
            return true;
        } else {
            return false;
        }
    }
}
