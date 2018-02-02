package com.projectx.clients.market;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;

import com.projectx.clients.market.models.Node;
import com.projectx.clients.market.models.Vertex;
import com.qozix.tileview.TileView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Pathfinder extends AsyncTask<Double[], Void, List<double[]>> {

    private static Logger logger = Logger.getLogger(Pathfinder.class.getSimpleName());

    private int floor = 0;
    private DatabaseHelper db;
    private final WeakReference<MainActivity> activity;
    private List<double[]> path = new ArrayList<>();
    private Node node;


    Pathfinder(MainActivity activity, int floor, Node node) {
        this.floor = floor;
        this.node = node;
        this.activity = new WeakReference<>(activity);
        db = new DatabaseHelper(activity.getApplicationContext());
    }

    @Override
    protected List<double[]> doInBackground(Double[]... doubles) {
        logger.log(Level.INFO, "Computing path");
        Vertex from = db.getNearestVertex(floor, doubles[0][0], doubles[0][1]);
        Vertex to = db.getNearestVertex(floor, doubles[1][0], doubles[1][1]);
        path.add(from.toArray());


        return computePath(floor, from, to);
    }

    private List<double[]> computePath(int floor, Vertex from, Vertex to) {

        Vertex best = from;
        double leastDistanceFrom = 100;
        List<Vertex> vertices = db.getVertices(floor);

        for (Vertex v : vertices) {

            if (best.equals(to)) {
                logger.log(Level.INFO, "true");
                path.add(to.toArray());
                path.add(new double[]{node.getX(), node.getY()});
                return path;
            }

            if ((!path.isEmpty() && pathContains(v))) {
                //(!path.isEmpty() && (v.getX() == path.get(path.size() -1)[0]) &&
                //(v.getY() == path.get(path.size() -1)[1]) )
                continue;
            }

            double distanceFrom = computeDistance(from, v);
            if (distanceFrom < leastDistanceFrom) {
                leastDistanceFrom = distanceFrom;
                best = v;
            }
        }

        path.add(best.toArray());
        logger.log(Level.INFO, "Added (" +  best.getX() + ", " + best.getY()
                + ") " + "Size: " + path.size());


        return computePath(floor, best, to);
    }

    private boolean pathContains(Vertex v) {
        for (double[] i : path) {
            if (i[0] == v.getX() && i[1] == v.getY()) {
                return true;
            }
        }

        return false;
    }

    private static double computeDistance(Vertex a, Vertex b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }

//    private static double computeDistance(double[] a, double[] b) {
//        return Math.hypot(a[0] - b[0], a[1] - b[1]);
//    }

    @Override
    protected void onPostExecute(List<double[]> doubles) {
        super.onPostExecute(doubles);
        logger.log(Level.INFO, "Finished computing path");
        TileView tileView = activity.get().findViewById(R.id.tileView);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(10);
        paint.setAntiAlias(true);

        activity.get().setCurrentShownPath(tileView.drawPath(doubles, paint));
    }
}