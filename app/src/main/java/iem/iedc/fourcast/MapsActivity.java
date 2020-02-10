package iem.iedc.fourcast;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    Marker marker;


    AutocompleteSupportFragment placeAutoComplete;

    RequestQueue queue;

    HashMap<String, Double> pollutants;
    int airIndex;
    String airQuality;

    double latitude;
    double longitude;

    ImageButton myLocationBtn;

    String isSelected;

    private static final String TAG = "MapsActivity";
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 10f;

    Intent mServiceIntent;
    private ApiService mApiService;

    TextView pm10, o3, pm25, co, so2, p, h, no2, t,  tv_temp, tv_humid, tv_pre, bNum, goodbad, aqi, mplace, currPoll, inff, precc;

    ImageButton pm10_b, pm25_b, o3_b, co_b, so2_b, no2_b;

    ImageView bIcon;

    ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        pm10 = findViewById(R.id.poll_pm10);
        o3 = findViewById(R.id.poll_o3);
        pm25 = findViewById(R.id.poll_pm25);
        co = findViewById(R.id.poll_co);
        so2 = findViewById(R.id.poll_so2);
        p = findViewById(R.id.poll_p);
        h = findViewById(R.id.poll_h);
        no2 = findViewById(R.id.poll_no2);
        t = findViewById(R.id.poll_t);

        tv_temp = findViewById(R.id.text_t);
        tv_humid = findViewById(R.id.text_h);
        tv_pre = findViewById(R.id.text_p);

        mplace = findViewById(R.id.myplace);

        bNum = findViewById(R.id.bigNum);
        goodbad = findViewById(R.id.goodbad);
        aqi = findViewById(R.id.aqiTxt);

        pm25_b = findViewById(R.id.pm25b);
        pm10_b = findViewById(R.id.pm10b);
        o3_b = findViewById(R.id.o3b);
        co_b = findViewById(R.id.cob);
        so2_b = findViewById(R.id.so2b);
        no2_b = findViewById(R.id.no2b);

        bIcon = findViewById(R.id.bigIcon);
        currPoll = findViewById(R.id.currPoll);

        inff = findViewById(R.id.inff);
        precc = findViewById(R.id.precss);

        progress = findViewById(R.id.progress_bar);

//        progress.setVisibility(View.GONE);
        isSelected = "pm25";


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        myLocationBtn = findViewById(R.id.mylocation);

        queue = Volley.newRequestQueue(this);
        pollutants = new HashMap<>();

        latitude = 23.0711575;
        longitude = 84.5297639;  // Center of developer's native country


        while(!hasPermission()){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 8);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 8);
        }

        displayLocationSettingsRequest(MapsActivity.this);



        initializeLocationManager();

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setCostAllowed(true);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

        try {
            mLocationManager.requestSingleUpdate(criteria, mLocationListeners[0], null);
        } catch (SecurityException e){
            Toast.makeText(getApplicationContext(), "Location access required", Toast.LENGTH_SHORT).show();
        }

        resumeLocationMonitoring();

        apiThread.start();

        mApiService = new ApiService();
        mServiceIntent = new Intent(this, mApiService.getClass());
        if (!isMyServiceRunning(mApiService.getClass())) {
            startService(mServiceIntent);
        }

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key), Locale.US);
        }

        placeAutoComplete = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.place_autocomplete);
        placeAutoComplete.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));

        placeAutoComplete.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {

                Log.e("Maps", "Place selected: " + place.getName());

                latitude = place.getLatLng().latitude;
                longitude = place.getLatLng().longitude;

                pauseLocationMonitoring();
                myLocationBtn.setVisibility(View.VISIBLE);


                LatLng pos = new LatLng(latitude, longitude);
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(pos)
                        .zoom(17)
                        .bearing(0)                // Sets the orientation of the camera to east
                        .tilt(30)                   // Sets the tilt of the camera to 30 degrees
                        .build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                try {
                    Geocoder gcd = new Geocoder(getApplicationContext(), Locale.getDefault());
                    List<Address> addresses = gcd.getFromLocation(latitude, longitude, 1);
                    if (addresses.size() > 0) {
                        mplace.setText(addresses.get(0).getLocality());
                    } else {
                        Toast.makeText(getApplicationContext(), "Cannot get locality", Toast.LENGTH_SHORT).show();
                        mplace.setText("--");
                    }
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "Cannot get locality", Toast.LENGTH_SHORT).show();
                    mplace.setText("--");
                }
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(Status status) {
                Log.d("Maps", "An error occurred: " + status);
            }
        });

        myLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeLocationMonitoring();
                myLocationBtn.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }
        });


        pm25_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSelected = "pm25";
                if(null != pollutants.get("pm25")) {
                    if(pollutants.get("pm25") >= 100)
                        bNum.setText(pollutants.get("pm25").intValue() + "");
                    else
                        bNum.setText(pollutants.get("pm25").toString());
                }
                else
                    bNum.setText("--");

                bIcon.setImageResource(R.drawable.ic_pm25);
                currPoll.setText("Patricuate Matter \n(2.5\u00B5m)");

                inff.setText(getString(R.string.pm25_info));
                precc.setText(getString(R.string.pm25_prec));
            }
        });

        pm10_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSelected = "pm10";
                if(null != pollutants.get("pm10")) {
                    if(pollutants.get("pm10") >= 100)
                        bNum.setText(pollutants.get("pm10").intValue() + "");
                    else
                        bNum.setText(pollutants.get("pm10").toString());
                }
                else
                    bNum.setText("--");

                bIcon.setImageResource(R.drawable.ic_pm10);
                currPoll.setText("Patricuate Matter \n(10\u00B5m)");

                inff.setText(getString(R.string.pm10_info));
                precc.setText(getString(R.string.pm10_prec));
            }
        });

        o3_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSelected = "o3";
                if(null != pollutants.get("o3")){
                    if(pollutants.get("o3") >= 100)
                        bNum.setText(pollutants.get("o3").intValue() + "");
                    else
                        bNum.setText(pollutants.get("o3").toString());
                }
                else
                    bNum.setText("--");

                bIcon.setImageResource(R.drawable.ic_o3);
                currPoll.setText("Ozone");

                inff.setText(getString(R.string.o3_info));
                precc.setText(getString(R.string.o3_prec));
            }
        });

        so2_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSelected = "so2";
                if(null != pollutants.get("so2")){
                    if(pollutants.get("so2") >= 100)
                        bNum.setText(pollutants.get("so2").intValue() + "");
                    else
                        bNum.setText(pollutants.get("so2").toString());
                }
                else
                    bNum.setText("--");

                bIcon.setImageResource(R.drawable.ic_so2);
                currPoll.setText("Sulfur Dioxide");

                inff.setText(getString(R.string.so2_info));
                precc.setText(getString(R.string.so2_prec));
            }
        });

        co_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSelected = "co";
                if(null != pollutants.get("co")){
                    if(pollutants.get("co") >= 100)
                        bNum.setText(pollutants.get("co").intValue() + "");
                    else
                        bNum.setText(pollutants.get("co").toString());
                }
                else
                    bNum.setText("--");

                bIcon.setImageResource(R.drawable.ic_co);
                currPoll.setText("Carbon Monoxide");

                inff.setText(getString(R.string.co_info));
                precc.setText(getString(R.string.co_prec));
            }
        });

        no2_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSelected = "no2";
                if(null != pollutants.get("no2")){
                    if(pollutants.get("no2") >= 100)
                        bNum.setText(pollutants.get("no2").intValue() + "");
                    else
                        bNum.setText(pollutants.get("no2").toString());
                }
                else
                    bNum.setText("--");

                bIcon.setImageResource(R.drawable.ic_no2);
                currPoll.setText("Nitrogen Dioxide");

                inff.setText(getString(R.string.no2_info));
                precc.setText(getString(R.string.no2_prec));
            }
        });
    }

    public void pauseLocationMonitoring() {
        mLocationManager.removeUpdates(mLocationListeners[0]);
        mLocationManager.removeUpdates(mLocationListeners[1]);
    }

    public void resumeLocationMonitoring() {
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }


    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.d(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }
        @Override
        public void onLocationChanged(Location location) {
            mLastLocation.set(location);

            latitude = mLastLocation.getLatitude();
            longitude = mLastLocation.getLongitude();

            Log.d(TAG, "onLocationChanged: " + latitude + " ; " + longitude);

            LatLng pos = new LatLng(latitude, longitude);
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(pos)
                    .zoom(17)
                    .bearing(0)                // Sets the orientation of the camera to east
                    .tilt(30)                   // Sets the tilt of the camera to 30 degrees
                    .build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            marker.setPosition(pos);
            marker.setVisible(true);

            try {
                Geocoder gcd = new Geocoder(getApplicationContext(), Locale.getDefault());
                List<Address> addresses = gcd.getFromLocation(latitude, longitude, 1);
                if (addresses.size() > 0) {
                    mplace.setText(addresses.get(0).getLocality());
                } else {
                    Toast.makeText(getApplicationContext(), "Cannot get locality", Toast.LENGTH_SHORT).show();
                    mplace.setText("--");
                }
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Cannot get locality", Toast.LENGTH_SHORT).show();
                mplace.setText("--");
            }

        }
        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG, "onProviderDisabled: " + provider);
        }
        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG, "onProviderEnabled: " + provider);
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, "onStatusChanged: " + provider);
        }

    }

    LocationListener[] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    Thread apiThread = new Thread() {
        @Override
        public void run() {
            try {
                while (!apiThread.isInterrupted()) {
                    Thread.sleep(5000);
                    runOnUiThread(() -> {
                        get_data(latitude + "", longitude + "");
                    });
                }
            } catch (InterruptedException e) {
                Log.e("API thread", e.getMessage());
            }
        }
    };



    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng pos = new LatLng(latitude, longitude);
        marker = mMap.addMarker(new MarkerOptions()
                .position(pos).title("My location")); //.icon(BitmapDescriptorFactory.fromBitmap(smallMarker)));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
        marker.setVisible(false);
    }

    public boolean hasPermission() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(this , new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return false;
        }
    }

    void get_data(String latitude, String longitude) {
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());

        final String URL = "https://api.waqi.info/feed/geo:" +latitude+ ";" +longitude+ "/?token="+getApplicationContext().getResources().getString(R.string.token);

        JsonObjectRequest request = new JsonObjectRequest(URL, new JSONObject(),
                (JSONObject response) -> {
                        try {
                            //Log.d("Response", response.toString());

                            airQuality = "--";
                            airIndex = -1;

                            pollutants = new HashMap<>();

                            boolean status = response.getString("status").equals("ok");

                            if(status) {
                                airIndex = response.getJSONObject("data").getInt("aqi");

                                response = response.getJSONObject("data").getJSONObject("iaqi");
                                Iterator<String> keys = response.keys();

                                while(keys.hasNext()) {
                                    String key = keys.next();
//                                    if(null != poll.get(key)) {
                                    if (response.get(key) instanceof JSONObject) {
                                        //pollutants.put(key, Double.parseDouble(response.getJSONObject(key).getString("v")));
                                        Log.d("Notification Update", "Success");
                                        pollutants.put(key, Double.parseDouble(response.getJSONObject(key).getString("v")));

//                                        }
                                    }
                                }


                                if(airIndex <= 50) {
                                    airQuality = "Good";
                                    goodbad.setTextColor(Color.parseColor("#43A047"));
                                }
                                else if(airIndex <= 100) {
                                    airQuality = "Moderate";
                                    goodbad.setTextColor(Color.parseColor("#1565C0"));
                                }
                                else if(airIndex <= 150) {
                                    airQuality = "Unhealty for some";
                                    goodbad.setTextColor(Color.parseColor("#FFC400"));
                                }
                                else if(airIndex <= 200) {
                                    airQuality = "Unhealthy";
                                    goodbad.setTextColor(Color.parseColor("#FB8C00"));
                                }
                                else if(airIndex <= 300) {
                                    airQuality = "Very Unhealty";
                                    goodbad.setTextColor(Color.parseColor("#C2185B"));
                                }
                                else {
                                    airQuality = "Hazardous";
                                    goodbad.setTextColor(Color.parseColor("#b71c1c"));
                                }

                                pm10.setText("--");
                                o3.setText("--");
                                pm25.setText("--");
                                co.setText("--");
                                so2.setText("--");
                                p.setText("--");
                                h.setText("--");
                                no2.setText("--");
                                t.setText("--");

                                // TODO update UI
                                if (null != pollutants.get("pm10")) {
                                    pm10.setText(pollutants.get("pm10").toString());
                                }
                                if (null != pollutants.get("o3")) {
                                    o3.setText(pollutants.get("o3").toString());
                                }
                                if (null != pollutants.get("pm25")) {
                                    pm25.setText(pollutants.get("pm25").toString());
                                }
                                if (null != pollutants.get("co")) {
                                    co.setText(pollutants.get("co").toString());
                                }
                                if (null != pollutants.get("so2")) {
                                    so2.setText(pollutants.get("so2").toString());
                                }
                                if (null != pollutants.get("p")) {
                                    p.setText(pollutants.get("p").intValue()+"");
                                    tv_pre.setText(pollutants.get("p").intValue()+" mbr");
                                }
                                if (null != pollutants.get("h")) {
                                    h.setText(String.format("%.2f", pollutants.get("h")));
                                    tv_humid.setText(String.format("%.2f", pollutants.get("h"))+" %");
                                }
                                if (null != pollutants.get("no2")) {
                                    no2.setText(pollutants.get("no2").toString());
                                }
                                if (null != pollutants.get("t")) {
                                    t.setText(String.format("%.2f", pollutants.get("t")));
                                    tv_temp.setText(String.format("%.2f", pollutants.get("t"))+" °C");
                                }

                                if(isSelected.equals("pm25")){
                                    if(null != pollutants.get("pm25")){
                                        if(pollutants.get("pm25") >= 100)
                                            bNum.setText(pollutants.get("pm25").intValue() + "");
                                        else
                                            bNum.setText(pollutants.get("pm25").toString());
                                    }
                                    else
                                        bNum.setText("--");
                                }
                                if(isSelected.equals("pm10")){
                                    if(null != pollutants.get("pm10")){
                                        if(pollutants.get("pm10") >= 100)
                                            bNum.setText(pollutants.get("pm10").intValue() + "");
                                        else
                                            bNum.setText(pollutants.get("pm10").toString());
                                    }
                                    else
                                        bNum.setText("--");
                                }
                                if(isSelected.equals("o3")){
                                    if(null != pollutants.get("o3")){
                                        if(pollutants.get("o3") >= 100)
                                            bNum.setText(pollutants.get("o3").intValue() + "");
                                        else
                                            bNum.setText(pollutants.get("o3").toString());
                                    }
                                    else
                                        bNum.setText("--");
                                }
                                if(isSelected.equals("co")){
                                    if(null != pollutants.get("co")){
                                        if(pollutants.get("co") >= 100)
                                            bNum.setText(pollutants.get("co").intValue() + "");
                                        else
                                            bNum.setText(pollutants.get("co").toString());
                                    }
                                    else
                                        bNum.setText("--");
                                }
                                if(isSelected.equals("so2")){
                                    if(null != pollutants.get("so2")){
                                        if(pollutants.get("so2") >= 100)
                                            bNum.setText(pollutants.get("so2").intValue() + "");
                                        else
                                            bNum.setText(pollutants.get("so2").toString());
                                    }
                                    else
                                        bNum.setText("--");
                                }
                                if(isSelected.equals("no2")){
                                    if(null != pollutants.get("no2")){
                                        if(pollutants.get("no2") >= 100)
                                            bNum.setText(pollutants.get("no2").intValue() + "");
                                        else
                                            bNum.setText(pollutants.get("no2").toString());
                                    }
                                    else
                                        bNum.setText("--");
                                }

                                goodbad.setText(airQuality);
                                aqi.setText(airIndex+"");

                                progress.setVisibility(View.GONE);

                            }
                        } catch (Exception e) {
                            Log.e("Error: ", e.toString());
                        }
                    
                }, (VolleyError error) -> {
                    Log.e("Error: ", "");
                }
        );

        request.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return 50000;
            }

            @Override
            public int getCurrentRetryCount() {
                return 50000;
            }

            @Override
            public void retry(VolleyError error) throws VolleyError {}
        });

        queue.add(request);
    }

    private void displayLocationSettingsRequest(Context context) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000 / 2);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback((LocationSettingsResult locationSettingsResult) -> {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.i(TAG, "All location settings are satisfied.");
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings ");
                        try {
                            status.startResolutionForResult(MapsActivity.this, 0x1);
                        } catch (IntentSender.SendIntentException e) {
                            Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        break;
                }
        });
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i ("Service status", "Running");
                return true;
            }
        }
        Log.i ("Service status", "Not running");
        return false;
    }

    @Override
    protected void onDestroy() {
        stopService(mServiceIntent);
        super.onDestroy();
    }
}

