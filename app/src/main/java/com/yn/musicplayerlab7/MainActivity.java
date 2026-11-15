package com.yn.musicplayerlab7;

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
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService progressExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final List<Song> currentSongs = new ArrayList<>();

    private ListView songListView;
    private TextView songNameView;
    private TextView currentTimeView;
    private TextView totalTimeView;
    private SeekBar progressBar;
    private Button playPauseButton;
    private SongAdapter songAdapter;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ScheduledFuture<?> progressUpdateTask;

    private volatile MusicService.MusicBinder musicBinder;
    private boolean serviceBound;
    private boolean userIsSeeking;
    private int pendingPlayIndex = -1;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicBinder = (MusicService.MusicBinder) service;
            musicBinder.setPlaylist(new ArrayList<>(currentSongs));
            if (pendingPlayIndex >= 0 && pendingPlayIndex < currentSongs.size()) {
                musicBinder.playAt(pendingPlayIndex);
                pendingPlayIndex = -1;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        songListView = findViewById(R.id.lvSongs);
        songNameView = findViewById(R.id.tvSongName);
        currentTimeView = findViewById(R.id.tvCurrentTime);
        totalTimeView = findViewById(R.id.tvTotalTime);
        progressBar = findViewById(R.id.seekBar);
        playPauseButton = findViewById(R.id.btnPlayPause);

        songAdapter = new SongAdapter(this);
        songListView.setAdapter(songAdapter);
        songListView.setOnItemClickListener((parent, view, position, id) ->
                playSongAt(position));

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        loadSongs();
                        requestNotificationPermission();
                    } else {
                        Toast.makeText(
                                this,
                                R.string.audio_permission_denied,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        Toast.makeText(
                                this,
                                R.string.notification_permission_denied,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left + v.getPaddingLeft(),
                    systemBars.top + v.getPaddingTop(),
                    systemBars.right + v.getPaddingRight(),
                    systemBars.bottom + v.getPaddingBottom()
            );
            return insets;
        });

        setUpPlaybackControls();
        requestAudioPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        serviceBound = bindService(
                new Intent(this, MusicService.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );
        startProgressUpdates();
    }

    @Override
    protected void onStop() {
        stopProgressUpdates();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        musicBinder = null;
        super.onStop();
    }

    private void setUpPlaybackControls() {
        findViewById(R.id.btnPrevious).setOnClickListener(view -> {
            if (musicBinder != null) {
                startPlaybackService();
                musicBinder.playPrevious();
            }
        });

        playPauseButton.setOnClickListener(view -> {
            if (musicBinder != null) {
                startPlaybackService();
                musicBinder.togglePlayPause();
            }
        });

        findViewById(R.id.btnStop).setOnClickListener(view -> {
            if (musicBinder != null) {
                musicBinder.stop();
                currentTimeView.setText(R.string.time_placeholder);
                progressBar.setProgress(0);
                playPauseButton.setText(R.string.play);
            }
        });

        findViewById(R.id.btnNext).setOnClickListener(view -> {
            if (musicBinder != null) {
                startPlaybackService();
                musicBinder.playNext();
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeView.setText(
                            DateUtils.formatElapsedTime(progress / 1000L)
                    );
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (musicBinder != null) {
                    musicBinder.seekTo(seekBar.getProgress());
                }
                userIsSeeking = false;
            }
        });
    }

    private void playSongAt(int position) {
        if (position < 0 || position >= currentSongs.size()) {
            return;
        }

        Song song = currentSongs.get(position);
        songNameView.setText(song.getTitle());
        currentTimeView.setText(R.string.time_placeholder);
        totalTimeView.setText(DateUtils.formatElapsedTime(song.getDurationMs() / 1000));
        progressBar.setMax((int) Math.max(song.getDurationMs(), 1));
        progressBar.setProgress(0);

        if (musicBinder != null) {
            startPlaybackService();
            musicBinder.playAt(position);
        } else {
            pendingPlayIndex = position;
        }
    }

    private void startProgressUpdates() {
        if (progressUpdateTask != null && !progressUpdateTask.isDone()) {
            return;
        }
        progressUpdateTask = progressExecutor.scheduleAtFixedRate(() -> {
            MusicService.MusicBinder binder = musicBinder;
            if (binder == null) {
                return;
            }

            Song song = binder.getCurrentSong();
            if (song == null) {
                return;
            }

            int position = binder.getCurrentPosition();
            int mediaDuration = binder.getDuration();
            int duration = mediaDuration > 0
                    ? mediaDuration
                    : (int) Math.max(song.getDurationMs(), 1);
            boolean playing = binder.isPlaying();

            runOnUiThread(() -> {
                if (isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed())) {
                    return;
                }

                songNameView.setText(song.getTitle());
                totalTimeView.setText(DateUtils.formatElapsedTime(duration / 1000L));
                progressBar.setMax(duration);
                if (!userIsSeeking) {
                    progressBar.setProgress(position);
                    currentTimeView.setText(
                            DateUtils.formatElapsedTime(position / 1000L)
                    );
                }
                playPauseButton.setText(playing ? R.string.pause : R.string.play);
            });
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    private void stopProgressUpdates() {
        if (progressUpdateTask != null) {
            progressUpdateTask.cancel(true);
            progressUpdateTask = null;
        }
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
            requestNotificationPermission();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void startPlaybackService() {
        ContextCompat.startForegroundService(
                this,
                new Intent(this, MusicService.class)
        );
    }

    private void loadSongs() {
        databaseExecutor.execute(() -> {
            List<Song> songs = new ArrayList<>();
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            String sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
            )) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int displayNameColumn = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.DISPLAY_NAME
                    );
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int durationColumn = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.DURATION
                    );

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        String title = cursor.getString(titleColumn);
                        String displayName = cursor.getString(displayNameColumn);
                        String artist = cursor.getString(artistColumn);
                        long duration = cursor.getLong(durationColumn);

                        if (TextUtils.isEmpty(title)) {
                            title = TextUtils.isEmpty(displayName)
                                    ? getString(R.string.unknown_song)
                                    : displayName;
                        }
                        if (TextUtils.isEmpty(artist)
                                || MediaStore.UNKNOWN_STRING.equals(artist)) {
                            artist = getString(R.string.unknown_artist);
                        }

                        Uri uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                        );
                        songs.add(new Song(id, title, artist, duration, uri));
                    }
                }
            } catch (SecurityException exception) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        R.string.audio_permission_denied,
                        Toast.LENGTH_LONG
                ).show());
                return;
            }

            runOnUiThread(() -> {
                currentSongs.clear();
                currentSongs.addAll(songs);
                songAdapter.setSongs(songs);
                if (musicBinder != null) {
                    musicBinder.setPlaylist(new ArrayList<>(songs));
                }
                if (songs.isEmpty()) {
                    Toast.makeText(this, R.string.no_music_found, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        stopProgressUpdates();
        progressExecutor.shutdownNow();
        databaseExecutor.shutdownNow();
        super.onDestroy();
    }
}
