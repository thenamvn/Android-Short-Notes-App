package com.zypher.shortnotes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BackgroundService extends Service {
    private static final String CHANNEL_ID = "BackgroundServiceChannel";
    private Handler handler;
    private PeriodicTaskRunnable periodicRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        handler = new Handler();
        periodicRunnable = new PeriodicTaskRunnable(this);
        handler.post(periodicRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Background Service")
                .setContentText("Running...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && periodicRunnable != null) {
            handler.removeCallbacks(periodicRunnable);
        }
        sendStatusToServerAsync(false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class PeriodicTaskRunnable implements Runnable {
        private final BackgroundService service;
        
        PeriodicTaskRunnable(BackgroundService service) {
            this.service = service;
        }

        @Override
        public void run() {
            if (service != null) {
                service.sendStatusToServerAsync(true);
                if (service.handler != null) {
                    service.handler.postDelayed(this, 10000);
                }
            }
        }
    }

    private void sendStatusToServerAsync(final boolean isRunning) {
        new Thread(new ServerStatusTask(this, isRunning)).start();
    }

    private static class ServerStatusTask implements Runnable {
        private final BackgroundService service;
        private final boolean isRunning;
        
        ServerStatusTask(BackgroundService service, boolean isRunning) {
            this.service = service;
            this.isRunning = isRunning;
        }

        @Override
        public void run() {
            boolean success = service.sendStatusToServer(isRunning);
            if (service.handler != null) {
                service.handler.post(() -> {
                    if (success) {
                        Toast.makeText(service.getApplicationContext(), "Trạng thái True gửi thành công", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(service.getApplicationContext(), "Lỗi khi gửi trạng thái", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private boolean sendStatusToServer(boolean isRunning) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://responsibility-sorted.gl.at.ply.gg:40543/log/status");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            
            String data = "status=" + (isRunning ? "deo biet" : "False");
            if (data == null || data.isEmpty()) {
                return false;
            }
            
            OutputStream os = connection.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
