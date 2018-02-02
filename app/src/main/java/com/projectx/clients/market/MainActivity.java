package com.projectx.clients.market;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.projectx.clients.market.models.Node;
import com.qozix.tileview.TileView;
import com.qozix.tileview.markers.MarkerLayout;

import com.github.clans.fab.FloatingActionButton;
import com.qozix.tileview.paths.CompositePathView.DrawablePath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1001;
    private static Logger logger = Logger.getLogger(MainActivity.class.getSimpleName());

    // Views
    private TileView tileView;
    private ImageView locationView;
    private NodeTapListener nodeTapListener = new NodeTapListener();

    // Database Helper object
    private DatabaseHelper databaseHelper;

    // Collection items / per floor loaded
    private DrawablePath currentShownPath;

    private double[] currentPosition = new double[2];
    private int currentFloor = 0; // TODO pass to TrackPathActivity

    // Wifi tools
    private WifiManager wifiManager;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Map<String, Integer> values = new HashMap<>();
            if (Objects.equals(intent.getAction(), WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {

                for (ScanResult result : wifiManager.getScanResults()) {

                    String bssid = result.BSSID.toLowerCase();
                    int rssi = result.level;
                    values.put(bssid, rssi);
                }
            }

            if (!values.isEmpty()) {
                double[] point = databaseHelper.getClosestPosition(values);

                currentPosition[0] = point[0];
                currentPosition[1] = point[1];
                logger.log(Level.INFO,
                        "Computed location: (" + point[0] + ", " + point[1] + ")");
                tileView.moveMarker(locationView, currentPosition[0], currentPosition[1]);

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FloatingActionButton fab = findViewById(R.id.record_menu_item);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(), TrackPathActivity.class);
                intent.putExtra("currentFloor", currentFloor);
                startActivity(intent);
            }
        });

        FloatingActionButton locationFab = findViewById(R.id.go_to_location_menu_item);
        locationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!(currentPosition[0] == 0 && currentPosition[1] == 0)) {
                    tileView.scrollToAndCenter(currentPosition[0], currentPosition[1]);
                } else {
                    String msg = "No access points detected.";
                    Snackbar.make(tileView, msg, Snackbar.LENGTH_LONG)
                            .setAction("Warning", null).show();
                }
            }
        });

        FloatingActionButton searchFab = findViewById(R.id.search_menu_item);
        searchFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final EditText search = findViewById(R.id.searchView);
                search.setVisibility(View.VISIBLE);
                search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {

                        if (i == EditorInfo.IME_ACTION_SEARCH ||
                                (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                            String name = search.getText().toString();
                            List<Node> nodes = new ArrayList<>();
                            for (int j=0; j<5; j++) nodes.addAll(databaseHelper.getNodes(j));
                            for (Node node : nodes) {
                                if (node.getName().equals(name)) {
                                    runPathfinder(node);
                                }
                            }
                        }
                        return false;
                    }
                });
            }
        });


        databaseHelper = new DatabaseHelper(this);

        initializeTileView(currentFloor);
        initializeNodes(currentFloor);

        // If OS is <= MM, ask permission on run-tim
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            logger.log(Level.INFO, "Detected API is >= 23");
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
            );

            // Check if location is running, required for <= MM
            if (isLocationEnabled()) {
                String msg = "For Android M, location must " +
                        "be enabled to scan for access points.";
                Snackbar.make(tileView, msg, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }

        } else {
            // Get wifiManger service
            initializeWifiManager();
            scheduleScan();
//            accessPointReceiver = new AccessPointReceiver(this, databaseHelper);
//            accessPointReceiver.register(this);
        }

        initializeLocationView();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
//        this.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            logger.log(Level.INFO,
                    "AccessPointReceiver" +
                            " not registered yet.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (receiver != null) {
            registerReceiver(receiver,
                    new IntentFilter(WifiManager.
                            SCAN_RESULTS_AVAILABLE_ACTION)
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            logger.log(Level.INFO, "AccessPointReceiver not registered yet.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            accessPointReceiver = new AccessPointReceiver(this, databaseHelper);
//            accessPointReceiver.register(this);
            // Get wifiManger service
            initializeWifiManager();
            scheduleScan();
        } else {
            finish();
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void scheduleScan() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                wifiManager.startScan();
            }
        };

        Timer timer = new Timer();
        timer.schedule(task, 100);
    }

    private void initializeTileView(int floor) {
        logger.log(Level.INFO, "Initializing TileView");
        tileView = findViewById(R.id.tileView);

        // Set id for newly created TileView
//        tileView.setId(View.generateViewId());

        // Set tileView size (width * height)
        tileView.setSize(1376, 1069);

        // Initialize ground floor data
        tileView.addDetailLevel(1f,
                floor + "/tiles/%d-%d.png", 256, 256);

        // Set zoom out and zoom in limits
        tileView.setScaleLimits(0, 2.5f);

        // Create coordinate system
        tileView.defineBounds(0, 0, 100, 100);

        // Set current view point to center (50, 50)
        tileView.scrollTo(50, 50);

        // Set marker settings
        tileView.setMarkerAnchorPoints(-0.5f, -0.5f);
        tileView.setMarkerTapListener(nodeTapListener);

        // Add tileView to R.id.constraintLayout
//        ConstraintLayout constraintLayout = findViewById(R.id.mainLayout);
//        ConstraintSet constraintSet = new ConstraintSet();
//
//        constraintLayout.addView(tileView, 0);
//        constraintSet.clone(constraintLayout);
//
//        constraintSet.connect(
//                tileView.getId(), ConstraintSet.TOP,
//                constraintLayout.getId(), ConstraintSet.TOP, 0
//        );
//
//        constraintSet.applyTo(constraintLayout);
    }

    private void initializeWifiManager() {
        wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);

        assert wifiManager != null;
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        registerReceiver(receiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
    }

    private void initializeNodes(int floor) {
        List<Node> nodes = databaseHelper.getNodes(floor);
        for (Node node : nodes) {
            if (!node.getName().contains("Elevator") ||
                    node.getName().contains("#Vertex")) {
                mark(node.getX(), node.getY(), node);
            }
        }
    }

    private void mark(double x, double y, Node node) {
        ImageView mapMarkerView = new ImageView(getApplicationContext());
        mapMarkerView.setTag(node);

        if (node.getName().contains("Restroom")) {
            mapMarkerView.setImageResource(R.drawable.ic_wc_24dp);
        } else {
            mapMarkerView.setImageResource(R.drawable.ic_place_16dp);
        }

        tileView.addMarker(
                mapMarkerView, x, y,
                -0.5f, -1f
        );
    }

    private void initializeLocationView() {
        locationView = new ImageView(this);

        locationView.setImageResource(R.drawable.ic_my_location_24dp);

        // Add locationView in origin in tileView
        tileView.addMarker(
                locationView,
                currentPosition[0], currentPosition[1],
                -0.5f, -0.5f);
    }

    public boolean isLocationEnabled() {
        int locationMode;

        try {
            locationMode =
                    Settings.Secure.getInt(
                            getContentResolver(),
                            Settings.Secure.LOCATION_MODE
                    );
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        return locationMode != Settings.Secure.LOCATION_MODE_OFF;
    }

    public void setCurrentShownPath(DrawablePath currentShownPath) {
        this.currentShownPath = currentShownPath;
    }

    private void runPathfinder(Node node) {
        Pathfinder pathfinder = new Pathfinder(this, node.getFloorLevel(), node);
        pathfinder.execute(new Double[] {40d, 57d}, new Double[] {node.getX(), node.getY()});
    }

    private class NodeTapListener implements MarkerLayout.MarkerTapListener {

        @Override
        public void onMarkerTap(View view, int x, int y) {
            Node node = (Node) view.getTag();
            tileView.slideToAndCenter(node.getX(), node.getY());
            tileView.removePath(currentShownPath);
//
//            NodeCallout callout = new NodeCallout(getApplicationContext());
//            callout.setTitle(node.getName());
//            tileView.addCallout(callout, node.getX(), node.getY(), -0.5f, -1f);
        }
    }

}

