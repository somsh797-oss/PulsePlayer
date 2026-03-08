package com.pulse.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "pulse_player_channel";
    private static final int NOTIF_ID = 1;

    public interface PlaybackCallback {
        void onProgressUpdate(int position, int duration);
        void onSongComplete();
        void onPlayStateChanged(boolean playing);
    }

    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    private final IBinder binder = new MusicBinder();
    private MediaPlayer mediaPlayer;
    private PlaybackCallback callback;
    private Song currentSong;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean isPlaying = false;

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying) {
                if (callback != null) {
                    callback.onProgressUpdate(
                        mediaPlayer.getCurrentPosition(),
                        mediaPlayer.getDuration()
                    );
                }
                progressHandler.postDelayed(this, 500);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build());

        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            if (callback != null) callback.onSongComplete();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            isPlaying = false;
            return false;
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(PlaybackCallback cb) {
        this.callback = cb;
    }

    public void playSong(Song song) {
        currentSong = song;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(getApplicationContext(), song.uri);
            mediaPlayer.prepare();
            requestAudioFocus();
            mediaPlayer.start();
            isPlaying = true;
            if (callback != null) callback.onPlayStateChanged(true);
            startProgressUpdates();
            showNotification(song.title, song.artist, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            if (callback != null) callback.onPlayStateChanged(false);
            stopProgressUpdates();
            if (currentSong != null)
                showNotification(currentSong.title, currentSong.artist, false);
        } else {
            requestAudioFocus();
            mediaPlayer.start();
            isPlaying = true;
            if (callback != null) callback.onPlayStateChanged(true);
            startProgressUpdates();
            if (currentSong != null)
                showNotification(currentSong.title, currentSong.artist, true);
        }
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(msec);
            if (callback != null)
                callback.onProgressUpdate(msec, mediaPlayer.getDuration());
        }
    }

    public void setVolume(float vol) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(vol, vol);
        }
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        if (isPlaying) togglePlayPause();
                    }
                })
                .build();
            audioManager.requestAudioFocus(focusRequest);
        }
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Pulse Player", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private void showNotification(String title, String artist, boolean playing) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(icon)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pi)
            .setOngoing(playing)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build();

        startForeground(NOTIF_ID, notif);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProgressUpdates();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
