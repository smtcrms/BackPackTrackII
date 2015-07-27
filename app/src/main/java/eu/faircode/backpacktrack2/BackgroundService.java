package eu.faircode.backpacktrack2;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.joda.time.DateTime;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;

public class BackgroundService extends IntentService {
    private static final String TAG = "BPT2.Service";

    // Actions
    public static final String ACTION_ALARM = "Alarm";
    public static final String ACTION_DAILY = "Daily";
    public static final String ACTION_UPDATE_WEATHER = "Weather";
    public static final String ACTION_ACTIVITY = "Activity";
    public static final String ACTION_LOCATION_FINE = "LocationFine";
    public static final String ACTION_LOCATION_COARSE = "LocationCoarse";
    public static final String ACTION_LOCATION_PASSIVE = "LocationPassive";
    public static final String ACTION_LOCATION_TIMEOUT = "LocationTimeout";
    public static final String ACTION_LOCATION_CHECK = "LocationCheck";
    public static final String ACTION_STATE_CHANGED = "StateChanged";
    public static final String ACTION_STOP_LOCATING = "StopLocating";
    public static final String ACTION_TRACKPOINT = "TrackPoint";
    public static final String ACTION_WAYPOINT = "WayPoint";
    public static final String ACTION_GEOPOINT = "Geopoint";
    public static final String ACTION_SHARE_GPX = "ShareGPX";
    public static final String ACTION_SHARE_KML = "ShareKML";
    public static final String ACTION_UPLOAD_GPX = "UploadGPX";

    public static final String EXPORTED_ACTION_TRACKING = "eu.faircode.backpacktrack2.TRACKING";
    public static final String EXPORTED_ACTION_TRACKPOINT = "eu.faircode.backpacktrack2.TRACKPOINT";
    public static final String EXPORTED_ACTION_WAYPOINT = "eu.faircode.backpacktrack2.WAYPOINT";
    public static final String EXPORTED_ACTION_WRITE_GPX = "eu.faircode.backpacktrack2.WRITE_GPX";
    public static final String EXPORTED_ACTION_WRITE_KML = "eu.faircode.backpacktrack2.WRITE_KML";
    public static final String EXPORTED_ACTION_UPLOAD_GPX = "eu.faircode.backpacktrack2.UPLOAD_GPX";
    public static final String EXPORTED_ACTION_GET_ALTITUDE = "eu.faircode.backpacktrack2.GET_ALTITUDE";
    public static final String EXPORTED_ACTION_UPDATE_WEATHER = "eu.faircode.backpacktrack2.UPDATE_WEATHER";

    // Extras
    public static final String EXTRA_ENABLE = "Enable";
    public static final String EXTRA_TRACK_NAME = "TrackName";
    public static final String EXTRA_WRITE_EXTENSIONS = "WriteExtensions";
    public static final String EXTRA_DELETE_DATA = "DeleteData";
    public static final String EXTRA_TIME_FROM = "TimeFrom";
    public static final String EXTRA_TIME_TO = "TimeTo";
    public static final String EXTRA_GEOURI = "Geopoint";

    public static final String DEFAULT_TRACK_NAME = "BackPackTrack";

    // Constants
    private static final int STATE_IDLE = 1;
    private static final int STATE_ACQUIRING = 2;
    private static final int STATE_ACQUIRED = 3;

    private static final int LOCATION_TRACKPOINT = 1;
    private static final int LOCATION_WAYPOINT = 2;
    private static final int LOCATION_PERIODIC = 3;

    private static final int VIBRATE_SHORT = 250; // milliseconds
    private static final int VIBRATE_LONG = 500; // milliseconds

    private static final int ALARM_DUE_TIME = 5 * 1000; // milliseconds

    private static final int NOTIFICATION_LOCATION = 0;
    private static final int NOTIFICATION_WEATHER = 1;
    private static int mEGM96Pointer = -1;
    private static int mEGM96Offset;

    public BackgroundService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            wakeLock.acquire();

            Log.i(TAG, "Intent=" + intent);

            if (EXPORTED_ACTION_TRACKING.equals(intent.getAction()))
                handleTrackingEnable(intent);

            else if (ACTION_ACTIVITY.equals(intent.getAction()))
                handleActivity(intent);

            else if (ACTION_TRACKPOINT.equals(intent.getAction()) ||
                    ACTION_WAYPOINT.equals(intent.getAction()) ||
                    ACTION_ALARM.equals(intent.getAction()))
                handleLocationRequest(intent);

            else if (EXPORTED_ACTION_TRACKPOINT.equals(intent.getAction())) {
                intent.setAction(ACTION_TRACKPOINT);
                handleLocationRequest(intent);

            } else if (EXPORTED_ACTION_WAYPOINT.equals(intent.getAction())) {
                intent.setAction(ACTION_WAYPOINT);
                handleLocationRequest(intent);

            } else if (ACTION_LOCATION_FINE.equals(intent.getAction()) ||
                    ACTION_LOCATION_COARSE.equals(intent.getAction()))
                handleLocationUpdate(intent);

            else if (ACTION_LOCATION_PASSIVE.equals(intent.getAction()))
                handlePassiveLocationUpdate(intent);

            else if (ACTION_LOCATION_CHECK.equals(intent.getAction()))
                handleSatelliteCheck(intent);

            else if (ACTION_STATE_CHANGED.equals(intent.getAction()))
                handleStateChanged(intent);

            else if (ACTION_LOCATION_TIMEOUT.equals(intent.getAction()))
                handleLocationTimeout(intent);

            else if (ACTION_STOP_LOCATING.equals(intent.getAction()))
                handleStop(intent);

            else if (ACTION_GEOPOINT.equals(intent.getAction()))
                handleGeopoint(intent);

            else if (ACTION_SHARE_GPX.equals(intent.getAction()))
                handleShare(intent);

            else if (ACTION_SHARE_KML.equals(intent.getAction()))
                handleShare(intent);

            else if (ACTION_UPLOAD_GPX.equals(intent.getAction()))
                handleUploadGPX(intent);

            else if (EXPORTED_ACTION_WRITE_GPX.equals(intent.getAction())) {
                convertTime(intent);
                handleShare(intent);

            } else if (EXPORTED_ACTION_WRITE_KML.equals(intent.getAction())) {
                convertTime(intent);
                handleShare(intent);

            } else if (EXPORTED_ACTION_UPLOAD_GPX.equals(intent.getAction())) {
                convertTime(intent);
                handleUploadGPX(intent);

            } else if (EXPORTED_ACTION_GET_ALTITUDE.equals(intent.getAction())) {
                convertTime(intent);
                handleGetAltitude(intent);

            } else if (ACTION_DAILY.equals(intent.getAction()))
                handleDaily(intent);

            else if (ACTION_UPDATE_WEATHER.equals(intent.getAction()) ||
                    EXPORTED_ACTION_UPDATE_WEATHER.equals(intent.getAction()))
                handleWeatherUpdate(intent);

            else
                Log.i(TAG, "Unknown action");
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            Util.toast(ex.toString(), Toast.LENGTH_LONG, this);
        } finally {
            if (wakeLock != null)
                wakeLock.release();
        }
    }

    // Handle intents methods

    private void handleTrackingEnable(Intent intent) {
        boolean enable = intent.getBooleanExtra(EXTRA_ENABLE, true);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(SettingsFragment.PREF_ENABLED, enable).apply();
        if (enable)
            startTracking(this);
        else
            stopTracking(this);
    }

    private void handleActivity(Intent intent) {
        // Get preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int pref_confidence = Integer.parseInt(prefs.getString(SettingsFragment.PREF_RECOGNITION_CONFIDENCE, SettingsFragment.DEFAULT_RECOGNITION_CONFIDENCE));
        boolean pref_tilting = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_TILTING, SettingsFragment.DEFAULT_RECOGNITION_TILTING);
        boolean pref_known = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_KNOWN, SettingsFragment.DEFAULT_RECOGNITION_KNOWN);
        boolean pref_unknown = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_UNKNOWN, SettingsFragment.DEFAULT_RECOGNITION_UNKNOWN);

        // Get last activity
        int lastActivity = prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.STILL);
        long lastTime = prefs.getLong(SettingsFragment.PREF_LAST_ACTIVITY_TIME, -1);
        boolean lastStill = (lastActivity == DetectedActivity.STILL);

        // Get detected activity
        ActivityRecognitionResult activityResult = ActivityRecognitionResult.extractResult(intent);
        List<DetectedActivity> listProbable = activityResult.getProbableActivities();

        // Persist probably activities
        if (prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_HISTORY, SettingsFragment.DEFAULT_RECOGNITION_HISTORY)) {
            DatabaseHelper dh = null;
            try {
                dh = new DatabaseHelper(this);
                long time = new Date().getTime();
                for (DetectedActivity act : listProbable)
                    dh.insertActivityType(time, act.getType(), act.getConfidence());
            } finally {
                if (dh != null)
                    dh.close();
            }
        }

        DetectedActivity activity = listProbable.get(0);

        // Filter tilting activity
        if (pref_tilting && activity.getType() == DetectedActivity.TILTING) {
            boolean found = false;
            for (DetectedActivity act : listProbable)
                if (act.getType() != DetectedActivity.TILTING) {
                    Log.i(TAG, "Replacing " + activity + " by " + act);
                    listProbable.remove(activity);
                    activity = act;
                    found = true;
                    break;
                }
            if (!found) {
                Log.i(TAG, "Filtering " + activity);
                return;
            }
        }

        // Replace unknown activity
        if (pref_known && activity.getType() == DetectedActivity.UNKNOWN)
            for (DetectedActivity act : listProbable)
                if (act.getType() != DetectedActivity.UNKNOWN && act.getConfidence() > pref_confidence) {
                    Log.i(TAG, "Replacing " + activity + " by " + act);
                    listProbable.remove(activity);
                    activity = act;
                    break;
                }

        // Get walking or running
        if (activity.getType() == DetectedActivity.ON_FOOT)
            for (DetectedActivity act : activityResult.getProbableActivities())
                if (act.getType() == DetectedActivity.WALKING || act.getType() == DetectedActivity.RUNNING) {
                    Log.i(TAG, "Replacing " + activity + " by " + act);
                    listProbable.remove(activity);
                    activity = act;
                    break;
                }

        // Filter unknown activity
        if (pref_unknown && activity.getType() == DetectedActivity.UNKNOWN) {
            Log.i(TAG, "Filtering " + activity);
            return;
        }

        long time = new Date().getTime();
        DateFormat DF = SimpleDateFormat.getDateTimeInstance();
        Log.i(TAG, "Activity=" + activity + " @" + DF.format(time) +
                " last=" + getActivityName(lastActivity, this) + " @" + DF.format(lastTime));

        // Check confidence
        if (activity.getConfidence() > pref_confidence) {
            // Persist probable activity
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(SettingsFragment.PREF_LAST_ACTIVITY, activity.getType());
            editor.putInt(SettingsFragment.PREF_LAST_CONFIDENCE, activity.getConfidence());
            editor.putLong(SettingsFragment.PREF_LAST_ACTIVITY_TIME, time);
            editor.apply();

            // Update activity duration
            if (lastTime >= 0)
                new DatabaseHelper(this).updateActivity(lastTime, lastActivity, time - lastTime).close();

            // Debug
            if (Util.debugMode(this) && lastActivity != activity.getType())
                Util.toast(getActivityName(activity.getType(), this), Toast.LENGTH_SHORT, this);

            // Feedback
            updateState(this, "new activity");

            // Get parameters
            int act = activity.getType();
            boolean still = (act == DetectedActivity.STILL);
            boolean onfoot = (act == DetectedActivity.ON_FOOT || act == DetectedActivity.WALKING || act == DetectedActivity.RUNNING);
            boolean pref_recognize_steps = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_STEPS, SettingsFragment.DEFAULT_RECOGNITION_STEPS);
            boolean pref_unknown_steps = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_UNKNOWN_STEPS, SettingsFragment.DEFAULT_RECOGNITION_UNKNOWN_STEPS);
            if (pref_unknown_steps && act == DetectedActivity.UNKNOWN)
                onfoot = true;

            // Stop/start repeating alarm
            if (lastStill != still) {
                // Restart activity recognition if needed
                int intervalStill = Integer.parseInt(prefs.getString(SettingsFragment.PREF_RECOGNITION_INTERVAL_STILL, SettingsFragment.DEFAULT_RECOGNITION_INTERVAL_STILL));
                int intervalMoving = Integer.parseInt(prefs.getString(SettingsFragment.PREF_RECOGNITION_INTERVAL_MOVING, SettingsFragment.DEFAULT_RECOGNITION_INTERVAL_MOVING));
                if (intervalStill != intervalMoving) {
                    stopActivityRecognition(this);
                    startActivityRecognition(this);
                }

                // Stop/start locating
                if (still) {
                    stopPeriodicLocating(this);
                    stopLocating(this);
                } else
                    startPeriodicLocating(this);
            }

            // Start/stop step counter service
            if (pref_recognize_steps)
                if (onfoot) // Keep alive
                    startService(new Intent(this, StepCounterService.class));
                else
                    stopService(new Intent(this, StepCounterService.class));
        }
    }

    private void handleLocationRequest(Intent intent) {
        // Guarantee fresh location
        if (ACTION_TRACKPOINT.equals(intent.getAction()) || ACTION_WAYPOINT.equals((intent.getAction())))
            stopLocating(this);

        // Persist location type
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (ACTION_TRACKPOINT.equals(intent.getAction()))
            prefs.edit().putInt(SettingsFragment.PREF_LOCATION_TYPE, LOCATION_TRACKPOINT).apply();
        else if (ACTION_WAYPOINT.equals((intent.getAction())))
            prefs.edit().putInt(SettingsFragment.PREF_LOCATION_TYPE, LOCATION_WAYPOINT).apply();
        else if (ACTION_ALARM.equals(intent.getAction()))
            prefs.edit().putInt(SettingsFragment.PREF_LOCATION_TYPE, LOCATION_PERIODIC).apply();

        // Try to acquire a new location
        startLocating(this);
    }

    private void handleLocationUpdate(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Process location update
        int locationType = prefs.getInt(SettingsFragment.PREF_LOCATION_TYPE, -1);
        Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
        Log.i(TAG, "Update location=" + location + " type=" + locationType);
        if (location == null || (location.getLatitude() == 0.0 && location.getLongitude() == 0.0))
            return;

        // Check if still acquiring location
        if (prefs.getInt(SettingsFragment.PREF_STATE, STATE_IDLE) == STATE_IDLE) {
            Log.i(TAG, "Not acquiring anymore");
            return;
        }

        // Filter inaccurate location
        int pref_inaccurate = Integer.parseInt(prefs.getString(SettingsFragment.PREF_INACCURATE, SettingsFragment.DEFAULT_INACCURATE));
        if (!location.hasAccuracy() || location.getAccuracy() > pref_inaccurate) {
            Log.i(TAG, "Filtering inaccurate location=" + location);
            return;
        }

        // Filter old locations
        Location lastLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_LAST_LOCATION, null));
        if (lastLocation != null && location.getTime() <= lastLocation.getTime()) {
            Log.i(TAG, "Location is older than last location, location=" + location);
            return;
        }

        // Correct altitude
        correctAltitude(location, this);

        // Persist better location
        Location bestLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_BEST_LOCATION, null));
        if (isBetterLocation(bestLocation, location)) {
            Log.i(TAG, "Better location=" + location);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(SettingsFragment.PREF_STATE, STATE_ACQUIRED);
            editor.putString(SettingsFragment.PREF_BEST_LOCATION, LocationSerializer.serialize(location));
            editor.apply();
            updateState(this, "better location");
        }

        // Check altitude
        boolean pref_altitude = prefs.getBoolean(SettingsFragment.PREF_ALTITUDE, SettingsFragment.DEFAULT_ALTITUDE);
        if (!location.hasAltitude() && pref_altitude) {
            Log.i(TAG, "No altitude, but preferred, location=" + location);
            return;
        }

        // Check accuracy
        int pref_accuracy;
        if (locationType == LOCATION_WAYPOINT)
            pref_accuracy = Integer.parseInt(prefs.getString(SettingsFragment.PREF_WP_ACCURACY, SettingsFragment.DEFAULT_WP_ACCURACY));
        else
            pref_accuracy = Integer.parseInt(prefs.getString(SettingsFragment.PREF_TP_ACCURACY, SettingsFragment.DEFAULT_TP_ACCURACY));
        if (!location.hasAccuracy() || location.getAccuracy() > pref_accuracy) {
            Log.i(TAG, "Accuracy not reached, location=" + location);
            return;
        }

        // Check pressure
        boolean pressure_enabled = prefs.getBoolean(SettingsFragment.PREF_PRESSURE_ENABLED, SettingsFragment.DEFAULT_PRESSURE_ENABLED);
        if (pressure_enabled)
            if (prefs.getFloat(SettingsFragment.PREF_PRESSURE_VALUE, -1) < 0) {
                Log.i(TAG, "Pressure not available yet");
                return;
            }

        stopLocating(this);

        // Process location
        handleLocation(locationType, location);
    }

    private void handlePassiveLocationUpdate(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Process passive location update
        Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
        Log.i(TAG, "Update passive location=" + location);
        if (location == null || (location.getLatitude() == 0.0 && location.getLongitude() == 0.0))
            return;

        // Filter inaccurate passive locations
        int pref_inaccurate = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PASSIVE_INACCURATE, SettingsFragment.DEFAULT_PASSIVE_INACCURATE));
        if (!location.hasAccuracy() || location.getAccuracy() > pref_inaccurate) {
            Log.i(TAG, "Filtering inaccurate passive location=" + location);
            return;
        }

        // Get last location
        Location lastLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_LAST_LOCATION, null));
        if (lastLocation == null) {
            Log.i(TAG, "Passive location without last location, location=" + location);
            return;
        }

        // Filter old locations
        if (location.getTime() <= lastLocation.getTime()) {
            Log.i(TAG, "Passive location is older than last location, location=" + location);
            return;
        }

        // Correct altitude
        correctAltitude(location, this);

        // Filter nearby passive locations
        int pref_nearby = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PASSIVE_NEARBY, SettingsFragment.DEFAULT_PASSIVE_NEARBY));
        if (lastLocation.distanceTo(location) < pref_nearby &&
                (lastLocation.hasAccuracy() ? lastLocation.getAccuracy() : Float.MAX_VALUE) <=
                        (location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE)) {
            Log.i(TAG, "Filtering nearby passive location=" + location);
            return;
        }

        float bchange = 0;
        double achange = 0;
        boolean update = false;

        // Handle bearing change
        if (location.hasBearing()) {
            int pref_bearing_change = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PASSIVE_BEARING, SettingsFragment.DEFAULT_PASSIVE_BEARING));
            bchange = Math.abs(lastLocation.getBearing() - location.getBearing());
            if (bchange > 180)
                bchange = 360 - bchange;
            if (!lastLocation.hasBearing() || bchange > pref_bearing_change) {
                Log.i(TAG, "Bearing changed to " + location.getBearing());
                update = true;
            }
        }

        // Handle altitude change
        if (location.hasAltitude()) {
            int pref_altitude_change = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PASSIVE_ALTITUDE, SettingsFragment.DEFAULT_PASSIVE_ALTITUDE));
            achange = Math.abs(lastLocation.getAltitude() - location.getAltitude());
            if (!lastLocation.hasAltitude() || achange > pref_altitude_change) {
                Log.i(TAG, "Altitude changed to " + location.getAltitude());
                update = true;
            }
        }

        if (update) {
            // Persist new location
            prefs.edit().putString(SettingsFragment.PREF_LAST_LOCATION, LocationSerializer.serialize(location)).apply();
            DatabaseHelper dh = null;
            try {
                dh = new DatabaseHelper(this);
                int activity_type = prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.UNKNOWN);
                int activity_confidence = prefs.getInt(SettingsFragment.PREF_LAST_CONFIDENCE, -1);
                int stepcount = dh.getSteps(location.getTime());
                dh.insertLocation(location, null, activity_type, activity_confidence, stepcount).close();
            } finally {
                if (dh != null)
                    dh.close();
            }

            // Feedback
            updateState(this, "passive location");
            if (Util.debugMode(this))
                Util.toast(getString(R.string.title_trackpoint) +
                        " " + getProviderName(location, this) +
                        " " + Math.round(bchange) +
                        "° / " + Math.round(achange) + "m", Toast.LENGTH_SHORT, this);
        }
    }

    private void handleSatelliteCheck(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int fixed = prefs.getInt(SettingsFragment.PREF_SATS_FIXED, 0);
        int visible = prefs.getInt(SettingsFragment.PREF_SATS_VISIBLE, 0);
        int checksat = Integer.parseInt(prefs.getString(SettingsFragment.PREF_CHECK_SAT, SettingsFragment.DEFAULT_CHECK_SAT));
        Log.i(TAG, "Check satellites fixed/visible=" + fixed + "/" + visible + " required=" + checksat);

        // Check if there is any chance for a GPS fix
        if (fixed < checksat) {
            // Cancel fine location updates
            Intent locationIntent = new Intent(this, BackgroundService.class);
            locationIntent.setAction(BackgroundService.ACTION_LOCATION_FINE);
            PendingIntent pi = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            lm.removeUpdates(pi);
            stopService(new Intent(this, GpsStatusService.class));
            Log.i(TAG, "Canceled fine location updates");
        }
    }

    private void handleStateChanged(Intent intent) {
        updateState(this, "state changed");
    }

    private void handleLocationTimeout(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Process location time-out
        int locationType = prefs.getInt(SettingsFragment.PREF_LOCATION_TYPE, -1);
        Location bestLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_BEST_LOCATION, null));
        Log.i(TAG, "Timeout best location=" + bestLocation + " type=" + locationType);

        stopLocating(this);

        // Process location
        if (bestLocation != null)
            handleLocation(locationType, bestLocation);
    }

    private void handleStop(Intent intent) {
        stopLocating(this);
    }

    private void handleGeopoint(Intent intent) {
        // geo:latitude,longitude?q=latitude,longitude(label)
        Uri data = (Uri) intent.getExtras().get(EXTRA_GEOURI);
        Log.i(TAG, "Received geopoint q=" + data.toString());

        double lat = 0;
        double lon = 0;
        String name = null;
        String[] query = data.getSchemeSpecificPart().split("\\?");

        // Prefer query part
        if (query.length > 1) {
            String[] q = query[1].split("=");
            if (q.length > 1 && "q".equals(q[0])) {
                int p = q[1].indexOf('(');
                if (p >= 0) {
                    name = q[1].substring(p + 1, q[1].length() - 1);
                    q[1] = q[1].substring(0, p);
                }
                String[] loc = q[1].split(",");
                if (loc.length == 2) {
                    lat = Double.parseDouble(loc[0]);
                    lon = Double.parseDouble(loc[1]);
                }
            }
        }

        // Fallback to scheme part
        if (lat == 0 && lon == 0 && query.length > 0) {
            String[] loc = query[0].split(",");
            if (loc.length == 2) {
                lat = Double.parseDouble(loc[0]);
                lon = Double.parseDouble(loc[1]);
            }
        }

        if (lat != 0 || lon != 0) {
            Location location = new Location("shared");
            location.setTime(System.currentTimeMillis());
            location.setLatitude(lat);
            location.setLongitude(lon);
            if (name == null)
                name = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM).format(new Date());
            new DatabaseHelper(this).insertLocation(location, name, -1, -1, -1).close();
            Util.toast(getString(R.string.msg_added, name), Toast.LENGTH_LONG, this);
        }
    }

    private void handleShare(Intent intent) throws IOException {
        // Write file
        String trackName = intent.getStringExtra(EXTRA_TRACK_NAME);
        boolean extensions = intent.getBooleanExtra(EXTRA_WRITE_EXTENSIONS, false);
        boolean delete = intent.getBooleanExtra(EXTRA_DELETE_DATA, false);
        long from = intent.getLongExtra(EXTRA_TIME_FROM, 0);
        long to = intent.getLongExtra(EXTRA_TIME_TO, Long.MAX_VALUE);
        boolean gpx = ACTION_SHARE_GPX.equals(intent.getAction()) || EXPORTED_ACTION_WRITE_GPX.equals(intent.getAction());
        String fileName = writeFile(gpx, trackName, extensions, from, to, this);

        // Persist last share time
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (gpx)
            prefs.edit().putLong(SettingsFragment.PREF_LAST_SHARE_GPX, new Date().getTime()).apply();
        else
            prefs.edit().putLong(SettingsFragment.PREF_LAST_SHARE_KML, new Date().getTime()).apply();

        // Delete data on request
        if (delete)
            new DatabaseHelper(this).deleteLocations(from, to).close();

        // View file
        if (ACTION_SHARE_GPX.equals(intent.getAction()) || ACTION_SHARE_KML.equals(intent.getAction())) {
            Intent viewIntent = new Intent();
            viewIntent.setAction(Intent.ACTION_VIEW);
            if (gpx)
                viewIntent.setDataAndType(Uri.fromFile(new File(fileName)), "application/gpx+xml");
            else
                viewIntent.setDataAndType(Uri.fromFile(new File(fileName)), "application/vnd.google-earth.kml+xml");
            viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(viewIntent);
        }
    }

    private void handleUploadGPX(Intent intent) throws IOException, XMLRPCException {
        // Write GPX file
        String trackName = intent.getStringExtra(EXTRA_TRACK_NAME);
        boolean extensions = intent.getBooleanExtra(EXTRA_WRITE_EXTENSIONS, false);
        boolean delete = intent.getBooleanExtra(EXTRA_DELETE_DATA, false);
        long from = intent.getLongExtra(EXTRA_TIME_FROM, 0);
        long to = intent.getLongExtra(EXTRA_TIME_TO, Long.MAX_VALUE);
        String gpxFileName = writeFile(true, trackName, extensions, from, to, this);

        // Get GPX file content
        File gpx = new File(gpxFileName);
        byte[] bytes = new byte[(int) gpx.length()];
        DataInputStream in = new DataInputStream(new FileInputStream(gpx));
        in.readFully(bytes);
        in.close();

        // Get parameters
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String blogUrl = prefs.getString(SettingsFragment.PREF_BLOGURL, "");
        int blogId = Integer.parseInt(prefs.getString(SettingsFragment.PREF_BLOGID, "1"));
        String userName = prefs.getString(SettingsFragment.PREF_BLOGUSER, "");
        String passWord = prefs.getString(SettingsFragment.PREF_BLOGPWD, "");

        // Create XML-RPC client
        XMLRPCClient client = new XMLRPCClient(new URL(blogUrl + "xmlrpc.php"));

        // Create upload parameters
        Map<String, Object> args = new HashMap<>();
        args.put("name", trackName + ".gpx");
        args.put("type", "text/xml");
        args.put("bits", bytes);
        args.put("overwrite", true);
        Object[] params = {blogId, userName, passWord, args};

        // Call
        HashMap<Object, Object> result = (HashMap<Object, Object>) client.call("bpt.upload", params);
        String url = result.get("url").toString();
        Log.i(TAG, "Uploaded url=" + url);

        // Persist last upload time
        prefs.edit().putLong(SettingsFragment.PREF_LAST_UPLOAD_GPX, new Date().getTime()).apply();

        // Delete data on request
        if (delete)
            new DatabaseHelper(this).deleteLocations(from, to).close();

        // Feedback
        if (ACTION_UPLOAD_GPX.equals(intent.getAction())) {
            Util.toast(getString(R.string.msg_uploaded, url), Toast.LENGTH_LONG, this);
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(500);
        }
    }

    private void handleGetAltitude(Intent intent) {
        Calendar cfrom = Calendar.getInstance();
        cfrom.add(Calendar.DAY_OF_YEAR, -1); // yesterday
        cfrom.set(Calendar.HOUR_OF_DAY, 0);
        cfrom.set(Calendar.MINUTE, 0);
        cfrom.set(Calendar.SECOND, 0);
        cfrom.set(Calendar.MILLISECOND, 0);

        Calendar cto = Calendar.getInstance();
        cto.add(Calendar.DAY_OF_YEAR, -1); // yesterday
        cto.set(Calendar.HOUR_OF_DAY, 23);
        cto.set(Calendar.MINUTE, 59);
        cto.set(Calendar.SECOND, 59);
        cto.set(Calendar.MILLISECOND, 999);

        long from = intent.getLongExtra(EXTRA_TIME_FROM, cfrom.getTimeInMillis());
        long to = intent.getLongExtra(EXTRA_TIME_TO, cto.getTimeInMillis());

        getAltitude(from, to, this);
    }

    private void handleDaily(Intent intent) {
        try {
            long time = new Date().getTime() / (5 * 60 * 1000) * (5 * 60 * 1000);
            Log.i(TAG, "Daily task at " + SimpleDateFormat.getDateTimeInstance().format(time));

            // Reset step counter
            new DatabaseHelper(this).updateSteps(time, 0).close();

            // Finalize last activity
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int lastActivity = prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.STILL);
            long lastTime = prefs.getLong(SettingsFragment.PREF_LAST_ACTIVITY_TIME, -1);
            if (lastTime >= 0) {
                new DatabaseHelper(this).updateActivity(lastTime, lastActivity, time - lastTime).close();
                prefs.edit().putLong(SettingsFragment.PREF_LAST_ACTIVITY_TIME, time).apply();
                new DatabaseHelper(this).updateActivity(time, lastActivity, 0).close();
            }

            // Optimize database
            new DatabaseHelper(this).vacuum().close();
        } finally {
            startDaily(this);
            updateState(this, "daily alarm");
            StepCountWidget.updateWidgets(this);
        }
    }

    private void handleWeatherUpdate(Intent intent) throws Throwable {
        // Get weather
        try {
            // Check connectivity
            if (!Util.isConnected(this))
                return;

            // Get last location
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Location lastLocation = BackgroundService.LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_LAST_LOCATION, null));
            if (lastLocation == null)
                return;

            // Get API key
            String api = prefs.getString(SettingsFragment.PREF_WEATHER_API, SettingsFragment.DEFAULT_WEATHER_API);
            String apikey_fio = prefs.getString(SettingsFragment.PREF_WEATHER_APIKEY_FIO, null);
            String apikey_owm = prefs.getString(SettingsFragment.PREF_WEATHER_APIKEY_OWM, null);
            if (apikey_owm == null) {
                ApplicationInfo app = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
                apikey_owm = app.metaData.getString("org.openweathermap.API_KEY", null);
            }

            // Get settings
            int stations = Integer.parseInt(prefs.getString(SettingsFragment.PREF_WEATHER_STATIONS, SettingsFragment.DEFAULT_WEATHER_STATIONS));
            int maxage = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PRESSURE_MAXAGE, SettingsFragment.DEFAULT_PRESSURE_MAXAGE));
            int maxdist = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PRESSURE_MAXDIST, SettingsFragment.DEFAULT_PRESSURE_MAXDIST));
            float weight = Float.parseFloat(prefs.getString(SettingsFragment.PREF_WEATHER_WEIGHT, SettingsFragment.DEFAULT_WEATHER_WEIGHT));
            long id = Long.parseLong(prefs.getString(SettingsFragment.PREF_WEATHER_ID, "-1"));
            boolean airport = prefs.getBoolean(SettingsFragment.PREF_WEATHER_AIRPORT, SettingsFragment.DEFAULT_WEATHER_AIRPORT);
            boolean cwop = prefs.getBoolean(SettingsFragment.PREF_WEATHER_CWOP, SettingsFragment.DEFAULT_WEATHER_CWOP);
            boolean synop = prefs.getBoolean(SettingsFragment.PREF_WEATHER_SYNOP, SettingsFragment.DEFAULT_WEATHER_SYNOP);
            boolean diy = prefs.getBoolean(SettingsFragment.PREF_WEATHER_DIY, SettingsFragment.DEFAULT_WEATHER_DIY);
            boolean other = prefs.getBoolean(SettingsFragment.PREF_WEATHER_OTHER, SettingsFragment.DEFAULT_WEATHER_OTHER);
            boolean firstrain = prefs.getBoolean(SettingsFragment.PREF_WEATHER_RAIN, SettingsFragment.DEFAULT_WEATHER_RAIN);
            Log.i(TAG, "stations=" + stations + " maxage=" + maxage + " maxdist=" + maxdist + " weight=" + weight +
                    " id=" + id + " airport=" + airport + " cwop=" + cwop + " synop=" + synop + " diy=" + diy + " other=" + other);

            // Fetch weather
            List<Weather> listWeather = new ArrayList<Weather>();
            if ("fio".equals(api)) {
                // Forecast.io
                Weather w = ForecastIO.getWeatherByLocation(apikey_fio, lastLocation, this);
                if (w != null) {
                    listWeather.add(w);
                    showWeatherNotification(w, this);
                }
            } else if ("owm".equals(api)) {
                // OpenWeatherMap
                if (id < 0)
                    listWeather = OpenWeatherMap.getWeatherByLocation(apikey_owm, lastLocation, stations, maxage, maxdist, weight, this);
                else {
                    Weather w = OpenWeatherMap.getWeatherByStation(apikey_owm, id, this);
                    if (w != null)
                        listWeather.add(w);
                }
            } else
                throw new Throwable("Unknown weather API");

            long time = new Date().getTime();

            // Find station with precipitation data
            Weather rainy = null;
            if (firstrain && "owm".equals(api))
                for (Weather weather : listWeather) {
                    float distance = weather.station_location.distanceTo(lastLocation);
                    if (weather.time + maxage * 60 * 1000 >= time && distance <= maxdist * 1000) {
                        if (!Double.isNaN(weather.rain_1h) && !Double.isNaN(weather.rain_today)
                                && (weather.rain_1h > 0 || weather.rain_today > 0)) {
                            rainy = weather;
                            Log.i(TAG, "Rainy " + rainy);
                            break;
                        }
                    }
                }

            // Select best weather station
            boolean found = false;
            for (Weather weather : listWeather) {
                float distance = weather.station_location.distanceTo(lastLocation);
                Log.i(TAG, weather.toString() + " " + Math.round(distance) + "m");

                if (!found &&
                        weather.time + maxage * 60 * 1000 >= time &&
                        ("fio".equals(api) || id > 0 ||
                                (weather.station_type == 1 && airport) ||
                                (weather.station_type == 2 && cwop) ||
                                (weather.station_type == 3 && synop) ||
                                (weather.station_type == 5 && diy) ||
                                ((weather.station_type < 1 || weather.station_type > 5 || weather.station_type == 4) && other))
                        && !Double.isNaN(weather.pressure)) {
                    found = true;

                    // Persist weather
                    DatabaseHelper dh = new DatabaseHelper(this);
                    if (dh.insertWeather(weather, lastLocation) && Util.debugMode(this))
                        Util.toast(getString(R.string.title_weather_settings), Toast.LENGTH_SHORT, this);
                    if (rainy != null && Double.isNaN(weather.rain_1h) && Double.isNaN(weather.rain_today)) {
                        rainy.temperature = Double.NaN;
                        rainy.humidity = Double.NaN;
                        rainy.pressure = Double.NaN;
                        rainy.wind_speed = Double.NaN;
                        rainy.wind_gust = Double.NaN;
                        rainy.wind_direction = Double.NaN;
                        rainy.visibility = Double.NaN;
                        dh.insertWeather(rainy, lastLocation);
                    }
                    dh.close();

                    // Persist reference pressure
                    Log.i(TAG, "Reference pressure " + weather.pressure + "hPa " +
                            weather.station_name + " @" + SimpleDateFormat.getDateTimeInstance().format(weather.time));
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putFloat(SettingsFragment.PREF_PRESSURE_REF_LAT, (float) weather.station_location.getLatitude());
                    editor.putFloat(SettingsFragment.PREF_PRESSURE_REF_LON, (float) weather.station_location.getLongitude());
                    editor.putFloat(SettingsFragment.PREF_PRESSURE_REF_TEMP, (float) weather.temperature);
                    editor.putFloat(SettingsFragment.PREF_PRESSURE_REF_VALUE, (float) weather.pressure);
                    editor.putLong(SettingsFragment.PREF_PRESSURE_REF_TIME, weather.time);
                    editor.apply();
                }
            }
        } finally {
            startWeatherUpdates(this);
            updateState(this, "weather");
        }
    }

    // Start/stop methods

    public static void startTracking(final Context context) {
        Log.i(TAG, "Start tracking");

        // Check if enabled
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(SettingsFragment.PREF_ENABLED, SettingsFragment.DEFAULT_ENABLED)) {
            Log.i(TAG, "Tracking disabled");
            return;
        }

        updateState(context, "start tracking");

        // Start activity recognition / repeating alarm
        boolean recognition = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_ENABLED, SettingsFragment.DEFAULT_RECOGNITION_ENABLED);
        boolean filterSteps = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_STEPS, SettingsFragment.DEFAULT_RECOGNITION_STEPS);
        if (recognition) {
            startActivityRecognition(context);
            if (!filterSteps)
                context.startService(new Intent(context, StepCounterService.class));
        } else {
            startPeriodicLocating(context);
            context.startService(new Intent(context, StepCounterService.class));
        }

        // Request passive location updates
        boolean passive = prefs.getBoolean(SettingsFragment.PREF_PASSIVE_ENABLED, SettingsFragment.DEFAULT_PASSIVE_ENABLED);
        if (passive) {
            Intent locationIntent = new Intent(context, BackgroundService.class);
            locationIntent.setAction(BackgroundService.ACTION_LOCATION_PASSIVE);
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            int minTime = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PASSIVE_MINTIME, SettingsFragment.DEFAULT_PASSIVE_MINTIME));
            int minDist = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PASSIVE_MINDIST, SettingsFragment.DEFAULT_PASSIVE_MINDIST));
            lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, minTime * 1000, minDist, pi);
            Log.i(TAG, "Requested passive location updates");
        }
    }

    public static void stopTracking(final Context context) {
        Log.i(TAG, "Stop tracking");

        stopPeriodicLocating(context);
        stopLocating(context);
        cancelNotification(context);

        // Cancel activity updates
        stopActivityRecognition(context);

        // Cancel passive location updates
        Intent locationIntent = new Intent(context, BackgroundService.class);
        locationIntent.setAction(BackgroundService.ACTION_LOCATION_PASSIVE);
        PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        lm.removeUpdates(pi);

        // Stop step counter
        context.stopService(new Intent(context, StepCounterService.class));
    }

    private static void startActivityRecognition(final Context context) {
        if (Util.hasPlayServices(context)) {
            GoogleApiClient gac = new GoogleApiClient.Builder(context).addApi(ActivityRecognition.API).build();
            if (gac.blockingConnect().isSuccess()) {
                Log.i(TAG, "GoogleApiClient connected");
                Intent activityIntent = new Intent(context, BackgroundService.class);
                activityIntent.setAction(BackgroundService.ACTION_ACTIVITY);
                PendingIntent pi = PendingIntent.getService(context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                boolean still = (prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.STILL) == DetectedActivity.STILL);
                String setting = (still ? SettingsFragment.PREF_RECOGNITION_INTERVAL_STILL : SettingsFragment.PREF_RECOGNITION_INTERVAL_MOVING);
                String standard = (still ? SettingsFragment.DEFAULT_RECOGNITION_INTERVAL_STILL : SettingsFragment.DEFAULT_RECOGNITION_INTERVAL_MOVING);
                int interval = Integer.parseInt(prefs.getString(setting, standard));

                ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(gac, interval * 1000, pi);
                Log.i(TAG, "Activity updates frequency=" + interval + "s");
            }
        }
    }

    private static void stopActivityRecognition(final Context context) {
        if (Util.hasPlayServices(context)) {
            GoogleApiClient gac = new GoogleApiClient.Builder(context).addApi(ActivityRecognition.API).build();
            if (gac.blockingConnect().isSuccess()) {
                Log.i(TAG, "GoogleApiClient connected");
                Intent activityIntent = new Intent(context, BackgroundService.class);
                activityIntent.setAction(BackgroundService.ACTION_ACTIVITY);
                PendingIntent pi = PendingIntent.getService(context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(gac, pi);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(SettingsFragment.PREF_LAST_ACTIVITY);
                editor.remove(SettingsFragment.PREF_LAST_CONFIDENCE);
                editor.apply();
                Log.i(TAG, "Canceled activity updates");
            }
        }
    }

    private static void startPeriodicLocating(Context context) {
        // Set repeating alarm
        Intent alarmIntent = new Intent(context, BackgroundService.class);
        alarmIntent.setAction(BackgroundService.ACTION_ALARM);
        PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int interval = Integer.parseInt(prefs.getString(SettingsFragment.PREF_INTERVAL, SettingsFragment.DEFAULT_INTERVAL));
        // setRepeating is inexact since KitKat
        am.setRepeating(AlarmManager.RTC_WAKEUP, new Date().getTime() + ALARM_DUE_TIME, interval * 1000, pi);
        Log.i(TAG, "Start repeating alarm frequency=" + interval + "s" + " due=" + ALARM_DUE_TIME + "ms");
    }

    private static void stopPeriodicLocating(Context context) {
        // Cancel repeating alarm
        Intent alarmIntent = new Intent(context, BackgroundService.class);
        alarmIntent.setAction(BackgroundService.ACTION_ALARM);
        PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        Log.i(TAG, "Stop repeating alarm");
    }

    public static void startLocating(Context context) {
        Log.i(TAG, "Start locating");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Mark active
        if (prefs.getInt(SettingsFragment.PREF_STATE, STATE_IDLE) != STATE_IDLE) {
            Log.i(TAG, "Already active");
            return;
        }

        boolean network = prefs.getBoolean(SettingsFragment.PREF_USE_NETWORK, SettingsFragment.DEFAULT_USE_NETWORK);
        boolean gps = prefs.getBoolean(SettingsFragment.PREF_USE_GPS, SettingsFragment.DEFAULT_USE_GPS);
        int minTime = Integer.parseInt(prefs.getString(SettingsFragment.PREF_MINTIME, SettingsFragment.DEFAULT_MINTIME));
        int minDist = Integer.parseInt(prefs.getString(SettingsFragment.PREF_MINDIST, SettingsFragment.DEFAULT_MINDIST));

        // Request coarse location
        if (network && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Intent locationIntent = new Intent(context, BackgroundService.class);
            locationIntent.setAction(BackgroundService.ACTION_LOCATION_COARSE);
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime * 1000, minDist, pi);
            Log.i(TAG, "Requested network location updates");
        }

        // Request fine location
        if (gps && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent locationIntent = new Intent(context, BackgroundService.class);
            locationIntent.setAction(BackgroundService.ACTION_LOCATION_FINE);
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime * 1000, minDist, pi);
            context.startService(new Intent(context, GpsStatusService.class));
            Log.i(TAG, "Requested GPS location updates");
        }

        // Initiate location timeout
        if ((network && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) || (gps && lm.isProviderEnabled(LocationManager.GPS_PROVIDER))) {
            int timeout = Integer.parseInt(prefs.getString(SettingsFragment.PREF_TIMEOUT, SettingsFragment.DEFAULT_TIMEOUT));
            Intent alarmIntent = new Intent(context, BackgroundService.class);
            alarmIntent.setAction(BackgroundService.ACTION_LOCATION_TIMEOUT);
            PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                am.setExact(AlarmManager.RTC_WAKEUP, new Date().getTime() + timeout * 1000, pi);
            else
                am.set(AlarmManager.RTC_WAKEUP, new Date().getTime() + timeout * 1000, pi);
            Log.i(TAG, "Set timeout=" + timeout + "s");

            prefs.edit().putInt(SettingsFragment.PREF_STATE, STATE_ACQUIRING).apply();
            updateState(context, "start locating");
        } else
            Log.i(TAG, "No location providers");

        // Initiate satellite check
        if (gps && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            int check = Integer.parseInt(prefs.getString(SettingsFragment.PREF_CHECK_TIME, SettingsFragment.DEFAULT_CHECK_TIME));
            Intent alarmIntent = new Intent(context, BackgroundService.class);
            alarmIntent.setAction(BackgroundService.ACTION_LOCATION_CHECK);
            PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                am.setExact(AlarmManager.RTC_WAKEUP, new Date().getTime() + check * 1000, pi);
            else
                am.set(AlarmManager.RTC_WAKEUP, new Date().getTime() + check * 1000, pi);
            Log.i(TAG, "Set check=" + check + "s");
        }

        // Start pressure service
        boolean pressure_enabled = prefs.getBoolean(SettingsFragment.PREF_PRESSURE_ENABLED, SettingsFragment.DEFAULT_PRESSURE_ENABLED);
        if (pressure_enabled)
            context.startService(new Intent(context, PressureService.class));

        // Keep step counter service alive
        boolean recognition = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_ENABLED, SettingsFragment.DEFAULT_RECOGNITION_ENABLED);
        boolean recognizeSteps = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_STEPS, SettingsFragment.DEFAULT_RECOGNITION_STEPS);
        boolean pref_unknown_steps = prefs.getBoolean(SettingsFragment.PREF_RECOGNITION_UNKNOWN_STEPS, SettingsFragment.DEFAULT_RECOGNITION_UNKNOWN_STEPS);
        int activity = prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.STILL);
        boolean onfoot = (activity == DetectedActivity.ON_FOOT || activity == DetectedActivity.WALKING || activity == DetectedActivity.RUNNING);
        if (pref_unknown_steps && activity == DetectedActivity.UNKNOWN)
            onfoot = true;
        if (!recognition || !recognizeSteps || onfoot) // Keep alive
            context.startService(new Intent(context, StepCounterService.class));
    }

    private static void stopLocating(Context context) {
        Log.i(TAG, "Stop locating");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Cancel coarse location updates
        {
            Intent locationIntent = new Intent(context, BackgroundService.class);
            locationIntent.setAction(BackgroundService.ACTION_LOCATION_COARSE);
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.removeUpdates(pi);
        }

        // Cancel fine location updates
        {
            Intent locationIntent = new Intent(context, BackgroundService.class);
            locationIntent.setAction(BackgroundService.ACTION_LOCATION_FINE);
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.removeUpdates(pi);
            context.stopService(new Intent(context, GpsStatusService.class));
        }

        // Cancel check
        {
            Intent alarmIntent = new Intent(context, BackgroundService.class);
            alarmIntent.setAction(BackgroundService.ACTION_LOCATION_CHECK);
            PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
        }

        // Cancel timeout
        {
            Intent alarmIntent = new Intent(context, BackgroundService.class);
            alarmIntent.setAction(BackgroundService.ACTION_LOCATION_TIMEOUT);
            PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
        }

        // Stop pressure service
        context.stopService(new Intent(context, PressureService.class));

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(SettingsFragment.PREF_STATE, STATE_IDLE);
        editor.remove(SettingsFragment.PREF_LOCATION_TYPE);
        editor.remove(SettingsFragment.PREF_BEST_LOCATION);
        editor.apply();
        updateState(context, "stop locating");
    }

    public static void startWeatherUpdates(Context context) {
        // Check if weather updates enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(SettingsFragment.PREF_WEATHER_ENABLED, SettingsFragment.DEFAULT_WEATHER_ENABLED)) {
            Log.i(TAG, "Weather updates disabled");
            return;
        }

        // Set alarm
        Intent alarmIntent = new Intent(context, BackgroundService.class);
        alarmIntent.setAction(BackgroundService.ACTION_UPDATE_WEATHER);
        PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int interval = 60 * 1000 * Integer.parseInt(prefs.getString(SettingsFragment.PREF_WEATHER_INTERVAL, SettingsFragment.DEFAULT_WEATHER_INTERVAL));
        long trigger = new Date().getTime() / interval * interval + interval;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        else
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
        Log.i(TAG, "Start weather updates frequency=" + interval + "m" + " next=" + SimpleDateFormat.getDateTimeInstance().format(trigger));
    }

    public static void stopWeatherUpdates(Context context) {
        Intent alarmIntent = new Intent(context, BackgroundService.class);
        alarmIntent.setAction(BackgroundService.ACTION_UPDATE_WEATHER);
        PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        Log.i(TAG, "Stop weather updates");
    }

    public static void startDaily(Context context) {
        Intent alarmIntent = new Intent(context, BackgroundService.class);
        alarmIntent.setAction(BackgroundService.ACTION_DAILY);
        PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long trigger = calendar.getTimeInMillis() + 24 * 3600 * 1000;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
        else
            alarmManager.set(AlarmManager.RTC_WAKEUP, trigger, pi);
        Log.i(TAG, "Start daily job next=" + SimpleDateFormat.getDateTimeInstance().format(trigger));
    }

    public static void stopDaily(Context context) {
        Intent alarmIntent = new Intent(context, BackgroundService.class);
        alarmIntent.setAction(BackgroundService.ACTION_DAILY);
        PendingIntent pi = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        Log.i(TAG, "Stop daily job");
    }

    // Helper methods

    private static void convertTime(Intent intent) {
        if (intent.getExtras() == null || intent.getExtras().getString(EXTRA_TRACK_NAME, null) == null)
            intent.putExtra(EXTRA_TRACK_NAME, DEFAULT_TRACK_NAME);
        Bundle extras = intent.getExtras();
        if (extras.get(EXTRA_TIME_FROM) instanceof String)
            intent.putExtra(EXTRA_TIME_FROM, new DateTime((String) extras.get(EXTRA_TIME_FROM)).getMillis());
        if (extras.get(EXTRA_TIME_TO) instanceof String)
            intent.putExtra(EXTRA_TIME_TO, new DateTime((String) extras.get(EXTRA_TIME_TO)).getMillis());
    }

    private boolean isBetterLocation(Location prev, Location current) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pref_altitude = prefs.getBoolean(SettingsFragment.PREF_ALTITUDE, SettingsFragment.DEFAULT_ALTITUDE);
        return (prev == null ||
                ((!pref_altitude || !prev.hasAltitude() || current.hasAltitude()) &&
                        (current.hasAccuracy() ? current.getAccuracy() : Float.MAX_VALUE) <
                                (prev.hasAccuracy() ? prev.getAccuracy() : Float.MAX_VALUE)));
    }

    private void handleLocation(int locationType, Location location) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Filter nearby locations
        int pref_nearby = Integer.parseInt(prefs.getString(SettingsFragment.PREF_NEARBY, SettingsFragment.DEFAULT_NEARBY));
        Location lastLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_LAST_LOCATION, null));
        if (locationType == LOCATION_TRACKPOINT || locationType == LOCATION_WAYPOINT ||
                lastLocation == null || lastLocation.distanceTo(location) >= pref_nearby ||
                (lastLocation.hasAccuracy() ? lastLocation.getAccuracy() : Float.MAX_VALUE) >
                        (location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE)) {
            // New location
            Log.i(TAG, "New location=" + location + " type=" + locationType);

            // Derive altitude from pressure
            int pref_accuracy = Integer.parseInt(prefs.getString(SettingsFragment.PREF_PRESSURE_ACCURACY, SettingsFragment.DEFAULT_PRESSURE_ACCURACY));
            if (!location.hasAltitude() || !location.hasAccuracy() ||
                    location.getAltitude() * pref_accuracy / 100 >= location.getAccuracy() * 1.5) {
                float altitude = PressureService.getAltitude(location, this);
                if (!Float.isNaN(altitude))
                    location.setAltitude(altitude);
            }

            // Add elevation data
            if (!location.hasAltitude() && Util.isConnected(this)) {
                if (locationType == LOCATION_WAYPOINT) {
                    if (prefs.getBoolean(SettingsFragment.PREF_ALTITUDE_WAYPOINT, SettingsFragment.DEFAULT_ALTITUDE_WAYPOINT))
                        GoogleElevationApi.getElevation(location, this);
                } else {
                    if (prefs.getBoolean(SettingsFragment.PREF_ALTITUDE_TRACKPOINT, SettingsFragment.DEFAULT_ALTITUDE_TRACKPOINT))
                        GoogleElevationApi.getElevation(location, this);
                }
            }

            // Get waypoint name
            String waypointName = null;
            if (locationType == LOCATION_WAYPOINT) {
                List<String> listAddress = reverseGeocode(location, this);
                if (listAddress == null || listAddress.size() == 0)
                    waypointName = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM).format(new Date());
                else
                    waypointName = TextUtils.join(", ", listAddress);
            }

            // Persist new location
            prefs.edit().putString(SettingsFragment.PREF_LAST_LOCATION, LocationSerializer.serialize(location)).apply();
            DatabaseHelper dh = null;
            try {
                dh = new DatabaseHelper(this);
                int activity_type = prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.UNKNOWN);
                int activity_confidence = prefs.getInt(SettingsFragment.PREF_LAST_CONFIDENCE, -1);
                int stepcount = dh.getSteps(location.getTime());
                dh.insertLocation(location, waypointName, activity_type, activity_confidence, stepcount).close();
            } finally {
                if (dh != null)
                    dh.close();
            }

            // Feedback
            updateState(this, "handle location");
            if (locationType == LOCATION_TRACKPOINT || locationType == LOCATION_WAYPOINT) {
                if (locationType == LOCATION_WAYPOINT)
                    Util.toast(waypointName, Toast.LENGTH_LONG, this);
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(locationType == LOCATION_TRACKPOINT ? VIBRATE_SHORT : VIBRATE_LONG);
            } else if (Util.debugMode(this))
                Util.toast(getString(R.string.title_trackpoint) + " " + getProviderName(location, this), Toast.LENGTH_SHORT, this);
        } else
            Log.i(TAG, "Filtered location=" + location);
    }

    private static void correctAltitude(Location location, Context context) {
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs.getBoolean(SettingsFragment.PREF_CORRECTION_ENABLED, SettingsFragment.DEFAULT_CORRECTION_ENABLED))
                try {
                    double offset = getEGM96Offset(location, context);
                    Log.i(TAG, "Offset=" + offset);
                    location.setAltitude(location.getAltitude() - offset);
                    Log.i(TAG, "Corrected location=" + location);
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
        }
    }

    private static double getEGM96Offset(Location location, Context context) throws IOException {
        InputStream is = null;
        try {
            double lat = location.getLatitude();
            double lon = location.getLongitude();

            int y = (int) Math.floor((90 - lat) * 4);
            int x = (int) Math.floor((lon >= 0 ? lon : lon + 360) * 4);
            int p = ((y * 1440) + x) * 2;
            int o;

            if (mEGM96Pointer >= 0 && p == mEGM96Pointer)
                o = mEGM96Offset;
            else {
                is = context.getAssets().open("WW15MGH.DAC");
                is.skip(p);

                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.order(ByteOrder.BIG_ENDIAN);
                bb.put((byte) is.read());
                bb.put((byte) is.read());
                o = bb.getShort(0);

                mEGM96Pointer = p;
                mEGM96Offset = o;
            }

            return o / 100d;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static List<String> reverseGeocode(Location location, Context context) {
        List<String> listline = new ArrayList<>();
        if (location != null && Geocoder.isPresent())
            try {
                Geocoder geocoder = new Geocoder(context);
                List<Address> listPlace = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (listPlace != null && listPlace.size() > 0) {
                    for (int l = 0; l < listPlace.get(0).getMaxAddressLineIndex(); l++)
                        listline.add(listPlace.get(0).getAddressLine(l));
                }
            } catch (IOException ex) {
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
        return listline;
    }

    private static void updateState(Context context, String reason) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Check if tracking enabled
        if (!prefs.getBoolean(SettingsFragment.PREF_ENABLED, SettingsFragment.DEFAULT_ENABLED))
            return;

        // Get state
        int state = prefs.getInt(SettingsFragment.PREF_STATE, STATE_IDLE);
        int activityType = prefs.getInt(SettingsFragment.PREF_LAST_ACTIVITY, DetectedActivity.STILL);
        Location lastLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_LAST_LOCATION, null));

        // Get title
        String activity = getActivityName(activityType, context);
        String bearing = "-";
        String altitude = "?";
        if (lastLocation != null) {
            if (lastLocation.hasBearing())
                bearing = getWindDirectionName(lastLocation.getBearing(), context);
            if (lastLocation.hasAltitude())
                altitude = Long.toString(Math.round(lastLocation.getAltitude()));
        }
        long steps;
        DatabaseHelper db = null;
        try {
            db = new DatabaseHelper(context);
            steps = db.getSteps(new Date().getTime());
        } finally {
            if (db != null)
                db.close();
        }
        String title = context.getString(R.string.msg_notification, activity, bearing, altitude, steps);

        // Get text
        String text = null;
        if (state == STATE_IDLE)
            if (lastLocation == null)
                text = context.getString(R.string.msg_idle, "-", "");
            else {
                text = context.getString(R.string.msg_idle,
                        SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.MEDIUM).format(new Date(lastLocation.getTime())),
                        getProviderName(lastLocation, context));
            }
        else if (state == STATE_ACQUIRING)
            text = context.getString(R.string.msg_acquiring);
        else if (state == STATE_ACQUIRED) {
            Location bestLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_BEST_LOCATION, null));
            text = context.getString(R.string.msg_acquired,
                    SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.MEDIUM).format(new Date(bestLocation.getTime())),
                    getProviderName(bestLocation, context),
                    bestLocation.hasAccuracy() ? Math.round(bestLocation.getAccuracy()) : 0);
        }

        //if (debugMode(context))
        //    text += " " + reason;

        // Build main intent
        Intent riSettings = new Intent(context, SettingsActivity.class);
        riSettings.setAction(Intent.ACTION_MAIN);
        riSettings.addCategory(Intent.CATEGORY_LAUNCHER);
        riSettings.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piSettings = PendingIntent.getActivity(context, 1, riSettings, PendingIntent.FLAG_UPDATE_CURRENT);

        // Build notification
        Notification.Builder notificationBuilder = new Notification.Builder(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.backpacker_grey).copy(Bitmap.Config.ARGB_8888, true);

            // Show temperature
            float ref_temp = prefs.getFloat(SettingsFragment.PREF_PRESSURE_REF_TEMP, Float.NaN);
            long ref_time = prefs.getLong(SettingsFragment.PREF_PRESSURE_REF_TIME, 0);
            int maxage = Integer.parseInt(prefs.getString(SettingsFragment.PREF_WEATHER_MAXAGE, SettingsFragment.DEFAULT_WEATHER_MAXAGE));
            String unit = prefs.getString(SettingsFragment.PREF_TEMPERATURE, SettingsFragment.DEFAULT_TEMPERATURE);
            if (!Float.isNaN(ref_temp) && ref_time + maxage * 60 * 1000 > new Date().getTime()) {
                if ("f".equals(unit))
                    ref_temp = ref_temp * 9 / 5 + 32;

                int size = largeIcon.getWidth() / 4;
                String label = Integer.toString(Math.round(ref_temp));

                Paint paint = new Paint();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.GRAY);
                paint.setTextSize(size);
                paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));

                Rect bounds = new Rect();
                paint.getTextBounds(label, 0, label.length(), bounds);

                Canvas canvas = new Canvas(largeIcon);
                canvas.drawText(label, 0, bounds.height(), paint);
            }

            notificationBuilder.setLargeIcon(largeIcon);
        }

        if (activityType == DetectedActivity.STILL)
            notificationBuilder.setSmallIcon(R.drawable.pause);
        else if (activityType == DetectedActivity.ON_FOOT || activityType == DetectedActivity.WALKING)
            notificationBuilder.setSmallIcon(R.drawable.walk);
        else if (activityType == DetectedActivity.RUNNING)
            notificationBuilder.setSmallIcon(R.drawable.run);
        else if (activityType == DetectedActivity.ON_BICYCLE)
            notificationBuilder.setSmallIcon(R.drawable.bike);
        else if (activityType == DetectedActivity.IN_VEHICLE)
            notificationBuilder.setSmallIcon(R.drawable.car);
        else
            notificationBuilder.setSmallIcon(R.drawable.explore);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            notificationBuilder.setColor(context.getResources().getColor(R.color.color_teal_600));

        notificationBuilder.setContentTitle(title);
        notificationBuilder.setContentText(text);
        notificationBuilder.setContentIntent(piSettings);
        notificationBuilder.setUsesChronometer(lastLocation != null);
        notificationBuilder.setWhen(lastLocation == null ? System.currentTimeMillis() : lastLocation.getTime());
        notificationBuilder.setAutoCancel(false);
        notificationBuilder.setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setCategory(Notification.CATEGORY_STATUS);
            notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        if (state == STATE_IDLE) {
            // Build trackpoint intent
            Intent riTrackpoint = new Intent(context, BackgroundService.class);
            riTrackpoint.setAction(BackgroundService.ACTION_TRACKPOINT);
            PendingIntent piTrackpoint = PendingIntent.getService(context, 2, riTrackpoint, PendingIntent.FLAG_UPDATE_CURRENT);

            // Build waypoint intent
            Intent riWaypoint = new Intent(context, BackgroundService.class);
            riWaypoint.setAction(BackgroundService.ACTION_WAYPOINT);
            PendingIntent piWaypoint = PendingIntent.getService(context, 3, riWaypoint, PendingIntent.FLAG_UPDATE_CURRENT);

            // Add actions
            notificationBuilder.addAction(android.R.drawable.ic_menu_mylocation, context.getString(R.string.title_trackpoint),
                    piTrackpoint);
            notificationBuilder.addAction(android.R.drawable.ic_menu_myplaces, context.getString(R.string.title_waypoint),
                    piWaypoint);

        } else {
            // Indeterminate progress
            int fixed = prefs.getInt(SettingsFragment.PREF_SATS_FIXED, 0);
            int visible = prefs.getInt(SettingsFragment.PREF_SATS_VISIBLE, 0);
            if (visible == 0)
                notificationBuilder.setProgress(0, 0, true);
            else
                notificationBuilder.setProgress(visible, fixed, false);

            // Build stop intent
            Intent riStop = new Intent(context, BackgroundService.class);
            riStop.setAction(BackgroundService.ACTION_STOP_LOCATING);
            PendingIntent piStop = PendingIntent.getService(context, 4, riStop, PendingIntent.FLAG_UPDATE_CURRENT);

            // Build accept intent
            Intent riAccept = new Intent(context, BackgroundService.class);
            riAccept.setAction(BackgroundService.ACTION_LOCATION_TIMEOUT);
            PendingIntent piAccept = PendingIntent.getService(context, 5, riAccept, PendingIntent.FLAG_UPDATE_CURRENT);

            // Add cancel action
            notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(android.R.string.cancel),
                    piStop);

            // Add accept action
            Location bestLocation = LocationDeserializer.deserialize(prefs.getString(SettingsFragment.PREF_BEST_LOCATION, null));
            boolean pressure = prefs.getBoolean(SettingsFragment.PREF_PRESSURE_ENABLED, SettingsFragment.DEFAULT_PRESSURE_ENABLED);
            pressure = (pressure ? prefs.getFloat(SettingsFragment.PREF_PRESSURE_VALUE, -1) >= 0 : true);
            if (bestLocation != null && pressure)
                notificationBuilder.addAction(android.R.drawable.ic_menu_save, context.getString(R.string.title_accept),
                        piAccept);
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_LOCATION, notificationBuilder.build());
    }

    private static void cancelNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_LOCATION);
    }

    public static void showWeatherNotification(Weather weather, Context context) {
        Notification.Builder notificationBuilder = new Notification.Builder(context);
        int resId = (weather.icon == null ? -1 : context.getResources().getIdentifier(weather.icon.replace("-", "_git co"), "drawable", context.getPackageName()));
        Log.i(TAG, "icon=" + weather.icon + " res=" + resId);
        if (resId > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), resId).copy(Bitmap.Config.ARGB_8888, true);
                notificationBuilder.setLargeIcon(largeIcon);
                notificationBuilder.setSmallIcon(resId);
            } else
                notificationBuilder.setSmallIcon(resId);
        } else
            notificationBuilder.setSmallIcon(android.R.drawable.ic_menu_help);
        notificationBuilder.setContentTitle(weather.summary);

        double temperature = weather.temperature;
        if (!Double.isNaN(temperature)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String temperature_unit = prefs.getString(SettingsFragment.PREF_TEMPERATURE, SettingsFragment.DEFAULT_TEMPERATURE);
            if ("f".equals(temperature_unit))
                temperature = temperature * 9 / 5 + 32;
            final DecimalFormat DF = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.ROOT));
            notificationBuilder.setContentText(DF.format(temperature) + "°" + temperature_unit.toUpperCase());
        }
        notificationBuilder.setUsesChronometer(true);
        notificationBuilder.setWhen(System.currentTimeMillis());
        notificationBuilder.setAutoCancel(false);
        notificationBuilder.setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setCategory(Notification.CATEGORY_STATUS);
            notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_WEATHER, notificationBuilder.build());
    }

    public static void removeWeatherIcon(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_WEATHER);
    }

    public static String getActivityName(int activityType, Context context) {
        switch (activityType) {
            case DetectedActivity.STILL:
                return context.getString(R.string.still);
            case DetectedActivity.TILTING:
                return context.getString(R.string.tilting);
            case DetectedActivity.ON_FOOT:
                return context.getString(R.string.on_foot);
            case DetectedActivity.WALKING:
                return context.getString(R.string.walking);
            case DetectedActivity.RUNNING:
                return context.getString(R.string.running);
            case DetectedActivity.ON_BICYCLE:
                return context.getString(R.string.on_bicycle);
            case DetectedActivity.IN_VEHICLE:
                return context.getString(R.string.in_vehicle);
            case DetectedActivity.UNKNOWN:
                return context.getString(R.string.unknown);
            case -1:
                return context.getString(R.string.motion);
            case -2:
                return context.getString(R.string.displacement);
            default:
                return context.getString(R.string.undefined);
        }
    }

    public static String getWindDirectionName(float bearing, Context context) {
        int b = Math.round(bearing) + 15;
        b = (b % 360) / 30 * 30;
        int resId = context.getResources().getIdentifier("direction_" + b, "string", context.getPackageName());
        return (resId == 0 ? "?" : context.getString(resId));
    }

    private static String getProviderName(Location location, Context context) {
        String provider = (location == null ? context.getString(R.string.undefined) : location.getProvider());
        int resId = context.getResources().getIdentifier("provider_" + provider, "string", context.getPackageName());
        return (resId == 0 ? provider : context.getString(resId));
    }

    private static String writeFile(boolean gpx, String trackName, boolean extensions, long from, long to, Context context) throws IOException {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BackPackTrackII");
        folder.mkdirs();
        String fileName = folder.getAbsolutePath() + File.separatorChar + trackName + (gpx ? ".gpx" : ".kml");
        Log.i(TAG, "Writing file=" + fileName +
                " extensions=" + extensions +
                " from=" + SimpleDateFormat.getDateTimeInstance().format(new Date(from)) +
                " to=" + SimpleDateFormat.getDateTimeInstance().format(new Date(to)));
        DatabaseHelper dh = null;
        Cursor trackPoints = null;
        Cursor wayPoints = null;
        try {
            dh = new DatabaseHelper(context);
            trackPoints = dh.getLocations(from, to, true, false, true);
            wayPoints = dh.getLocations(from, to, false, true, true);
            if (gpx)
                GPXFileWriter.writeGPXFile(new File(fileName), trackName, extensions, trackPoints, wayPoints, context);
            else
                KMLFileWriter.writeKMLFile(new File(fileName), trackName, extensions, trackPoints, wayPoints, context);
        } finally {
            if (wayPoints != null)
                wayPoints.close();
            if (trackPoints != null)
                trackPoints.close();
            if (dh != null)
                dh.close();
        }
        return fileName;
    }

    public static void getAltitude(long from, long to, Context context) {
        Log.i(TAG, "Get altitude" +
                " from=" + SimpleDateFormat.getDateTimeInstance().format(new Date(from)) +
                " to=" + SimpleDateFormat.getDateTimeInstance().format(new Date(to)));

        DatabaseHelper dh = null;
        Cursor cursor = null;
        boolean first = true;
        try {
            dh = new DatabaseHelper(context);
            cursor = dh.getLocations(from, to, true, true, true);

            int colID = cursor.getColumnIndex("ID");
            int colTime = cursor.getColumnIndex("time");
            int colProvider = cursor.getColumnIndex("provider");
            int colLatitude = cursor.getColumnIndex("latitude");
            int colLongitude = cursor.getColumnIndex("longitude");

            while (cursor.moveToNext()) {
                long id = cursor.getLong(colID);
                long time = cursor.getLong(colTime);
                final String provider = cursor.getString(colProvider);
                double latitude = cursor.getDouble(colLatitude);
                double longitude = cursor.getDouble(colLongitude);

                Location location = new Location(provider);
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                location.setTime(time);
                if (GoogleElevationApi.getElevation(location, context)) {
                    if (first)
                        first = false;
                    else
                        try {
                            // Max. 5 requests/second
                            Thread.sleep(200);
                        } catch (InterruptedException ignored) {
                        }
                    Log.i(TAG, "New altitude for location=" + location);
                    dh.updateLocationAltitude(id, location.getAltitude());
                } else
                    break;
            }
        } finally {
            if (cursor != null)
                cursor.close();
            if (dh != null)
                dh.close();
        }
    }

    // Serialization

    public static class LocationSerializer implements JsonSerializer<Location> {
        public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jObject = new JsonObject();

            jObject.addProperty("Provider", src.getProvider());
            jObject.addProperty("Time", src.getTime());
            jObject.addProperty("Latitude", src.getLatitude());
            jObject.addProperty("Longitude", src.getLongitude());

            if (src.hasAltitude())
                jObject.addProperty("Altitude", src.getAltitude());

            if (src.hasSpeed())
                jObject.addProperty("Speed", src.getSpeed());

            if (src.hasAccuracy())
                jObject.addProperty("Accuracy", src.getAccuracy());

            if (src.hasBearing())
                jObject.addProperty("Bearing", src.getBearing());

            return jObject;
        }

        public static String serialize(Location location) {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(Location.class, new LocationSerializer());
            Gson gson = builder.create();
            return gson.toJson(location);
        }
    }

    public static class LocationDeserializer implements JsonDeserializer<Location> {
        public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jObject = (JsonObject) json;
            Location location = new Location(jObject.get("Provider").getAsString());

            location.setTime(jObject.get("Time").getAsLong());
            location.setLatitude(jObject.get("Latitude").getAsDouble());
            location.setLongitude(jObject.get("Longitude").getAsDouble());

            if (jObject.has("Altitude"))
                location.setAltitude(jObject.get("Altitude").getAsDouble());

            if (jObject.has("Speed"))
                location.setSpeed(jObject.get("Speed").getAsFloat());

            if (jObject.has("Bearing"))
                location.setBearing(jObject.get("Bearing").getAsFloat());

            if (jObject.has("Accuracy"))
                location.setAccuracy(jObject.get("Accuracy").getAsFloat());

            return location;
        }

        public static Location deserialize(String json) {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(Location.class, new LocationDeserializer());
            Gson gson = builder.create();
            return gson.fromJson(json, Location.class);
        }
    }
}