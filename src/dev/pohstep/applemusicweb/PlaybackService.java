package dev.pohstep.applemusicweb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Does nothing except keep the process at foreground priority while music plays, so Android does
 * not freeze or kill the app (and its media session) when the launcher is in front.
 * Shows one silent, minimum-importance ongoing notification.
 */
public class PlaybackService extends Service {
    private static final String CH = "amw_playback";

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CH) == null) {
            NotificationChannel c = new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            c.setSound(null, null);
            nm.createNotificationChannel(c);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Apple Music Web")
                .setContentText("Playing in the background")
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
        startForeground(1, n);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
