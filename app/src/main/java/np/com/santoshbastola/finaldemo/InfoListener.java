package np.com.santoshbastola.finaldemo;

import java.util.List;

/**
 * Created by Santosh on 6/29/2017.
 */

public interface InfoListener {
    void onFinishStationUpdate(List<StationData> stationDataList);
    void onFinishGettingBus(List<BusData> busDataList);
    void onFinishGettingStationPoints(Integer[] stationIds);
    void onRouteFinderSuccess(List<RouteData> routes, String mode);
    void onGettingErrorMessage(String error);
}
