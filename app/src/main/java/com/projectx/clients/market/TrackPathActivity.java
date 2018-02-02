package com.projectx.clients.market;

import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.projectx.clients.market.models.Position;
import com.qozix.tileview.TileView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrackPathActivity extends AppCompatActivity {

    private static Logger logger = Logger.getLogger(TrackPathActivity.class.getSimpleName());
    private static int progress = 0;

    private TileView tileView;
    private FloatingActionButton fab;
    private ProgressBar progressBar;
    private List<ImageView> positionViews = new ArrayList<>();
    private List<ImageView> learnedViews = new ArrayList<>();
    private Animation animation = new AlphaAnimation(1, 0);

    private PositionLearner learner;
    private int currentFloor;


    private double[] startPoint = new double[] {0, 0};
    private double[] endPoint = new double[] {0, 0};
    private static final double THRESHOLD = 2.2;
    private static final long SCAN_INTERVAL = 500;
    private static final long SCAN_TOTAL_TIME = 5000;

    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_path);

        databaseHelper = new DatabaseHelper(this);

        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        progressBar.setProgress(progress);

        fab = findViewById(R.id.saveFab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                buildRecordDialog();
            }
        });

        // Create a blinking animation
        animation.setDuration(1000);
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);

        currentFloor = getIntent().
                getIntExtra("currentFloor", 0);
        initializeTileView(currentFloor);

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (learner != null) {
            learner.stop();
        }
    }

    private void initializeTileView(int floor) {
        logger.log(Level.INFO, "Initializing modified TileView");

        tileView = new TileView(this) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {

                double[] point = {
                        tileView.getCoordinateTranslater().
                                translateAndScaleAbsoluteToRelativeX(
                                tileView.getScrollX() + event.getX(),
                                tileView.getScale()),
                        tileView.getCoordinateTranslater().
                                translateAndScaleAbsoluteToRelativeY(
                                tileView.getScrollY() + event.getY(),
                                tileView.getScale())
                };

                // Will be called twice
                createPath(point);
                return super.onSingleTapConfirmed(event);
            }
        };

        // Set id for newly created TileView
        tileView.setId(View.generateViewId());

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

        // Add tileView to R.id.constraintLayout
        ConstraintLayout constraintLayout = findViewById(R.id.trackLayout);
        ConstraintSet constraintSet = new ConstraintSet();

        constraintLayout.addView(tileView, 0);
        constraintSet.clone(constraintLayout);

        constraintSet.connect(
                tileView.getId(), ConstraintSet.TOP,
                constraintLayout.getId(), ConstraintSet.TOP, 0
        );

        constraintSet.applyTo(constraintLayout);
    }

    private void createPath(double[] point) {
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.ic_point_black_24dp);
        positionViews.add(imageView);

        if (isArrayEmpty(startPoint)) {
            System.arraycopy(point, 0,
                    startPoint, 0, point.length);
            tileView.addMarker(imageView,
                startPoint[0], startPoint[1],
                -0.5f, -0.5f);
        } else if (isArrayEmpty(endPoint)) {
            System.arraycopy(point, 0,
                    endPoint, 0, point.length);

            // Clear positionViews
            clearMap();
            positionViews = new ArrayList<>();

            // Smoothing of point
            if (Math.round(startPoint[0]) == Math.round(endPoint[0]) ||
                    Math.round(startPoint[0]) + 2 == Math.round(endPoint[0]) ||
                    Math.round(startPoint[0]) - 2 == Math.round(endPoint[0])) {
                endPoint[0] = startPoint[0];
            }

            if (isArrayEquals(startPoint, endPoint)) {
                String msg = "Cannot create line if points are equal.";
                Snackbar.make(tileView, msg, Snackbar.LENGTH_LONG)
                        .setAction("Error", null).show();
                return;
            }

            // Create ImageViews of Integral Points
            for (double[] p :
                    calculateIntegralPoints(startPoint, endPoint)) {
                ImageView v = new ImageView(this);
                v.setImageResource(R.drawable.ic_point_black_24dp);
                v.setTag(p);

                positionViews.add(v);
                tileView.addMarker(v, p[0], p[1],
                        -0.5f, -0.5f);
            }

        }
    }

    private void learnPosition(ImageView v) {
        double[] point = (double[]) v.getTag();
        v.setImageResource(R.drawable.ic_point_red_24dp);

        // Add blinking animation imageView
        v.startAnimation(animation);

        learner = new PositionLearner(
                this, currentFloor, point[0], point[1]);
        Timer timer = new Timer(learner,
                SCAN_TOTAL_TIME, SCAN_INTERVAL);
        timer.setPositionView(v);
        timer.start();
    }

    private boolean isArrayEmpty(double[] array) {

        for (double val : array) {
            if (val != 0) return false;
        }

        return true;
    }

    private boolean isArrayEquals(double[] a, double[] b) {
        if (a.length != b.length)
            return false;

        for (int i=0;i<a.length;i++)  {
            if (a[i] != b[i])  {
                return false;
            }
        }

        return true;
    }

    private void buildRecordDialog() {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(
                            TrackPathActivity.this,
                            R.style.RecordPathDialog));
        } else {
            builder = new AlertDialog.
                    Builder(getApplicationContext());
        }

        builder.setTitle(R.string.confirm_dialog).
            setMessage(R.string.track_path_dialog).
            setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int i) {

                            for (ImageView v : positionViews) {
                                if (!learnedViews.contains(v)) {
                                    learnPosition(v);
                                    learnedViews.add(v);
                                    return;
                                }
                            }

                        }
                    }).
            setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int i) {
                            clearMap();
                            // Reset lists
                            positionViews = new ArrayList<>();
                            startPoint = new double[2];
                            endPoint = new double[2];
                        }
                    }).
            setIcon(android.R.drawable.ic_dialog_alert).
            show();
    }

    private void clearMap() {
        // Remove all marker
        for (View v : positionViews) {
            tileView.removeMarker(v);
        }
    }

    private static List<double[]> calculateIntegralPoints(double[] a, double[] b) {
        List<double[]> pointsInLine = new ArrayList<>();

        double x1 = a[0], y1 = a[1];
        double x2 = b[0], y2 = b[1];

        if (a[0] > b[0] || a[0] == b[0]) {
            x1 = b[0];
            y1 = b[1];
            x2 = a[0];
            y2 = a[1];
        }

        double height = y2 - y1;
        double width = x2 - x1;
        double slope = width != 0 ? height / width : 0;

        logger.log(Level.INFO, "Computation Results (Integral Points): ");

        logger.log(Level.INFO, "A: (" + a[0] + ", " + a[1] + ")");
        logger.log(Level.INFO, "B: (" + b[0] + ", " + b[1] + ")");

        logger.log(Level.INFO, "width: " + width);
        logger.log(Level.INFO, "height: " + height);
        logger.log(Level.INFO, "slope: " + slope);

        if (width != 0) {

            for (double i = 0; i < width; i = i + THRESHOLD) {
                double x = x1 + i;
                double y = y1 + (slope * i);
                pointsInLine.add(new double[]{x, y});

                logger.log(Level.INFO, "(" + x + ", " + y + ")");
            }

        } else {

            int factor = height < 0 ? -1 : 1;

            for (double i = 0; i < Math.abs(height); i += 2) {
                double y = y1 + i * factor;
                pointsInLine.add(new double[]{x1, y});

                logger.log(Level.INFO, "(" + x1 + ", " + y + ")");
            }
        }

        return pointsInLine;
    }

    class Timer extends CountDownTimer {

        private final long millisInFuture;
        private final long countDownInterval;

        private ImageView v;
        PositionLearner learner;
        Position position;

        Timer(PositionLearner learner, long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
            this.learner = learner;
            logger.log(Level.INFO,
                    "Learning position (" +
                            learner.getPosition().getY() + ", " +
                            learner.getPosition().getX() + ")");

            fab.setVisibility(View.GONE);
            fab.setImageResource(R.drawable.ic_arrow_forward_24dp);
            fab.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {

                    for (ImageView v : positionViews) {
                        if (!learnedViews.contains(v)) {
                            learnPosition(v);
                            learnedViews.add(v);
                            return;
                        }
                    }

                }

            });

            progressBar.setVisibility(View.VISIBLE);
            this.millisInFuture = millisInFuture;
            this.countDownInterval = countDownInterval;
        }

        void setPositionView(ImageView v) {
            this.v = v;
        }

        @Override
        public void onTick(long l) {
            position = learner.refresh();

            // Update progressBar
            progress++;
            progressBar.setProgress((int)
                    (progress * 100 /
                    (millisInFuture / countDownInterval)));
        }

        @Override
        public void onFinish() {
            learner.stop();
            fab.setVisibility(View.VISIBLE);

            for (Map.Entry<String, List<Integer>> e :
                    position.getValues().entrySet()) {

                StringBuilder values = new StringBuilder();
                for (int value : e.getValue()) {
                    values.append(value).append(", ");
                }

                String log = e.getKey() + " " +
                        values.toString().
                        substring(0, values.length() - 2);
                logger.log(Level.INFO, log);
            }

            // Reset progressBar
            progress = 0;
            progressBar.setProgress(progress);
            progressBar.setVisibility(View.GONE);

            // Insert position data to database
            databaseHelper.insertPosition(position);

            // Set v to finished mode
            // (Stop animation and change drawable
            v.clearAnimation();
            v.setImageResource(R.drawable.ic_point_green_24dp);
        }

    }
}