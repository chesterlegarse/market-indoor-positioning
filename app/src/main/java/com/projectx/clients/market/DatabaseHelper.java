package com.projectx.clients.market;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.projectx.clients.market.models.AccessPoint;
import com.projectx.clients.market.models.Node;
import com.projectx.clients.market.models.Position;
import com.projectx.clients.market.models.Vertex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int TOTAL_FLOORS = 5;
    // Database Info
    private static final String DATABASE_NAME = "MarketPOI";
    private static final int DATABASE_VERSION = 8;
    // Table names
    private static final String TABLE_ACCESS_POINT = "access_points";
    private static final String TABLE_NODES = "nodes";
    private static final String TABLE_WIFI_DATA = "wifi_data";
    private static final String TABLE_POSITION = "positions";
    private static final String TABLE_VERTEX = "vertices";
    private static final String TABLE_NEIGHBOR = "neighbors";
    // Common column names
    private static final String KEY_ID = "_id";
    private static final String FLOOR_LEVEL = "floor_level";
    private static final String X_COORD = "x";
    private static final String Y_COORD = "y";
    // ACCESS POINT columns
    private static final String MAC_ADDRESS = "bssid";
    // NODE columns
    private static final String NODE_NAME = "name";
    // WIFI DATA columns
    private static final String RSSI_VALUE = "rssi_value";
    private static final String POSITION_ID = "position_id";
    // NEIGHBOR column
    private static final String NEIGHBOR_ID = "vertex_id";
    private static final String PARENT_ID = "parent_id";

    private static final String CREATE_TABLE_VERTEX =
            "CREATE TABLE " + TABLE_VERTEX +
                    " (" + KEY_ID + " INTEGER PRIMARY KEY, " +
                    FLOOR_LEVEL + " INTEGER, " +
                    X_COORD  + " DOUBLE, " +
                    Y_COORD + " DOUBLE )";
    private static final String CREATE_TABLE_NEIGHBOR =
            "CREATE TABLE " + TABLE_NEIGHBOR + " (" +
                    KEY_ID + " INTEGER PRIMARY KEY, " +
                    PARENT_ID + "  INTEGER REFERENCES " +
                        TABLE_VERTEX + "(" + KEY_ID + "), " +
                    NEIGHBOR_ID + " INTEGER REFERENCES " +
                        TABLE_VERTEX + "(" + KEY_ID + ")" + ")";
    private static final String CREATE_TABLE_POSITIONS =
            "CREATE TABLE " + TABLE_POSITION +
                    " (" + KEY_ID + " INTEGER PRIMARY KEY, " +
                    FLOOR_LEVEL + " INTEGER, " +
                    X_COORD + " DOUBLE, " +
                    Y_COORD + " DOUBLE " + ")";
    private static final String CREATE_TABLE_WIFI_DATA =
            "CREATE TABLE " + TABLE_WIFI_DATA +
                    " (" + KEY_ID + " INTEGER PRIMARY KEY, " +
                    RSSI_VALUE + " INTEGER, " +
                    MAC_ADDRESS + " CHAR, " +
                    POSITION_ID + " INTEGER REFERENCES " +
                        TABLE_POSITION + "(" + KEY_ID + ")" + ")";
    private static final String CREATE_TABLE_ACCESS_POINT =
            "CREATE TABLE " + TABLE_ACCESS_POINT +
                    " (" + KEY_ID + " INTEGER PRIMARY KEY, " +
                    MAC_ADDRESS + " CHAR UNIQUE, " +
                    X_COORD + " DOUBLE, " +
                    Y_COORD + " DOUBLE, " +
                    FLOOR_LEVEL + " INTEGER )";
    private static final String CREATE_TABLE_NODES =
            "CREATE TABLE " + TABLE_NODES +
                    " (" + KEY_ID + " INTEGER PRIMARY KEY, " +
                    NODE_NAME + " TEXT, " +
                    X_COORD + " DOUBLE, " +
                    Y_COORD + " DOUBLE, " +
                    FLOOR_LEVEL + " INTEGER )";
    private final Context context;
    private final Logger logger = Logger.getLogger(DatabaseHelper.class.getSimpleName());

    DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        logger.log(Level.INFO, "Creating new database with default values.");
        sqLiteDatabase.execSQL(CREATE_TABLE_ACCESS_POINT);
        sqLiteDatabase.execSQL(CREATE_TABLE_NODES);
        sqLiteDatabase.execSQL(CREATE_TABLE_WIFI_DATA);
        sqLiteDatabase.execSQL(CREATE_TABLE_POSITIONS);
        sqLiteDatabase.execSQL(CREATE_TABLE_VERTEX);
        sqLiteDatabase.execSQL(CREATE_TABLE_NEIGHBOR);

        // Load .csv data to SQLite
        for (int i = 0; i < TOTAL_FLOORS; i++) {
            sqLiteDatabase.execSQL(defaultAccessPoints(i));
            sqLiteDatabase.execSQL(defaultNodes(i));
        }

        // TODO include in loop (above) if all floors has vertices
        sqLiteDatabase.execSQL(defaultVertices(0));
        insertNeighborVertices(sqLiteDatabase, 0);

    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        logger.log(Level.INFO, "Updating database, remove non-default values.");
        // On upgrade drop older tables
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCESS_POINT);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NODES);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_WIFI_DATA);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_POSITION);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NEIGHBOR);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_VERTEX);

        // Create database
        onCreate(sqLiteDatabase);
    }

    private String defaultAccessPoints(int floor) {

        StringBuilder query = new StringBuilder(
                "INSERT INTO " + TABLE_ACCESS_POINT +
                "(" + MAC_ADDRESS + ", " +
                X_COORD + ", " + Y_COORD + ", " +
                FLOOR_LEVEL + ") VALUES ");
        BufferedReader reader;

        try {
            reader = new BufferedReader(
                    new InputStreamReader(
                            context.getAssets().
                                    open(floor + "/" + "access_points.csv"),
                            "UTF-8"
                    )
            );

            String line; while ((line = reader.readLine()) != null) {
                query.append("(").append(line.toLowerCase())
                        .append(",").append(floor).append("), ");
            }

            query = new StringBuilder(query.substring(0, query.length() - 2));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return query.toString();
    }

    private String defaultNodes(int floor) {

        StringBuilder query = new StringBuilder(
                "INSERT INTO " + TABLE_NODES +
                        " (" + NODE_NAME + ", " + X_COORD + ", " + Y_COORD + ", "
                        + FLOOR_LEVEL + ") VALUES "
        );

        BufferedReader reader;

        try {
            reader = new BufferedReader(
                    new InputStreamReader(
                            context.getAssets().
                                    open(floor + "/" + "nodes.csv"),
                            "UTF-8"
                    )
            );

            String line; while ((line = reader.readLine()) != null) {
                query.append("(").append(line).append(",")
                        .append(floor).append("), ");
            }

            query = new StringBuilder(query.substring(0, query.length() - 2));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return query.toString();
    }

    @SuppressWarnings("SameParameterValue")
    private String defaultVertices(int floor) {

        // Auto inserts neighbor
        StringBuilder query = new StringBuilder(
                "INSERT INTO " + TABLE_VERTEX +
                        " (" + X_COORD + ", " +
                        Y_COORD + ", " +
                        FLOOR_LEVEL + ") VALUES "
        );

        BufferedReader reader;

        try {
            reader = new BufferedReader(
                    new InputStreamReader(
                            context.getAssets().
                                    open(floor + "/" + "vertex.csv"),
                            "UTF-8"
                    )
            );

            String line; while ((line = reader.readLine()) != null) {
                String val[] = line.split(",");
                query.append("(").append(val[0]).append(",")
                        .append(val[1]).append(",")
                        .append(floor).append("), ");

            }

            query = new StringBuilder(query.substring(0, query.length() - 2));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return query.toString();
    }

    List<Vertex> getVertices(int floor) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Vertex> vertices = new ArrayList<>();

        String selectQuery = "SELECT * " +
                "FROM " + TABLE_VERTEX +
                " WHERE " + FLOOR_LEVEL + " = " + floor;

        Cursor c = db.rawQuery(selectQuery, null);

        while (c.moveToNext()) {
            vertices.add(
                new Vertex(c.getDouble(c.getColumnIndex(X_COORD)),
                    c.getDouble(c.getColumnIndex(Y_COORD)))
            );
        }

        c.close();
        return vertices;
    }

    Vertex getNearestVertex(Node node) {
        Vertex best = null;
        double leastDistance = 100;

        for (Vertex v : getVertices(node.getFloorLevel())) {
            double distance = Math.hypot(node.getX() - v.getX(), node.getY() - v.getY());
            if (distance < leastDistance) {
                leastDistance = distance;
                best = v;
            }
        }

        return best;
    }

    Vertex getNearestVertex(int floor, double x, double y) {
        Vertex best = null;
        double leastDistance = 100;

        for (Vertex v : getVertices(floor)) {
            double distance = Math.hypot(x - v.getX(), y - v.getY());
            if ((v.getX() == x) && (v.getY() == y)) {
                continue;
            }

            if (distance < leastDistance) {
                leastDistance = distance;
                best = v;
            }
        }

        return best;
    }

    @SuppressWarnings("SameParameterValue")
    private void insertNeighborVertices(SQLiteDatabase db, int floor) {
        BufferedReader reader;

        try {
            reader = new BufferedReader(
                    new InputStreamReader(
                            context.getAssets().
                                    open(floor + "/" + "vertex.csv"),
                            "UTF-8"
                    )
            );

            String line; while ((line = reader.readLine()) != null) {
                String val[] = line.split(",");
                int parentId = getVertexId(db, Double.parseDouble(val[0]),
                        Double.parseDouble(val[1]));

                if (parentId > 0) {
                    for (String s : val) {
                        if (s.contains("|")) {
                            String split[] = s.split("\\|");
                            double x = Double.parseDouble(split[0]);
                            double y = Double.parseDouble(split[1]);
                            int neighborVertexId = getVertexId(db, x, y);

                            ContentValues values = new ContentValues();
                            values.put(PARENT_ID, parentId);
                            values.put(NEIGHBOR_ID, neighborVertexId);
                            logger.log(Level.INFO, "Inserted  " + parentId );
                            db.insert(TABLE_NEIGHBOR, null, values);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getVertexId(SQLiteDatabase db, double x, double y) {
        String selectQuery = "SELECT (" + KEY_ID + ")" +
                " FROM " + TABLE_VERTEX +
                " WHERE " + X_COORD + " = " + x +
                " AND " + Y_COORD + " = " +  y;
        Cursor c =  db.rawQuery(selectQuery, null);

        int retVal = c.moveToFirst() ?
                c.getInt(c.getColumnIndex(KEY_ID)) : -1;
        c.close();
        return retVal;
    }

    List<Vertex> getNeighbors(int floor, double x, double y) {
        SQLiteDatabase db = getReadableDatabase();
        String query = " SELECT * FROM " + TABLE_VERTEX +
                " WHERE " + FLOOR_LEVEL + " = " + floor +
                " AND " + KEY_ID + " IN ( " +
                "SELECT (" + NEIGHBOR_ID + " ) FROM " + TABLE_NEIGHBOR +
                " WHERE " + PARENT_ID + " = " + getVertexId(db, x, y) + ")";
        Cursor c = db.rawQuery(query, null);
        List<Vertex> neighbors = new ArrayList<>();

        while (c.moveToFirst()) {

            double tempX = c.getDouble(c.getColumnIndex(X_COORD));
            double tempY = c.getDouble(c.getColumnIndex(Y_COORD));
            neighbors.add(new Vertex(tempX, tempY));
        }

        c.close();

        return neighbors;
    }

    void insertPosition(Position position) {
        SQLiteDatabase db = this.getWritableDatabase();

        Map<String, List<Integer>> values = position.getValues();
        ContentValues positionValues = new ContentValues();
        positionValues.put(FLOOR_LEVEL, position.getFloor());
        positionValues.put(X_COORD, position.getX());
        positionValues.put(Y_COORD, position.getY());

        int positionId = (int) db.insert(TABLE_POSITION,
                null, positionValues);

        logger.log(Level.INFO, "Inserted new position " + positionId);

        for (Map.Entry<String, List<Integer>> e : values.entrySet()) {
            ContentValues wifiDataValues = new ContentValues();

            int total = 0;
            for (int rssi : e.getValue()) {
                total = rssi + total;
            }

            int average = total / e.getValue().size();
            wifiDataValues.put(POSITION_ID, positionId);
            wifiDataValues.put(MAC_ADDRESS, e.getKey());
            wifiDataValues.put(RSSI_VALUE, average);
            int wifiDataId = (int) db.insert(TABLE_WIFI_DATA,
                    null, wifiDataValues);

            logger.log(Level.INFO, "Inserted new wifi_data " + wifiDataId);

        }
    }

    double[] getClosestPosition(Map<String, Integer> values) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Integer> possiblePosition = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : values.entrySet()) {

            String selectQuery = "SELECT * " +
                    "FROM " + TABLE_WIFI_DATA +
                    " WHERE " + RSSI_VALUE + " >= " + (entry.getValue()-5) +
                    " AND " + RSSI_VALUE + " <= " +(entry.getValue()+5) +
                    " AND " + MAC_ADDRESS + " = '" + entry.getKey().toLowerCase() + "'";
            Cursor c = db.rawQuery(selectQuery, null);

            if (c.moveToFirst()) {
                int positionId = (int)
                    c.getLong(c.getColumnIndex(POSITION_ID));
                possiblePosition.add(positionId);
            }

            c.close();
        }

        int mostOccurrences = 0;
        int mostOccurrencesId = 0;
        for (int positionId : possiblePosition) {

            int occurrences = Collections.frequency(possiblePosition, positionId);
            if (occurrences > mostOccurrences) {
                mostOccurrences = occurrences;
                mostOccurrencesId = positionId;
            }
        }

        String selectQuery = "SELECT * " +
            "FROM " + TABLE_POSITION +
            " WHERE " + KEY_ID + " = " + mostOccurrencesId;

        Cursor c = db.rawQuery(selectQuery, null);

        double x = 0;
        double y = 0;
        if (c.moveToFirst()) {
            x = c.getDouble(c.getColumnIndex(X_COORD));
            y = c.getDouble(c.getColumnIndex(Y_COORD));
        }

        c.close();
        return new double[] {x, y};
    }

//    Position getClosestPosition(Map<String, List<Integer>> values) {
//        SQLiteDatabase db = this.getReadableDatabase();
//        int bestPositionId = -1;
//        int bestRssiAverage = -999;
//
//        for (Map.Entry<String, List<Integer>> entry : values.entrySet()) {
//
//            int rssiSum = 0;
//            for (int rssi : entry.getValue()) {
//                rssiSum = rssi + rssiSum;
//            }
//
//            int rssiAverage = rssiSum / entry.getValue().size();
//            if (rssiAverage > bestRssiAverage) {
//                bestRssiAverage = rssiAverage;
//            }
//
//            String selectQuery = "SELECT * " +
//                    "FROM " + TABLE_WIFI_DATA +
//                    " WHERE " + MAC_ADDRESS + " = " + entry.getKey().toLowerCase() +
//                    " AND " + RSSI_VALUE + " = " + bestRssiAverage;
//
//            Cursor c = db.rawQuery(selectQuery, null);
//
//            if (c.moveToFirst()) {
//                int positionId = (int)
//                    c.getLong(c.getColumnIndex(POSITION_ID));
//                bestPositionId = positionId;
//            }
//
//            c.close();
//
//        }
//
//        String selectQuery = "SELECT * " +
//                "FROM " + TABLE_POSITION +
//                " WHERE " +  + " = " + entry.getKey().toLowerCase() +
//
//    }

    List<Node> getNodes(int floorLevel) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Node> nodes = new ArrayList<>();

        String selectQuery = "SELECT * " +
                "FROM " + TABLE_NODES +
                " WHERE " + FLOOR_LEVEL + " = " + floorLevel;

        Cursor c = db.rawQuery(selectQuery, null);

        while (c.moveToNext()) {
            nodes.add(
                    new Node(
                            c.getString(c.getColumnIndex(NODE_NAME)),
                            c.getDouble(c.getColumnIndex(X_COORD)),
                            c.getDouble(c.getColumnIndex(Y_COORD)),
                            floorLevel
                    )
            );
        }

        c.close();
        return nodes;
    }

    List<Vertex> getNodesAsVertices(int floorLevel) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Vertex> vertices = new ArrayList<>();

        String selectQuery = "SELECT * " +
                "FROM " + TABLE_NODES +
                " WHERE " + FLOOR_LEVEL + " = " + floorLevel;

        Cursor c = db.rawQuery(selectQuery, null);

        while (c.moveToNext()) {
            vertices.add(
                    new Vertex(
                            c.getDouble(c.getColumnIndex(X_COORD)),
                            c.getDouble(c.getColumnIndex(Y_COORD))
                    )
            );
        }

        c.close();
        return vertices;
    }

    private List<String> getBSSIDs(int floorLevel) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> BSSIDs = new ArrayList<>();

        String selectQuery = "SELECT (" + MAC_ADDRESS + ") " +
                "FROM " + TABLE_ACCESS_POINT +
                " WHERE " + FLOOR_LEVEL + " = " + floorLevel;

        Cursor c = db.rawQuery(selectQuery, null);

        while (c.moveToNext()) {
            BSSIDs.add(
                    c.getString(c.getColumnIndex(MAC_ADDRESS))
            );
        }

        c.close();
        return  BSSIDs;
    }

    boolean isAccessPoint(String bssid) {
        for (int i=0; i<5; i++) {
            if (getBSSIDs(i).contains(bssid.toLowerCase())) {
                return true;
            }
        }

        return  false;
    }

    AccessPoint getAccessPoint(String macAddress) {
        SQLiteDatabase db = this.getReadableDatabase();

        String selectQuery = "SELECT * FROM " + TABLE_ACCESS_POINT +
                " WHERE " + MAC_ADDRESS + " = '" + macAddress + "'";
        Cursor c = db.rawQuery(selectQuery, null);

        if (c != null) {
            c.moveToFirst();
        } else {
            return null;
        }

        AccessPoint ap = new AccessPoint(
                c.getDouble(c.getColumnIndex(X_COORD)),
                c.getDouble(c.getColumnIndex(Y_COORD)),
                c.getInt(c.getColumnIndex(FLOOR_LEVEL)),
                c.getString(c.getColumnIndex(MAC_ADDRESS))
        );

        c.close();
        return ap;
    }

}
