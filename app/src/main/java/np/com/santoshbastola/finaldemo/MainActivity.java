package np.com.santoshbastola.finaldemo;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, InfoListener {

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private GoogleMap mMap;
    private MyDBHandler dbHandler;
    private LatLng myLocation;
    private ProgressDialog progressDialog;
    private Button btn;
    private AutoCompleteTextView autoCompleteTextView;
    private TextView textViewWalking, textViewDistance, textViewDuration;
    private TextInputLayout inputLayout;

    private String[] stationNames = {};
    private List<StationData> stationDataList;
    private List<Marker> busMarkers = new ArrayList<>();
    private List<Marker> stationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();
    private Marker myLocationMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        dbHandler = new MyDBHandler(this);
        stationDataList = new ArrayList<>();

        textViewDistance = (TextView) findViewById(R.id.textViewDistance);
        textViewDuration = (TextView) findViewById(R.id.textViewDuration);
        textViewWalking = (TextView) findViewById(R.id.textViewWalking);
        btn = (Button) findViewById(R.id.button);
        autoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView);
        inputLayout = (TextInputLayout) findViewById(R.id.input_layout);

        getAllDataFromDatabase();
        buttonClicked();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLocationPermission();
        locationStatusCheck();
        getMyLocation();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);

        // Add a marker in Device current location and move the camera
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(myLocation, 12);
        mMap.moveCamera(update);
//        mMap.setMyLocationEnabled(true);
    }

    private void buttonClicked() {
        btn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getMyLocation();
                        String destination = autoCompleteTextView.getText().toString().trim();
                        int id = checkUserInput(destination);
                        if (destination.isEmpty() || (id == 0)) {
                            inputLayout.setErrorEnabled(true);
                            inputLayout.setError("Please enter a valid station");
                        } else {
                            inputLayout.setErrorEnabled(false);
//                            Toast.makeText(MainActivity.this, "Sending station id " + String.valueOf(id), Toast.LENGTH_SHORT).show();
                            removeEverything();
                            progressDialog = ProgressDialog.show(MainActivity.this, "Please wait...", "Finding Route", true);
                            try {
//                                Toast.makeText(MainActivity.this, "My Location : "+myLocation.latitude+" ,"+myLocation.longitude, Toast.LENGTH_LONG).show();
                                new BusUpdater(MainActivity.this, id, myLocation).execute();
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        );
    }

    private int checkUserInput(String destination) {
        //get the id of the station entered by user
        for (StationData station : stationDataList) {
            if (destination.equals(station.name)) {
                return station.id;
            }
        }
        return 0;
    }

    public void onFinishStationUpdate(List<StationData> stationDataList) {
        progressDialog.dismiss();
        Log.i("Debug", "Station finding success");
        boolean isInserted = false;
        //Add station list to mysql database
        for (StationData station : stationDataList) {
            isInserted = dbHandler.insertData(station.id, station.name, station.location.latitude, station.location.longitude);
        }
        if (isInserted) {
            //if the database is updated successfully the show a toast message
            Toast.makeText(MainActivity.this, "Data Inserted Successfully!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MainActivity.this, "Data not Inserted!", Toast.LENGTH_SHORT).show();
            try {
                new StationUpdater(this).execute();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        getAllDataFromDatabase();
    }

    public void onFinishGettingBus(List<BusData> busDataList) {
        Log.i("Debug", "Setting bus markers");
        for (BusData busData : busDataList) {
            busMarkers.add(mMap.addMarker(new MarkerOptions()
                    .title(busData.name)
                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.bus))
                    .position(busData.location)));
        }
    }

    public void onFinishGettingStationPoints(Integer[] stationIds) {
        progressDialog.dismiss();
        Log.i("Debug", "setting stations marker");
        LatLng[] points = new LatLng[stationIds.length];
        for (int i = 0; i < stationIds.length; i++) {
            for (StationData stationData : stationDataList) {
                if (stationData.id == stationIds[i]) {
                    points[i] = stationData.location;
                    stationMarkers.add(mMap.addMarker(new MarkerOptions()
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.station))
                            .title("Station : " + stationData.name)
                            .position(stationData.location)));
                }
            }
        }
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder()
                        .target(points[points.length / 2])
                        .zoom(13)
                        .build()
        ), 10000, null);
        //Adding waypoints to the url
        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?origin=" + points[0].latitude + "," + points[0].longitude + "&destination=" + points[points.length - 1].latitude + "," + points[points.length - 1].longitude);
        if (points.length > 2) {
            url.append("&waypoints=");
            for (int i = 1; i < points.length - 2; i++)
                url.append("via:" + points[i].latitude + "%2C" + points[i].longitude + "%7C");
            url.append("via:" + points[points.length - 2].latitude + "%2C" + points[points.length - 2].longitude);
        }

        url.append("&key=AIzaSyA0maDUutsfLew_fJdp7lYKzLfhe1S0FTc");
        try {
            new RouteFinder(this, url.toString(), "Driving").execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?origin=" + myLocation.latitude + "," + myLocation.longitude + "&destination=" + points[0].latitude + "," + points[0].longitude + "&mode=walking&key=AIzaSyA0maDUutsfLew_fJdp7lYKzLfhe1S0FTc");
        try {
            new RouteFinder(this, url.toString(), "Walking").execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRouteFinderSuccess(List<RouteData> routeList, String mode) {

        for (RouteData route : routeList) {
            if (mode.equals("Driving")) {
                PolylineOptions polylineOptions = new PolylineOptions().
                        geodesic(true).
                        color(Color.BLUE).
                        width(5);

                polylineOptions.add(route.startLocation);

                for (int i = 0; i < route.points.size(); i++) {
                    polylineOptions.add(route.points.get(i));
                }

                polylineOptions.add(route.endLocation);

                polylinePaths.add(mMap.addPolyline(polylineOptions));

                textViewDuration.setText(new DecimalFormat("##.##").format(route.duration / 60) + " Min");
                textViewDistance.setText(new DecimalFormat("##.##").format(route.distance / 1000) + " KM");
            } else {
                PolylineOptions polylineOptions = new PolylineOptions().
                        geodesic(true).
                        color(Color.RED).
                        width(5);

                polylineOptions.add(route.startLocation);

                for (int i = 0; i < route.points.size(); i++) {
                    polylineOptions.add(route.points.get(i));
                }

                polylineOptions.add(route.endLocation);

                polylinePaths.add(mMap.addPolyline(polylineOptions));

                textViewWalking.setText((int) route.distance + " M");
            }
        }
    }

    public void onGettingErrorMessage(String message) {
        progressDialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(message);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    public void getAllDataFromDatabase() {
        Cursor res = dbHandler.getAllData();
        if (res.getCount() == 0) {
            Log.i("Debug", "database is empty");
            try {
                dbHandler.onUpgrade(dbHandler.db, 1, 2);
                progressDialog = ProgressDialog.show(this, "Please wait", "Fetching data from server", false, true);
                new StationUpdater(this).execute();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        stationNames = new String[res.getCount()];
        int i = 0;
        while (res.moveToNext()) {
            StationData stationData = new StationData();
            stationData.id = Integer.parseInt(res.getString(0));
            stationData.name = res.getString(1);
            stationData.location = new LatLng(Double.parseDouble(res.getString(2)), Double.parseDouble(res.getString(3)));
            stationNames[i] = res.getString(1);
            i++;
            stationDataList.add(stationData);
        }
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stationNames);
        autoCompleteTextView.setAdapter(adapter);
    }

    private void removeEverything() {
        if (busMarkers != null) {
            for (Marker marker : busMarkers) {
                marker.remove();
            }
        }

        if (stationMarkers != null) {
            for (Marker marker : stationMarkers) {
                marker.remove();
            }
        }

        if (polylinePaths != null) {
            for (Polyline polyline : polylinePaths) {
                polyline.remove();
            }
        }
        textViewDuration.setText("0 Min");
        textViewDistance.setText("0 KM");
        textViewWalking.setText("0 M");
    }

    private LatLng getMyLocation() {

        Log.i("Debug", "Getting Location");
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = lm.getProviders(true);
        Location l = null;

        for (int i = providers.size() - 1; i >= 0; i--) {
            l = lm.getLastKnownLocation(providers.get(i));
            if (l != null)
                break;
        }
        if (l != null) {
            myLocation = new LatLng(l.getLatitude(), l.getLongitude());
            if (mMap != null) {
                if (myLocationMarker != null) {
                    myLocationMarker.remove();
                    myLocationMarker = mMap.addMarker(new MarkerOptions().position(myLocation).title("My Location"));
                }
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder()
                                .target(myLocation)
                                .zoom(13)
                                .build()
                ), 10000, null);
            }
        } else
            myLocation = new LatLng(28.254551, 83.976247);

        return myLocation;
//        Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                //show an explanation to the user asynchronously
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_location_permission)
                        .setMessage(R.string.text_location_permission)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        }).create().show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        myLocation = getMyLocation();
                    } else {
                        Toast.makeText(this, "Cannot get current location", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    public void locationStatusCheck() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage(R.string.text_location_enable)
                    .setPositiveButton(R.string.text_alertdialog_goto_location, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton(R.string.text_exit, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            System.exit(0);
                        }
                    }).create().show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, AppPreferences.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_about) {
            Intent intent = new Intent(this, About.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
