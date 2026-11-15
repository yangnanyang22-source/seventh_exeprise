package com.yn.musicplayerlab7;

import android.app.Service;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new MusicBinder();
    private final List<Song> playlist = new ArrayList<>();

    private MediaPlayer mediaPlayer;
    private int currentIndex = -1;
    private boolean prepared = false;
    private boolean playWhenPrepared = false;

    public final class MusicBinder extends Binder {

        public void setPlaylist(List<Song> songs) {
            MusicService.this.setPlaylist(songs);
        }

        public void playAt(int index) {
            MusicService.this.playAt(index);
        }

        public void togglePlayPause() {
            MusicService.this.togglePlayPause();
        }

        public void stop() {
            MusicService.this.stopPlayback();
        }

        public void playPrevious() {
            MusicService.this.playPrevious();
        }

        public void playNext() {
            MusicService.this.playNext();
        }

        public void seekTo(int positionMs) {
            MusicService.this.seekTo(positionMs);
        }

        public boolean isPlaying() {
            return MusicService.this.isPlaying();
        }

        public int getCurrentPosition() {
            return MusicService.this.getCurrentPosition();
        }

        public int getDuration() {
            return MusicService.this.getDuration();
        }

        @Nullable
        public Song getCurrentSong() {
            return MusicService.this.getCurrentSong();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureNotificationChannel();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(attributes);
        mediaPlayer.setOnPreparedListener(player -> {
            prepared = true;
            if (playWhenPrepared) {
                player.start();
            }
            playWhenPrepared = false;
            showPlaybackNotification(getCurrentSong());
        });
        mediaPlayer.setOnCompletionListener(player -> playNext());
        mediaPlayer.setOnErrorListener((player, what, extra) -> {
            prepared = false;
            playWhenPrepared = false;
            Toast.makeText(this, R.string.playback_error, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildPlaybackNotification(getCurrentSong()));
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void setPlaylist(List<Song> songs) {
        long currentSongId = getCurrentSong() == null ? -1 : getCurrentSong().getId();
        playlist.clear();
        playlist.addAll(songs);

        if (currentSongId == -1 || playlist.isEmpty()) {
            if (playlist.isEmpty()) {
                currentIndex = -1;
            }
            return;
        }

        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).getId() == currentSongId) {
                currentIndex = i;
                return;
            }
        }
        currentIndex = -1;
    }

    private void playAt(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        currentIndex = index;
        prepared = false;
        playWhenPrepared = true;
        showPlaybackNotification(playlist.get(index));
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(this, playlist.get(index).getUri());
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException exception) {
            playWhenPrepared = false;
            Toast.makeText(this, R.string.playback_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayPause() {
        if (playlist.isEmpty()) {
            return;
        }

        if (!prepared) {
            int indexToPlay = currentIndex >= 0 ? currentIndex : 0;
            playAt(indexToPlay);
        } else if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.start();
        }
        showPlaybackNotification(getCurrentSong());
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (prepared || mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            } catch (IllegalStateException ignored) {
                mediaPlayer.reset();
            }
        }
        prepared = false;
        playWhenPrepared = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void playPrevious() {
        if (playlist.isEmpty()) {
            return;
        }
        int previousIndex = currentIndex <= 0 ? playlist.size() - 1 : currentIndex - 1;
        playAt(previousIndex);
    }

    private void playNext() {
        if (playlist.isEmpty()) {
            return;
        }
        int nextIndex = currentIndex < 0 || currentIndex >= playlist.size() - 1
                ? 0
                : currentIndex + 1;
        playAt(nextIndex);
    }

    private void seekTo(int positionMs) {
        if (prepared) {
            mediaPlayer.seekTo(Math.max(positionMs, 0));
        }
    }

    private boolean isPlaying() {
        try {
            return prepared && mediaPlayer.isPlaying();
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private int getCurrentPosition() {
        try {
            return prepared ? mediaPlayer.getCurrentPosition() : 0;
        } catch (IllegalStateException exception) {
            return 0;
        }
    }

    private int getDuration() {
        try {
            return prepared ? mediaPlayer.getDuration() : 0;
        } catch (IllegalStateException exception) {
            return 0;
        }
    }

    @Nullable
    private Song getCurrentSong() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) {
            return null;
        }
        return playlist.get(currentIndex);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildPlaybackNotification(@Nullable Song song) {
        String title = song == null ? getString(R.string.app_name) : song.getTitle();
        String text = song == null
                ? getString(R.string.notification_ready)
                : song.getArtist();

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void showPlaybackNotification(@Nullable Song song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildPlaybackNotification(song));
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
