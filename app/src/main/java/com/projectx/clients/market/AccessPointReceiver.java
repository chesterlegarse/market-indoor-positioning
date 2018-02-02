package com.projectx.clients.market;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;
import com.projectx.clients.market.models.AccessPoint;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccessPointReceiver extends BroadcastReceiver {

    private static Logger logger = Logger.getLogger(AccessPoint.class.getSimpleName());

    private WifiManager wifiManager;
    private DatabaseHelper databaseHelper;

    public AccessPointReceiver(Context context, DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
        wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        assert wifiManager != null;
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
    }

    void register(Context context) {
        // Register Broadcast Receiver
        context.registerReceiver(this,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
    }

    private List<AccessPoint> filterScanResults(List<ScanResult> scanResults) {
        List<AccessPoint> accessPoints = new ArrayList<>();
        databaseHelper.getReadableDatabase();

        for (ScanResult result : scanResults) {
            if (databaseHelper.isAccessPoint(result.BSSID.toLowerCase())) {

                // Create a new access point and set RSSI and frequency
                AccessPoint accessPoint
                        = databaseHelper.getAccessPoint(result.BSSID.toLowerCase());
                accessPoint.setRSSI(result.level);
                accessPoint.setFrequency(result.frequency);

                // Add created access point to list
                accessPoints.add(accessPoint);
            }
        }

        // Log filtered wifi
        if (accessPoints.size() > 0) {
            logger.log(Level.INFO, "Found the following Access Points: ");
        }

        for (AccessPoint accessPoint : accessPoints) {
            logger.log(Level.INFO, accessPoint.toString());
        }

        Collections.sort(accessPoints);
        return accessPoints;
    }

    private TrilaterationData prepareTrilaterationData(List<AccessPoint> accessPoints) {
        double[][] positions = new double[accessPoints.size()][2];
        double[] distances = new double[accessPoints.size()];

        if (accessPoints.size() >= 2) {

            for (int i = 0; i < positions.length; i++) {
                AccessPoint accessPoint = accessPoints.get(i);
                positions[i][0] = accessPoint.getX();
                positions[i][1] = accessPoint.getY();
                distances[i] = AccessPoint.calculateDistance(
                        accessPoint.getFrequency(), accessPoint.getRSSI()
                );
            }
        }

        return new TrilaterationData(
                positions, distances
        );
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {

            TrilaterationData data = prepareTrilaterationData(
                    filterScanResults(wifiManager.getScanResults())
            );

            if (data.distances.length > 2 && data.positions.length > 2) {
                double[] position = trilaterate(data.positions, data.distances);

                String currentLocationMsg
                        = "Computed location: (" + position[0] + ", " + position[1] + ")";

                logger.log(Level.INFO, currentLocationMsg);
            }
        }
    }

    private double[] trilaterate(double[][] positions, double[] distances) {
        NonLinearLeastSquaresSolver solver =
                new NonLinearLeastSquaresSolver(
                        new TrilaterationFunction(positions, distances),
                        new LevenbergMarquardtOptimizer()
                );
        LeastSquaresOptimizer.Optimum optimum = solver.solve();
        return optimum.getPoint().toArray();
    }

    class TrilaterationData {
        double[][] positions;
        double[] distances;

        TrilaterationData(double[][] positions, double[] distances) {
            this.positions = positions;
            this.distances = distances;
        }
    }
}
