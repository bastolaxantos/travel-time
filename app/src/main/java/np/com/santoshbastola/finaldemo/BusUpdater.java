package np.com.santoshbastola.finaldemo;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Santosh on 7/1/2017.
 */

public class BusUpdater {
    private String TAG = "Debug";
    private static final String JSON_URL = "https://api.smrtprj.cf/tt/routes.php?";
    private InfoListener listener;
    private int destination;
    private LatLng myLocation;

    public BusUpdater(InfoListener listener, int destination, LatLng myLocation) {
        this.listener = listener;
        this.destination = destination;
        this.myLocation = myLocation;
        Log.i(TAG, "Bus Updater called");
    }

    public void execute() throws UnsupportedEncodingException {
        new DownloadRawData().execute(JSON_URL + "lat=" + myLocation.latitude + "&lng=" + myLocation.longitude + "&" + "dest=" + destination);
    }

    private class DownloadRawData extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String link = params[0];
            Log.i(TAG, link);
            try {
                URL url = new URL(link);
                InputStream is = url.openConnection().getInputStream();
                StringBuffer buffer = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                String data;
                while ((data = reader.readLine()) != null) {
                    buffer.append(data + "\n");
                }
                return buffer.toString();

            } catch (IOException m) {
                m.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            try {
                parseJSON(s);
            } catch (JSONException e) {
                e.printStackTrace();
                listener.onGettingErrorMessage("There is some error on server. Please try again.");
            }
        }
    }

    private void parseJSON(String data) throws JSONException {
        if (data == null) {
            listener.onGettingErrorMessage("Cannot connect to server. Please check your internet connection.");
            return;
        }
        JSONObject parentObject = new JSONObject(data);

        if (!parentObject.getString("status").equals("OK")) {
            if (parentObject.getString("status").equals("ERROR")) {
                Log.i(TAG, parentObject.getString("message"));
                listener.onGettingErrorMessage(parentObject.getString("message"));
                return;
            } else {
                listener.onGettingErrorMessage("A corrupted data received");
                return;
            }
        }
        Log.i("Debug", data);
        List<BusData> busDataList = new ArrayList<>();
        Integer[] stationIds;

        JSONArray busNameArray = parentObject.getJSONArray("buses");
        JSONArray busLatArray = parentObject.getJSONArray("lats");
        JSONArray busLngArray = parentObject.getJSONArray("lngs");
        JSONArray stationIdArray = parentObject.getJSONArray("stations");

        for (int i = 0; i < busNameArray.length(); i++) {
            BusData busData = new BusData();
            busData.name = busNameArray.getString(i);
            busData.location = new LatLng(busLatArray.getDouble(i), busLngArray.getDouble(i));
            busDataList.add(busData);
        }

        stationIds = new Integer[stationIdArray.length()];

        for (int i = 0; i < stationIdArray.length(); i++) {
            stationIds[i] = stationIdArray.getInt(i);
        }
        listener.onFinishGettingBus(busDataList);
        listener.onFinishGettingStationPoints(stationIds);
    }
}
