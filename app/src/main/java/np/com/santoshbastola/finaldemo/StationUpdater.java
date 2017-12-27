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
 * Created by Santosh on 6/29/2017.
 */

public class StationUpdater {
    private InfoListener listener;
    private String JSONUrl = "https://api.smrtprj.cf/tt/stations-out.php";

    public StationUpdater(InfoListener listener) {
        this.listener = listener;
    }

    public void execute() throws UnsupportedEncodingException {
        new DownloadRawData().execute(JSONUrl);
    }

    private class DownloadRawData extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                InputStream is = url.openConnection().getInputStream();
                StringBuffer buffer = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                return buffer.toString();

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                parseJSon(res);
            } catch (JSONException e) {
                listener.onGettingErrorMessage("Error : JSON Exception");
                e.printStackTrace();
            }
        }
    }

    private void parseJSon(String data) throws JSONException {
        if (data == null){
            listener.onGettingErrorMessage("Cannot fetch app data. Please check your internet connection and restart the app.");
            return;
        }

        Log.i("Debug", "Json Parsing (StationPoints)....");

        List<StationData> stationList = new ArrayList<>();

        JSONObject parentObject = new JSONObject(data);

        if(!parentObject.getString("status").equals("OK")) {
            listener.onGettingErrorMessage("Corrupted data received");
            return;
        }

        JSONArray namesArray = parentObject.getJSONArray("names");
        JSONArray idsArray = parentObject.getJSONArray("ids");
        JSONArray latsArray = parentObject.getJSONArray("lats");
        JSONArray lngsArray = parentObject.getJSONArray("lngs");

        for(int i=0; i<namesArray.length(); i++) {
            StationData stationData = new StationData();
            stationData.name = namesArray.getString(i);
            stationData.id = idsArray.getInt(i);
            stationData.location = new LatLng(latsArray.getDouble(i), lngsArray.getDouble(i));

            stationList.add(stationData);
        }
        listener.onFinishStationUpdate(stationList);
    }
}
