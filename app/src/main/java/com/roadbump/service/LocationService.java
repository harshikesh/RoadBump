package com.roadbump.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.roadbump.database.DBHelper;
import com.roadbump.util.Utils;

/**
 * Created by harshikesh.kumar on 26/09/16.
 */
public class LocationService extends Service implements SensorEventListener, LocationListener ,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

  private static final String TAG = LocationService.class.getSimpleName();
  public static final String SPEED = "com.mejestic.bump.ui.MainActivity";
  private SensorManager senSensorManager;
  private Sensor senAccelerometer;

  protected LocationManager locationManager;

  private long lastUpdate = 0;
  private float last_x, last_y, last_z;
  private static final int SHAKE_THRESHOLD = 600;

  // The minimum distance to change Updates in meters
  private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // 1 meters

  // The minimum time between updates in milliseconds
  private static final long MIN_TIME_BW_UPDATES = 1000 * 1; // 1 second
  private DBHelper dbHelper;
  private Location prevLocation;
  private LocalBroadcastManager broadcaster;
  private GoogleApiClient mGoogleApiClient;
  private Location mCurrentLocation;

  @Override public void onCreate() {
    super.onCreate();
    Log.d(TAG, "Service created");
    broadcaster = LocalBroadcastManager.getInstance(this);

    senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext()).addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build();
    mGoogleApiClient.connect();
    dbHelper = new DBHelper(this);
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {

    return START_STICKY;
  }

  @Nullable @Override public IBinder onBind(Intent intent) {
    return null;
  }

  @Override public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "Service destroyed");
  }

  @Override public void onSensorChanged(SensorEvent sensorEvent) {
    Sensor mySensor = sensorEvent.sensor;
    if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
          /* -Z is the  direction where the car moves and the +Y will increase when the Bump occurs.*/
      float x = sensorEvent.values[0];
      float y = sensorEvent.values[1];
      float z = sensorEvent.values[2];

      long curTime = System.currentTimeMillis();
      if ((curTime - lastUpdate) > 1000) {
        long diffTime = (curTime - lastUpdate)/1000;
        mCurrentLocation = actualLocation();
        if (mCurrentLocation != null && mCurrentLocation.getAccuracy() > 40){
          return;
        }
        long sp =0;
        if (prevLocation != null && mCurrentLocation != null) {
          sp = Utils.calculateDistance(prevLocation, mCurrentLocation);
          Log.d(TAG,"distance : "+sp +" time : "+diffTime);
          sp = sp  / diffTime;
          sendResult(sp  / diffTime + "m/s");
        }
        prevLocation = mCurrentLocation;

        lastUpdate = curTime;
        float speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000;
        if (speed > SHAKE_THRESHOLD && sp>1) {

          if (mCurrentLocation != null) {
            Log.d(TAG, "lat : " + mCurrentLocation.getLatitude());
            dbHelper.insertData(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), x - last_x, y - last_y,
                z - last_z);
          }
          Log.d(TAG, "x  : " + x + " y : " + y + " z : " + z);
          Log.d(TAG, "speed : " + speed);
        }
      }
      last_x = x;
      last_y = y;
      last_z = z;
    }
  }

  public void sendResult(String message) {
    Intent intent = new Intent(SPEED);
    if (message != null) intent.putExtra(SPEED, message);
    broadcaster.sendBroadcast(intent);
  }

  @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }

  public Location getLocation(Context context) {
    Location location = null;
    try {
      locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);

      // getting GPS status
      boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

      // getting network status
      boolean isNetworkEnabled =
          locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

      if (!isGPSEnabled && !isNetworkEnabled) {
        // no network provider is enabled
      } else {
        if (isNetworkEnabled) {
          Utils.checkMapPermission(getApplicationContext());
          locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
              MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
          Log.d("Network", "Network");
          if (locationManager != null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
          }
        }
        // if GPS Enabled get lat/long using GPS Services
        if (isGPSEnabled) {
          if (location == null) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
            Log.d("GPS Enabled", "GPS Enabled");
            if (locationManager != null) {
              location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
          }
        }
      }
    } catch (Exception e) {
    }
    return location;
  }

  @Override public void onLocationChanged(Location location) {

  }

  @Override public void onStatusChanged(String provider, int status, Bundle extras) {

  }

  @Override public void onProviderEnabled(String provider) {

  }

  @Override public void onProviderDisabled(String provider) {

  }

  @Override public void onConnected(@Nullable Bundle bundle) {
    if (mGoogleApiClient.isConnected()) {
      Utils.checkMapPermission(getApplicationContext());
      Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
      mCurrentLocation = location;
    }
    }

  @Override public void onConnectionSuspended(int i) {

  }

  @Override public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

  }

  public @Nullable Location actualLocation() {
    if (mGoogleApiClient != null) {
      return null;
    }
    Utils.checkMapPermission(getApplicationContext());
    return LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
  }
}
