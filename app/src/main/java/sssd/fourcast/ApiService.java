package sssd.fourcast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.ContentValues.TAG;

public class ApiService extends Service {
    private static final int NOTIF_ID=1;

    private static final String TAG = "Fourcast_Service";
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 10f;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotification();

        initializeLocationManager();
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

    private void createNotification()
    {
        String NOTIFICATION_CHANNEL_ID = "sssd.fourcast";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            String channelName = "Background Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            manager.createNotificationChannel(chan);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running in background")
                .setContentText("")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.common_google_signin_btn_icon_light_normal_background)
                .build();

        startForeground(NOTIF_ID, notification);
        manager.notify(NOTIF_ID , notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
    }

    private class LocationListener implements android.location.LocationListener
    {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.d(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }
        @Override
        public void onLocationChanged(Location location) {
            mLastLocation.set(location);

            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            Log.d(TAG, "onLocationChanged: " + latitude + " ; " + longitude);
            get_data(latitude+"", longitude+"");
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
            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            Log.d(TAG, "onStatusChanged: " + latitude + " ; " + longitude);
            get_data(latitude+"", longitude+"");

        }

        void get_data(String latitude, String longitude) {
            RequestQueue queue = Volley.newRequestQueue(getApplicationContext());

            final String URL = "https://api.waqi.info/feed/geo:" +latitude+ ";" +longitude+ "/?token="+getApplicationContext().getResources().getString(R.string.token);

            JsonObjectRequest request = new JsonObjectRequest(URL, new JSONObject(),
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                //Log.d("Response", response.toString());

                                String airQuality = "Cannot be determined";

                                HashMap<String, Double> pollutants = new HashMap<>();
                                HashMap<String, String> poll = new HashMap<>();
                                poll.put("o3", "Ozone");
                                poll.put("co", "Carbon Monoxide");
                                poll.put("no2", "Nitrogen Dioxide");
                                poll.put("so2", "Sulfur Dioxide");
                                poll.put("pm25", "Patricuate Matter (2.5\u00B5m)");
                                poll.put("pm10", "Patricuate Matter (10\u00B5m)");

                                boolean status = response.getString("status").equals("ok");

                                if(status) {
                                    response = response.getJSONObject("data").getJSONObject("iaqi");
                                    Iterator<String> keys = response.keys();
                                    String update = "";

                                    while(keys.hasNext()) {
                                        String key = keys.next();
                                        if(null != poll.get(key)) {
                                            if (response.get(key) instanceof JSONObject) {
                                                //pollutants.put(key, Double.parseDouble(response.getJSONObject(key).getString("v")));
                                                Log.d("Notification Update", "Success");
                                                pollutants.put(poll.get(key), Double.parseDouble(response.getJSONObject(key).getString("v")));

                                                update += poll.get(key) + ": " + response.getJSONObject(key).getString("v") + "\n";
                                            }
                                        }
                                    }


                                    if(null!=pollutants.get("Patricuate Matter (2.5\u00B5m)")) {
                                        double pm25 = pollutants.get("Patricuate Matter (2.5\u00B5m)");

                                        if(pm25 <= 50)
                                            airQuality = "Good";
                                        else if(pm25 <= 100)
                                            airQuality = "Moderate";
                                        else if(pm25 <= 150)
                                            airQuality = "Unhealty for sensitive groups";
                                        else if(pm25 <= 200)
                                            airQuality = "Unhealthy";
                                        else if(pm25 <= 300)
                                            airQuality = "Very Unhealty";
                                        else
                                            airQuality = "Hazardous";
                                    }

                                    updateNotification(update, airQuality);
                                }
                            } catch (Exception e) {
                                Log.e("Error: ", e.toString());
                            }
                        }
                        private void updateNotification(String update, String aQ)
                        {
                            String NOTIFICATION_CHANNEL_ID = "sssd.fourcast";
                            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                                String channelName = "Background Service";
                                NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
                                chan.setLightColor(Color.BLUE);
                                chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

                                manager.createNotificationChannel(chan);
                            }

                            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID);
                            Notification notification = notificationBuilder.setOngoing(true)
                                    .setContentTitle("Air Quality: " + aQ)
                                    .setOnlyAlertOnce(true)
                                    .setStyle(new NotificationCompat.BigTextStyle().bigText(update))
                                    .setContentText("Show details")
                                    //.setAutoCancel(true)
                                    .setCategory(Notification.CATEGORY_SERVICE)
                                    .setSmallIcon(R.drawable.common_google_signin_btn_icon_light_normal_background)
                                    .build();


                            //startForeground(NOTIF_ID, notification);
                            manager.notify(NOTIF_ID , notification);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("Error: ", "");
                }
            });

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
                public void retry(VolleyError error) throws VolleyError {

                }
            });

            queue.add(request);
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


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}