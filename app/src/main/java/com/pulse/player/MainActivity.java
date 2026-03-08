package com.pulse.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private PlayerView playerView;
    private MusicService musicService;
    private boolean serviceBound = false;
    private List<Song> allSongs = new ArrayList<>();
    private int currentIndex = -1;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceBound = true;
            musicService.setCallback(new MusicService.PlaybackCallback() {
                @Override
                public void onProgressUpdate(int position, int duration) {
                    playerView.updateProgress(position, duration);
                }
                @Override
                public void onSongComplete() {
                    nextSong();
                }
                @Override
                public void onPlayStateChanged(boolean playing) {
                    playerView.updatePlayState(playing);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen immersive
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        // Keep screen on while playing (optional)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Dark status bar
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        getWindow().setStatusBarColor(0xFF0a0a0f);

        // Set up our custom view
        playerView = new PlayerView(this, new PlayerView.PlayerCallback() {
            @Override public void onPlayPause() { togglePlayPause(); }
            @Override public void onNext() { nextSong(); }
            @Override public void onPrev() { prevSong(); }
            @Override public void onSeekForward5() { seekBy(5000); }
            @Override public void onSeekBackward5() { seekBy(-5000); }
            @Override public void onSeekTo(int position) { if (serviceBound) musicService.seekTo(position); }
            @Override public void onVolumeChange(float vol) { if (serviceBound) musicService.setVolume(vol); }
            @Override public void onSongSelected(int index) { playSongAt(index); }
            @Override public void onSearch(String query) { filterSongs(query); }
        });
        setContentView(playerView);

        // Start + bind music service
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Check permissions and load songs
        checkPermissionsAndLoad();
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                    PERMISSION_REQUEST_CODE);
            } else {
                loadAllSongs();
            }
        } else {
            // Android 10 (Redmi 9A)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            } else {
                loadAllSongs();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllSongs();
            } else {
                Toast.makeText(this, "Storage permission is needed to load songs.", Toast.LENGTH_LONG).show();
                playerView.showPermissionDenied();
            }
        }
    }

    private void loadAllSongs() {
        playerView.showLoading(true);
        executor.execute(() -> {
            List<Song> songs = scanAllAudio();
            mainHandler.post(() -> {
                allSongs = songs;
                playerView.showLoading(false);
                playerView.setSongs(allSongs, -1);
                if (allSongs.isEmpty()) {
                    Toast.makeText(this, "No audio files found on device.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, allSongs.size() + " songs loaded", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private List<Song> scanAllAudio() {
        List<Song> list = new ArrayList<>();

        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
            + MediaStore.Audio.Media.DURATION + " > 30000"; // > 30 seconds

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        try (Cursor cursor = getContentResolver().query(
                uri, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " ASC")) {

            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                do {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    long duration = cursor.getLong(durationCol);
                    long albumId = cursor.getLong(albumIdCol);

                    Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    Uri artUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId);

                    if (title == null || title.isEmpty()) title = "Unknown";
                    if (artist == null || artist.equals("<unknown>")) artist = "Unknown Artist";
                    if (album == null) album = "Unknown Album";

                    list.add(new Song(id, title, artist, album, duration, contentUri, artUri));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Sort by title A-Z
        Collections.sort(list, (a, b) ->
            a.title.compareToIgnoreCase(b.title));

        return list;
    }

    // ── Playback controls ──

    private void playSongAt(int index) {
        if (index < 0 || index >= allSongs.size()) return;
        currentIndex = index;
        Song s = allSongs.get(index);
        if (serviceBound) {
            musicService.playSong(s);
        }
        playerView.setCurrentSong(s, currentIndex, allSongs.size());
    }

    private void togglePlayPause() {
        if (currentIndex == -1 && !allSongs.isEmpty()) {
            playSongAt(0);
            return;
        }
        if (serviceBound) {
            musicService.togglePlayPause();
        }
    }

    private void nextSong() {
        if (allSongs.isEmpty()) return;
        int next = (currentIndex + 1) % allSongs.size();
        playSongAt(next);
    }

    private void prevSong() {
        if (allSongs.isEmpty()) return;
        if (serviceBound && musicService.getCurrentPosition() > 3000) {
            musicService.seekTo(0);
            return;
        }
        int prev = (currentIndex - 1 + allSongs.size()) % allSongs.size();
        playSongAt(prev);
    }

    private void seekBy(int msec) {
        if (serviceBound) {
            int pos = musicService.getCurrentPosition() + msec;
            pos = Math.max(0, Math.min(pos, musicService.getDuration()));
            musicService.seekTo(pos);
        }
    }

    private void filterSongs(String query) {
        List<Song> filtered = new ArrayList<>();
        String lq = query.toLowerCase().trim();
        for (Song s : allSongs) {
            if (lq.isEmpty() || s.title.toLowerCase().contains(lq)
                    || s.artist.toLowerCase().contains(lq)) {
                filtered.add(s);
            }
        }
        playerView.setSongs(filtered, currentIndex);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        executor.shutdown();
    }

    @Override
    public void onBackPressed() {
        // Minimize to background instead of killing
        moveTaskToBack(true);
    }
}
