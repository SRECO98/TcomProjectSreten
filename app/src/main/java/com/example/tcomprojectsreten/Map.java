package com.example.tcomprojectsreten;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

public class Map extends AppCompatActivity implements OnMapReadyCallback {

    SupportMapFragment mapFragment;
    AppCompatButton buttonNavigation;
    AppCompatButton buttonCompass;
    TextView settingsStateTextView;
    TextView permissionStateTextView;
    boolean permissionRequested;
    boolean settingsChangeRequested;
    double currentLatitude;
    double currentLongitude;
    MenuItem buttonTarget;

    private static final int REQUEST_CHECK_SETTINGS = 1;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    LocationSettingsRequest.Builder builder;
    LocationCallback locationCallback;
    SettingsClient client;
    Task<LocationSettingsResponse> task;

    LatLng demoLocation, targetLocation;
    GoogleMap googleMap;
    Marker marker, marker2;
    Bitmap markerBitmap, markerBitmap2;
    private boolean cameraCalibrated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(Map.this);
        buttonNavigation = findViewById(R.id.buttonNavigation);
        buttonCompass = findViewById(R.id.buttonCompass);
        permissionStateTextView = findViewById(R.id.permissionStateTextView);
        settingsStateTextView = findViewById(R.id.settingsStateTextView);

        buttonNavigation.setOnClickListener(v -> {

        });

        buttonCompass.setOnClickListener(v -> {

        });

        BitmapDrawable bitmapDraw = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.location_marker);
        BitmapDrawable bitmapDraw2 = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.flag);

        Bitmap b = bitmapDraw.getBitmap();
        Bitmap b2 = bitmapDraw2.getBitmap();
        int width = 120;
        int height = 120;
        markerBitmap = Bitmap.createScaledBitmap(b, width, height, false);
        markerBitmap2 = Bitmap.createScaledBitmap(b2, width, height, false);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdateDelayMillis(1000)
                .build();
        builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        client = LocationServices.getSettingsClient(this);


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override // novi pdoaci o lokaciji
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);

                for (Location location: locationResult.getLocations()){
                    if(!cameraCalibrated){
                        initialMapCameraCalibration(location);
                    }else{
                        /*if (location.getAccuracy() > 30) //izlazi ako nije precizan podatak
                            return;*/

                        drawMarker(location);

                    }

                    //displayLocationData(location);
                }
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

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        try {
            boolean success = this.googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));

            if (!success) {
                Log.e("gmap", "Style parsing failed.");
            }
        }catch (Resources.NotFoundException e){
            Log.e("gmap", "Can't find style. Error: ", e);
        }

        this.googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull LatLng latLng) {
                drawMarker(latLng);
                targetLocation = latLng; //position of our target location.
            }
        });
        //putMarkerOnMap();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void startLocationUpdates(){
        //slusanje novih vrednosti stalno
        if(ContextCompat.checkSelfPermission(Map.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
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
                                resolvableApiException.startResolutionForResult(Map.this,
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
                ActivityCompat.requestPermissions(Map.this,
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
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void getLastKnownLocation() throws SecurityException{
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(location != null){
                    displayLocationData(location);
                    drawMarker(location);
                }else{
                    Toast.makeText(Map.this, "There is no last known location.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void displayLocationData(Location location){
        currentLatitude = location.getLatitude();
        currentLongitude = location.getLongitude();
        demoLocation = new LatLng(currentLatitude, currentLongitude);
    }

    private void initialMapCameraCalibration(Location location) {
        demoLocation = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(demoLocation)
                .bearing(90)
                .zoom(17)
                .build();

        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);
        cameraCalibrated = true;
    }

    private void drawMarker(Location location) {  //Our Location on map
        if(marker == null){
            marker = googleMap.addMarker(new MarkerOptions()
                    .position(demoLocation)
                    .title("You are here")
                    .snippet("Your current location.")
                    .alpha(0.8f)
                    .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap)));
        }else{
            marker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
        }
    }
    private void drawMarker(LatLng latLng) {   // Location when we press on map!
        if(marker2 == null){
            marker2 = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Target location")
                    .snippet("This is our goal location.")
                    .alpha(0.8f)
                    .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap2)));
        }else{
            marker2.setPosition(new LatLng(latLng.latitude, latLng.longitude));
        }
    }


    private void onFlyButtonClick(){  //Pritisak na dugme iz actionbar-a aktivira ovu logiku.
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(demoLocation)
                .zoom(17) //nivo priblizenja
                .bearing(90)
                .tilt(30)
                .build();

        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 3000, null);
        cameraCalibrated = true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.targetButton) {
            onFlyButtonClick();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {  //dodavanje ikonice u action bar.
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        buttonTarget = menu.findItem(R.id.targetButton);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }


}