package com.projectx.clients.market;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import com.projectx.clients.market.models.Position;

import java.util.List;
import java.util.Objects;

class PositionLearner {

    private Context context;

    private DatabaseHelper databaseHelper;
    private WifiManager wifiManager;
    private List<ScanResult> scanResults;

    private Position position;


    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (Objects.equals(intent.getAction(),
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                scanResults = wifiManager.getScanResults();
            }

        }
    };

    PositionLearner(Context context, int floor, double x, double y) {

        this.context = context;
        position = new Position(floor, x, y);

        databaseHelper = new DatabaseHelper(context);
        wifiManager = (WifiManager) context.getApplicationContext().
                getSystemService(Context.WIFI_SERVICE);

        context.registerReceiver(receiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    Position getPosition() {
        return position;
    }

    Position refresh() {
        if (wifiManager != null) {
            wifiManager.startScan();
            position = learn();
        }

        return position;
    }

    private Position learn() {

        if (scanResults == null) {
            return position;
        }

        for (ScanResult result : scanResults) {

            String bssid = result.BSSID.toLowerCase();
            int rssi = result.level;

            if (databaseHelper.isAccessPoint(bssid)) {
                position.add(bssid, rssi);
            }
        }

        return position;
    }

    void stop() {
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {}
    }

}
