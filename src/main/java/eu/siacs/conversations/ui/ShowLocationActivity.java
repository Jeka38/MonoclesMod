package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import org.jetbrains.annotations.NotNull;
import org.osmdroid.util.GeoPoint;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

import de.monocles.chat.SignUpPage;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityShowLocationBinding;
import eu.siacs.conversations.ui.util.LocationHelper;
import eu.siacs.conversations.ui.widget.Marker;
import eu.siacs.conversations.ui.widget.MyLocation;
import eu.siacs.conversations.utils.LocationProvider;
import me.drakeet.support.toast.ToastCompat;

public class ShowLocationActivity extends LocationActivity implements LocationListener {

    private GeoPoint loc = LocationProvider.FALLBACK;
    private ActivityShowLocationBinding binding;
    private String name;

    private Uri createGeoUri() {
        return Uri.parse("geo:" + this.loc.getLatitude() + "," + this.loc.getLongitude());
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_show_location);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());

        configureActionBar(getSupportActionBar());
        setupMapView(this.binding.map, this.loc);

        this.binding.fab.setOnClickListener(view -> startNavigation());

        final Intent intent = getIntent();
        if (intent != null) {
            this.name = intent.hasExtra("name") ? intent.getStringExtra("name") : null;
            if (intent.hasExtra("longitude") && intent.hasExtra("latitude")) {
                final double longitude = intent.getDoubleExtra("longitude", 0);
                final double latitude = intent.getDoubleExtra("latitude", 0);
                this.loc = new GeoPoint(latitude, longitude);
            }

        }
        updateLocationMarkers();
    }

    @Override
    protected void gotoLoc(final boolean setZoomLevel) {
        if (this.loc != null && mapController != null) {
            if (setZoomLevel) {
                mapController.setZoom(Config.Map.FINAL_ZOOM_LEVEL);
            }
            mapController.animateTo(new GeoPoint(this.loc));
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode,
            @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateUi();
    }

    @Override
    protected void setMyLoc(final Location location) {
        this.myLoc = location;
    }

    @Override
    public boolean onCreateOptionsMenu(@NotNull final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_show_location, menu);
        updateUi();
        return true;
    }

    @Override
    protected void updateLocationMarkers() {
        super.updateLocationMarkers();
        if (this.myLoc != null) {
            this.binding.map.getOverlays().add(new MyLocation(this, null, this.myLoc));
        }
        this.binding.map.getOverlays().add(new Marker(this.marker_icon, this.loc));
        new getAddressAsync(this, this.loc, this.name).execute();
    }

    private void showAddress(final GeoPoint loc, final String name) {
        this.binding.address.setText(Html.fromHtml(getAddress(this, loc, name)));
        if (Html.fromHtml(getAddress(this, loc, name)).length() > 0) {
            this.binding.address.setVisibility(View.VISIBLE);
        } else {
            hideAddress();
        }
    }

    private void hideAddress() {
        this.binding.address.setVisibility(View.GONE);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onBackendConnected() {

    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_copy_location:
                final ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    final ClipData clip =
                            ClipData.newPlainText("location", createGeoUri().toString());
                    clipboard.setPrimaryClip(clip);
                    ToastCompat.makeText(this, R.string.url_copied_to_clipboard, ToastCompat.LENGTH_SHORT)
                            .show();
                }
                return true;
            case R.id.action_share_location:
                final Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_TEXT, createGeoUri().toString());
                shareIntent.setType("text/plain");
                try {
                    startActivity(Intent.createChooser(shareIntent, getText(R.string.share_with)));
                } catch (final ActivityNotFoundException e) {
                    //This should happen only on faulty androids because normally chooser is always available
                    ToastCompat.makeText(
                                    this,
                                    R.string.no_application_found_to_open_file,
                                    ToastCompat.LENGTH_SHORT)
                            .show();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startNavigation() {
        final Intent intent = getStartNavigationIntent();
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.no_application_found, Toast.LENGTH_LONG).show();
        }
    }

    private Intent getStartNavigationIntent() {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                        "geo:0,0?q="
                                + this.loc.getLatitude()
                                + ","
                                + this.loc.getLongitude()));
    }

    @Override
    protected void updateUi() {
        final Intent intent = getStartNavigationIntent();
        final ActivityInfo activityInfo = intent.resolveActivityInfo(getPackageManager(), 0);
        //this.binding.fab.setVisibility(activityInfo == null ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onLocationChanged(@NotNull final Location location) {
        if (LocationHelper.isBetterLocation(location, this.myLoc)) {
            this.myLoc = location;
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

    private static String getAddress(final Activity context, final GeoPoint location, final String name) {
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
                    if (name != null && name.length() > 0) {
                        strAddress.append("<b>");
                        strAddress.append(name);
                        strAddress.append(":</b><br>");
                    }
                    if (Address.getAddressLine(0).length() > 0) {
                        strAddress.append(Address.getAddressLine(0));
                    }
                    address = strAddress.toString().replace(", ", "<br>");
                } else {
                    StringBuilder strAddress = new StringBuilder("");
                    if (name != null && name.length() > 0) {
                        strAddress.append("<b>");
                        strAddress.append(name);
                        strAddress.append("</b>");
                    }
                    address = strAddress.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
                StringBuilder strAddress = new StringBuilder("");
                if (name != null && name.length() > 0) {
                    strAddress.append("<b>");
                    strAddress.append(name);
                    strAddress.append("</b>");
                }
                address = strAddress.toString();
            }
        }
        return address;
    }

    private class getAddressAsync extends AsyncTask<Void, Void, Void> {
        String address = null;
        String name = null;
        GeoPoint location;

        private WeakReference<ShowLocationActivity> activityReference;

        getAddressAsync(final ShowLocationActivity context, final GeoPoint location, final String name) {
            activityReference = new WeakReference<>(context);
            this.location = location;
            this.name = name;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            address = getAddress(ShowLocationActivity.this, this.location, this.name);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            showAddress(this.location, this.name);
        }
    }
}
