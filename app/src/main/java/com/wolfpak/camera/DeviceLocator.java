package com.wolfpak.camera;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * A class that handles device location coordinates and what do to if it loses connection
 * @author Roland Fong
 */
public class DeviceLocator {

    private static double longitude = 0;
    private static double latitude = 0;
    private static Activity mActivity;

    /**
     * Sets the activity for the device locator and begins listening to device location
     */
    public static void setActivity(Activity activity)   {
        mActivity = activity;
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // New location found by network location provider
                longitude = location.getLongitude();
                latitude = location.getLatitude();
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    public static double getLongitude() {
        return longitude;
    }

    public static double getLatitude() {
        return latitude;
    }
}
