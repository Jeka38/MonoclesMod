package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.math.DoubleMath;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import java.lang.ref.WeakReference;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityShareLocactionBinding;
import eu.siacs.conversations.ui.util.LocationHelper;
import eu.siacs.conversations.ui.widget.Marker;
import eu.siacs.conversations.ui.widget.MyLocation;
import eu.siacs.conversations.utils.LocationProvider;
import eu.siacs.conversations.utils.ThemeHelper;

public class ShareLocationActivity extends LocationActivity implements LocationListener {

    private Snackbar snackBar;
    private ActivityShareLocactionBinding binding;
    private boolean marker_fixed_to_loc = false;
    private static final String KEY_FIXED_TO_LOC = "fixed_to_loc";
    private Boolean noAskAgain = false;

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FIXED_TO_LOC, marker_fixed_to_loc);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey(KEY_FIXED_TO_LOC)) {
            this.marker_fixed_to_loc = savedInstanceState.getBoolean(KEY_FIXED_TO_LOC);
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onBackendConnected() {

    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_share_locaction);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        setupMapView(binding.map, LocationProvider.getGeoPoint(this));

        this.binding.cancelButton.setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        this.snackBar = Snackbar.make(this.binding.snackbarCoordinator, R.string.location_sharing_disabled, Snackbar.LENGTH_INDEFINITE);
        this.snackBar.setAction(R.string.enable, view -> {
            if (isLocationEnabledAndAllowed()) {
                updateUi();
            } else if (!hasLocationPermissions()) {
                requestPermissions(REQUEST_CODE_SNACKBAR_PRESSED);
            } else if (!isLocationEnabled()) {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
        ThemeHelper.fix(this.snackBar);

        this.binding.shareButton.setOnClickListener(this::shareLocation);

        this.marker_fixed_to_loc = isLocationEnabledAndAllowed();

        this.binding.fab.setOnClickListener(view -> {
            if (!marker_fixed_to_loc) {
                if (!isLocationEnabled()) {
                    startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                } else {
                    requestPermissions(REQUEST_CODE_FAB_PRESSED);
                }
            }
            toggleFixedLocation();
        });
    }

    private void shareLocation(final View view) {
        final Intent result = new Intent();
        if (marker_fixed_to_loc && myLoc != null) {
            result.putExtra("latitude", myLoc.getLatitude());
            result.putExtra("longitude", myLoc.getLongitude());
            result.putExtra("altitude", myLoc.getAltitude());
            result.putExtra("accuracy", DoubleMath.roundToInt(myLoc.getAccuracy(), RoundingMode.HALF_UP));
        } else {
            final IGeoPoint markerPoint = this.binding.map.getMapCenter();
            result.putExtra("latitude", markerPoint.getLatitude());
            result.putExtra("longitude", markerPoint.getLongitude());
        }
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 &&
                grantResults[0] != PackageManager.PERMISSION_GRANTED &&
                Build.VERSION.SDK_INT >= 23 &&
                permissions.length > 0 &&
                (
                        Manifest.permission.LOCATION_HARDWARE.equals(permissions[0]) ||
                                Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[0]) ||
                                Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[0])
                ) &&
                !shouldShowRequestPermissionRationale(permissions[0])) {
            noAskAgain = true;
        }

        if (!noAskAgain && requestCode == REQUEST_CODE_SNACKBAR_PRESSED && !isLocationEnabled() && hasLocationPermissions()) {
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
        updateUi();
    }

    @Override
    protected void gotoLoc(final boolean setZoomLevel) {
        if (this.myLoc != null && mapController != null) {
            if (setZoomLevel) {
                mapController.setZoom(Config.Map.FINAL_ZOOM_LEVEL);
            }
            mapController.animateTo(new GeoPoint(this.myLoc));
        }
    }

    @Override
    protected void setMyLoc(final Location location) {
        this.myLoc = location;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void updateLocationMarkers() {
        super.updateLocationMarkers();
        if (this.myLoc != null) {
            this.binding.map.getOverlays().add(new MyLocation(this, null, this.myLoc));
            if (this.marker_fixed_to_loc) {
                this.binding.map.getOverlays().add(new Marker(marker_icon, new GeoPoint(this.myLoc)));
                new getAddressAsync(this, this.myLoc).execute();
            } else {
                this.binding.map.getOverlays().add(new Marker(marker_icon));
                new getAddressAsync(this, getMarkerPosition()).execute();
            }
        } else {
            this.binding.map.getOverlays().add(new Marker(marker_icon));
            hideAddress();
        }
    }

    private Location getMarkerPosition() {
        final IGeoPoint markerPoint = this.binding.map.getMapCenter();
        final Location location = new Location("");
        location.setLatitude(markerPoint.getLatitude());
        location.setLongitude(markerPoint.getLongitude());
        return location;
    }

    private void showAddress(final Location myLoc) {
        this.binding.address.setText(Html.fromHtml(getAddress(this, myLoc)));
        if (Html.fromHtml(getAddress(this, myLoc)).length() > 0) {
            this.binding.address.setVisibility(View.VISIBLE);
        } else {
            hideAddress();
        }
    }

    private void hideAddress() {
        this.binding.address.setVisibility(View.GONE);
    }

    @Override
    public void onLocationChanged(final Location location) {
        if (this.myLoc == null) {
            this.marker_fixed_to_loc = true;
        }
        updateUi();
        if (LocationHelper.isBetterLocation(location, this.myLoc)) {
            final Location oldLoc = this.myLoc;
            this.myLoc = location;

            // Don't jump back to the users location if they're not moving (more or less).
            if (oldLoc == null || (this.marker_fixed_to_loc && this.myLoc.distanceTo(oldLoc) > 1)) {
                gotoLoc();
            }
            updateLocationMarkers();
        }
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {

    }

    @Override
    public void onProviderEnabled(final String provider) {

    }

    @Override
    public void onProviderDisabled(final String provider) {

    }

    private boolean isLocationEnabledAndAllowed() {
        return this.hasLocationFeature && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || this.hasLocationPermissions()) && this.isLocationEnabled();
    }

    private void toggleFixedLocation() {
        this.marker_fixed_to_loc = isLocationEnabledAndAllowed() && !this.marker_fixed_to_loc;
        if (this.marker_fixed_to_loc) {
            gotoLoc(false);
        }
        updateLocationMarkers();
        updateUi();
    }

    @Override
    protected void updateUi() {
        if (!hasLocationFeature || noAskAgain || isLocationEnabledAndAllowed()) {
            this.snackBar.dismiss();
        } else {
            this.snackBar.show();
        }

        if (isLocationEnabledAndAllowed()) {
            this.binding.fab.setVisibility(View.VISIBLE);
            runOnUiThread(() -> {
                this.binding.fab.setImageResource(marker_fixed_to_loc ? R.drawable.ic_gps_fixed_white_24dp :
                        R.drawable.ic_gps_not_fixed_white_24dp);
                this.binding.fab.setContentDescription(getResources().getString(
                        marker_fixed_to_loc ? R.string.action_unfix_from_location : R.string.action_fix_to_location
                ));
                this.binding.fab.invalidate();
            });
        } else {
            this.binding.fab.setVisibility(View.GONE);
        }
    }

    private static String getAddress(final Context context, final Location location) {
        final double longitude = location.getLongitude();
        final double latitude = location.getLatitude();
        String address = "";
        if (latitude != 0 && longitude != 0) {
            try {
                final Geocoder geoCoder = new Geocoder(context, Locale.getDefault());
                final List<Address> addresses = geoCoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && addresses.size() > 0) {
                    final Address Address = addresses.get(0);
                    StringBuilder strAddress = new StringBuilder("");

                    if (Address.getAddressLine(0).length() > 0) {
                        strAddress.append(Address.getAddressLine(0));
                    }
                    address = strAddress.toString().replace(", ", "<br>");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return address;
    }

    private class getAddressAsync extends AsyncTask<Void, Void, Void> {
        String address = null;
        Location location;

        private WeakReference<ShareLocationActivity> activityReference;

        getAddressAsync(final ShareLocationActivity context, final Location location) {
            activityReference = new WeakReference<>(context);
            this.location = location;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            address = getAddress(ShareLocationActivity.this, this.location);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            showAddress(this.location);
        }
    }
}