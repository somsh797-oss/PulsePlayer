package com.pulse.player;

import android.net.Uri;

public class Song {
    public long id;
    public String title;
    public String artist;
    public String album;
    public long duration; // ms
    public Uri uri;
    public Uri albumArtUri;

    public Song(long id, String title, String artist, String album,
                long duration, Uri uri, Uri albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.uri = uri;
        this.albumArtUri = albumArtUri;
    }

    public String getFormattedDuration() {
        long secs = duration / 1000;
        long m = secs / 60;
        long s = secs % 60;
        return String.format("%d:%02d", m, s);
    }
}
