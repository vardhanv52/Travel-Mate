package io.github.project_travel_mate.destinations.description;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.project_travel_mate.R;
import objects.City;
import utils.TaskLoadedCallback;

public class Directions extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        TaskLoadedCallback, com.google.android.gms.location.LocationListener {
    Context context;
    City city;
    GoogleMap map;
    MapFragment mapFragment;
    MarkerOptions origin, destination;
    Polyline polyline;
    GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directions);
        context = this;
        city = (City) getIntent().getSerializableExtra("city");
        this.setTitle("Directions to " + city.getNickname());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map_fragment);
        destination = new MarkerOptions().position(new LatLng(Double.parseDouble(city.getLatitude()),
                Double.parseDouble(city.getLongitude()))).title(city.getNickname());
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.clear();
        map.addMarker(origin);
        map.addMarker(destination);

        CameraPosition googlePlex = CameraPosition.builder()
                .target(new LatLng(origin.getPosition().latitude, origin.getPosition().longitude))
                .zoom(15)
                .bearing(0)
                .build();

        map.animateCamera(CameraUpdateFactory.newCameraPosition(googlePlex), 5000, null);
    }

    private String getUrl(LatLng origin, LatLng dest) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=driving";
        String parameters = strOrigin + "&" + strDest + "&" + mode;
        String output = "json";
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationUpdates();

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location mLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLocation == null) {
            startLocationUpdates();
        }
        if (mLocation != null) {
            // Qwerty
        } else {
            Toast.makeText(this, "Location not Detected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i("Connection : ", "Suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("Connection : " , "" + connectionResult.getErrorCode());
    }

    @Override
    public void onTaskDone(Object... values) {
        if (polyline != null)
            polyline.remove();
        polyline = map.addPolyline((PolylineOptions) values[0]);
    }

    protected void startLocationUpdates() {
        LocationRequest mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(60000)
                .setFastestInterval(60000);
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        origin = new MarkerOptions().position(new LatLng(location.getLatitude(),
                location.getLongitude())).title("Me");
        new FetchURL(context).execute(getUrl(origin.getPosition(), destination.getPosition()),
                "driving");
        mapFragment.getMapAsync(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    public class DataParser {

        public List<List<HashMap<String, String>>> parse(JSONObject jObject) {

            List<List<HashMap<String, String>>> routes = new ArrayList<>();
            JSONArray jRoutes;
            JSONArray jLegs;
            JSONArray jSteps;
            try {
                jRoutes = jObject.getJSONArray("routes");
                for (int i = 0; i < jRoutes.length(); i++) {
                    jLegs = ((JSONObject) jRoutes.get(i)).getJSONArray("legs");
                    List path = new ArrayList<>();
                    for (int j = 0; j < jLegs.length(); j++) {
                        jSteps = ((JSONObject) jLegs.get(j)).getJSONArray("steps");

                        for (int k = 0; k < jSteps.length(); k++) {
                            String polyline;
                            polyline = (String) ((JSONObject) ((JSONObject) jSteps.get(k))
                                    .get("polyline")).get("points");
                            List<LatLng> list = decodePoly(polyline);

                            for (int l = 0; l < list.size(); l++) {
                                HashMap<String, String> hm = new HashMap<>();
                                hm.put("lat", Double.toString((list.get(l)).latitude));
                                hm.put("lng", Double.toString((list.get(l)).longitude));
                                path.add(hm);
                            }
                        }
                        routes.add(path);
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
            return routes;
        }

    }

    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            }
            while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            }
            while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                        (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

    public class PointsParser extends AsyncTask<String, Integer,
            List<List<HashMap<String, String>>>> {

        TaskLoadedCallback taskCallback;
        String directionMode;

        public PointsParser(Context mContext, String directionMode) {
            this.taskCallback = (TaskLoadedCallback) mContext;
            this.directionMode = directionMode;
        }

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                Log.d("mylog", jsonData[0]);
                DataParser parser = new DataParser();
                Log.d("mylog", parser.toString());

                routes = parser.parse(jObject);
                Log.d("mylog", "Executing routes");
                Log.d("mylog", routes.toString());

            } catch (Exception e) {
                Log.d("mylog", e.toString());
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points;
            PolylineOptions lineOptions = null;
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList<>();
                lineOptions = new PolylineOptions();
                List<HashMap<String, String>> path = result.get(i);
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);
                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);
                    points.add(position);
                }
                lineOptions.addAll(points);
                lineOptions.width(10);
                lineOptions.color(ContextCompat.getColor(context , R.color.colorPrimary));
                Log.d("mylog", "onPostExecute lineoptions decoded");
            }

            if (lineOptions != null) {
                taskCallback.onTaskDone(lineOptions);
            } else {
                Log.d("mylog", "without Polylines drawn");
            }
        }
    }

    public class FetchURL extends AsyncTask<String, Void, String> {

        Context mContext;
        String directionMode = "driving";

        FetchURL(Context mContext) {
            this.mContext = mContext;
        }

        @Override
        protected String doInBackground(String... strings) {
            String data = "";
            directionMode = strings[1];
            try {
                data = downloadUrl(strings[0]);
                Log.d("mylog", "Background task data " + data);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            PointsParser parserTask = new PointsParser(mContext, directionMode);
            parserTask.execute(s);
        }

        private String downloadUrl(String strUrl) throws IOException {
            String data = "";
            InputStream iStream = null;
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(strUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();
                iStream = urlConnection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
                StringBuffer sb = new StringBuffer();
                String line = "";
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                data = sb.toString();
                Log.d("mylog", "Downloaded URL: " + data);
                br.close();
            } catch (Exception e) {
                Log.d("mylog", "Exception downloading URL: " + e.toString());
            } finally {
                iStream.close();
                urlConnection.disconnect();
            }
            return data;
        }
    }

}
