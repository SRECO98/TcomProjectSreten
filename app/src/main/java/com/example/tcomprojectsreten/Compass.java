package com.example.tcomprojectsreten;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

public class Compass extends AppCompatActivity implements SensorEventListener {

    /* Compass values */
    private SensorManager sensorManager;
    private Sensor magnetometerSensor;
    private Sensor accelerometerSensor;
    float[] magneticField = new float[3];
    float[] acceleration = new float[3];
    float[] rotationMatrix = new float[9];
    float[] inclinationMatrix = new float[9];
    float[] orientation = new float[3];
    int azimuth;

    TextView azimuthTextView;
    ImageView compassFrontImageView;

    /*Location values */
    boolean permissionRequested, settingsChangeRequested;
    TextView settingsStateTextView, permissionStateTextView;
    private static final int REQUEST_CHECK_SETTINGS = 1;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    LocationSettingsRequest.Builder builder;
    LocationCallback locationCallback;
    SettingsClient client;
    Task<LocationSettingsResponse> task;

    /*Distance and direction.*/
    TextView distanceValueTextView;
    Location currentLocation;
    double targetLatitude;
    double targetLongitude;
    double currentDistance;
    double roundDistanceValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);

        permissionStateTextView = findViewById(R.id.permissionStateTextView);
        settingsStateTextView = findViewById(R.id.settingsStateTextView);
        distanceValueTextView = findViewById(R.id.textViewValue);

        azimuthTextView = findViewById(R.id.azimuthTextView);
        compassFrontImageView = findViewById(R.id.foregroundImage);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        /*Getting Intent values*/
        Intent intent = getIntent();
        targetLatitude = intent.getDoubleExtra("latitude", 0);
        targetLongitude = intent.getDoubleExtra("longitude", 0);

        /* Location logic*/////////////////////////////////////////////////////////////////////////////////////////////////
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdateDelayMillis(1000)
                .build();
        builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        client = LocationServices.getSettingsClient(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @SuppressLint("SetTextI18n")
            @Override // new data about our location.
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location: locationResult.getLocations()){
                    currentLocation = location; //Our current location.
                    //calculating distance between target and our location.
                    currentDistance = distanceBetweenLocations(currentLocation.getLatitude(), currentLocation.getLongitude(), targetLatitude,  targetLongitude);
                }
                roundDistanceValue = (double) Math.round(currentDistance * 100) / 100; //making result value more beautiful, (2 decimals).
                distanceValueTextView.setText(String.valueOf(roundDistanceValue * 1000) + "m");
            }

            @Override // kada dodje do promjene raspolozivosti podataka o lokaciji
            public void onLocationAvailability(@NonNull LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);

                if(locationAvailability.isLocationAvailable()){
                    settingsStateTextView.setText("");
                }else{
                    settingsStateTextView.setText("Please enable Location data in settings.");
                }
            }
        };

        getLastKnownLocation();
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(magnetometerSensor != null && accelerometerSensor != null){  //register for listening
            sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this); //unregister from listening
        stopLocationUpdates();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()){
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(sensorEvent.values, 0, magneticField, 0, sensorEvent.values.length);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(sensorEvent.values, 0, acceleration, 0, sensorEvent.values.length);
                break;
            default:
                break;
        }

        if(SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, acceleration, magneticField)){
            float azimuthRad = SensorManager.getOrientation(rotationMatrix, orientation)[0]; //value of azimuth value is returning in radians.
            double azimuthDeg = Math.toDegrees(azimuthRad);
            azimuth = ((int)azimuthDeg + 360) % 360;
            azimuthTextView.setText(String.valueOf(azimuth) + "°");
            compassFrontImageView.setRotation(-azimuth);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void startLocationUpdates(){
        //slusanje novih vrednosti stalno
        if(ContextCompat.checkSelfPermission(Compass.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            permissionStateTextView.setText("");
            task = client.checkLocationSettings(builder.build());
            task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                @Override
                public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                    settingsStateTextView.setText("");
                    getLastKnownLocation();
                }
            });
            task.addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    //this is when locatio is not available in phone
                    if (e instanceof ResolvableApiException) {
                        try {
                            if(!settingsChangeRequested){
                                ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                                resolvableApiException.startResolutionForResult(Compass.this,
                                        REQUEST_CHECK_SETTINGS);
                                settingsChangeRequested = true;
                            }else{
                                settingsStateTextView.setText("Please enable Location data in settings.");
                            }
                        }catch(IntentSender.SendIntentException sendIntentException){
                            //ignore error
                        }
                    }
                }
            });
        }else{ //trazenje dozvole ako nema i ovo je samo jednom.
            if(!permissionRequested){
                ActivityCompat.requestPermissions(Compass.this,
                        new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
                permissionRequested = true;
            }else{
                permissionStateTextView.setText("App does not have permission for using location data.");
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null); //ako je zadnje null odvijace se sve na glavnoj niti.
    }

    private void stopLocationUpdates(){
        fusedLocationProviderClient.removeLocationUpdates(locationCallback); //prestanbak slusanja, saljemo callback hjer preko njega ide dojava o lokaciji
    }

    private void getLastKnownLocation() throws SecurityException{
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(location != null){
                    currentLocation = location;
                }else{
                    Toast.makeText(Compass.this, "There is no last known location.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    /* Logic for calculating disatnce between two locations on earth.*////////////////////
    private static final int EARTH_RADIUS = 6371; // Approx Earth radius in KM

    public static double distanceBetweenLocations(double startLat, double startLong, double endLat, double endLong) {

        double dLat  = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat   = Math.toRadians(endLat);

        double a = haversin(dLat) + Math.cos(startLat) * Math.cos(endLat) * haversin(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // <-- d Kilometers result
    }

    private static double haversin(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
}