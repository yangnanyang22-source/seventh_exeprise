package com.yn.musicplayerlab7;

import android.net.Uri;

public final class Song {

    private final long id;
    private final String title;
    private final String artist;
    private final long durationMs;
    private final Uri uri;

    public Song(long id, String title, String artist, long durationMs, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.durationMs = durationMs;
        this.uri = uri;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Uri getUri() {
        return uri;
    }
}
