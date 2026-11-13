package cn.edu.sicnu.stu.zhangyuxin.seventh;

import android.Manifest;
import android.app.Activity;
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
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐列表界面：通过 ContentResolver 读取 MediaStore 中的音频，
 * 绑定 MusicService 获取 Binder，读取歌曲名/播放进度，并把控制命令交给服务。
 */
public class MainActivity extends Activity implements MusicService.Listener {

    private static final int REQUEST_PERMISSIONS = 100;

    private ListView listView;
    private SongAdapter adapter;
    private TextView emptyView;
    private TextView tvNowTitle;
    private TextView tvNowArtist;
    private TextView tvCurrent;
    private TextView tvTotal;
    private TextView tvCount;
    private ImageButton btnPlay;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnStop;
    private ImageButton btnRefresh;
    private SeekBar seekBar;

    private final List<Song> songs = new ArrayList<>();
    private MusicService service;
    private boolean bound;
    private boolean dragging;
    private volatile boolean polling;
    private Thread pollThread;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MusicService.MusicBinder) binder).getService();
            bound = true;
            service.setListener(MainActivity.this);
            if (!songs.isEmpty()) {
                service.setPlaylist(songs);
            }
            updateNowPlaying();
            startPolling();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupActions();

        adapter = new SongAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (service == null) {
                Toast.makeText(this, "服务尚未连接，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }
            service.play(position);
            adapter.setPlayingIndex(position);
        });

        Intent serviceIntent = new Intent(this, MusicService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        ensurePermissions();
    }

    private void bindViews() {
        listView = findViewById(R.id.list_songs);
        emptyView = findViewById(R.id.tv_empty);
        tvNowTitle = findViewById(R.id.tv_now_title);
        tvNowArtist = findViewById(R.id.tv_now_artist);
        tvCurrent = findViewById(R.id.tv_current);
        tvTotal = findViewById(R.id.tv_total);
        tvCount = findViewById(R.id.tv_count);
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnStop = findViewById(R.id.btn_stop);
        btnRefresh = findViewById(R.id.btn_refresh);
        seekBar = findViewById(R.id.seek_bar);
    }

    private void setupActions() {
        btnPlay.setOnClickListener(v -> {
            if (service != null) {
                service.toggle();
                btnPlay.postDelayed(this::refreshPlayIcon, 120);
            }
        });
        btnPrev.setOnClickListener(v -> {
            if (service != null) {
                service.previous();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (service != null) {
                service.next();
            }
        });
        btnStop.setOnClickListener(v -> {
            if (service != null) {
                service.stop();
                refreshPlayIcon();
            }
        });
        btnRefresh.setOnClickListener(v -> ensurePermissions());
        emptyView.setOnClickListener(v -> ensurePermissions());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrent.setText(Song.format(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                if (service != null) {
                    service.seekTo(bar.getProgress());
                }
            }
        });
    }

    // ---------------- 权限与音乐扫描 ----------------

    private String mediaPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void ensurePermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(mediaPermission()) != PackageManager.PERMISSION_GRANTED) {
            missing.add(mediaPermission());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (missing.isEmpty()) {
            loadSongs();
        } else {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        if (checkSelfPermission(mediaPermission()) == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText("未获得读取音乐权限\n点击此处重新授权");
        }
    }

    private void loadSongs() {
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText("正在扫描本机音乐…");
        new Thread(() -> {
            final List<Song> result = querySongs();
            runOnUiThread(() -> {
                songs.clear();
                songs.addAll(result);
                adapter.setSongs(songs);
                tvCount.setText(songs.size() + " 首");
                if (songs.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    emptyView.setText("未找到音乐文件\n请向设备添加音乐后点击右上角刷新");
                } else {
                    emptyView.setVisibility(View.GONE);
                }
                if (service != null) {
                    service.setPlaylist(songs);
                }
                updateNowPlaying();
            });
        }, "music-scan").start();
    }

    private List<Song> querySongs() {
        List<Song> result = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(
                collection, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor == null) {
                return result;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                long duration = cursor.getLong(durationColumn);
                if (title == null || title.trim().isEmpty()) {
                    title = "未知歌曲";
                }
                Uri uri = ContentUris.withAppendedId(collection, id);
                result.add(new Song(id, title, artist, duration, uri));
            }
        } catch (SecurityException e) {
            runOnUiThread(() -> Toast.makeText(this,
                    "缺少读取音乐的权限", Toast.LENGTH_SHORT).show());
        }
        return result;
    }

    // ---------------- 进度轮询（多线程） ----------------

    private void startPolling() {
        if (pollThread != null && pollThread.isAlive()) {
            return;
        }
        polling = true;
        pollThread = new Thread(() -> {
            while (polling) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
                final MusicService current = service;
                if (current == null) {
                    continue;
                }
                final int position = current.getPosition();
                final int duration = current.getDuration();
                final boolean playing = current.isPlaying();
                runOnUiThread(() -> {
                    if (!dragging && duration > 0) {
                        if (seekBar.getMax() != duration) {
                            seekBar.setMax(duration);
                        }
                        seekBar.setProgress(position);
                    }
                    tvCurrent.setText(Song.format(position));
                    tvTotal.setText(Song.format(duration));
                    setPlayIcon(playing);
                    Song song = current.getCurrentSong();
                    if (song != null) {
                        tvNowTitle.setText(song.title);
                        tvNowArtist.setText(song.displayArtist());
                        adapter.setPlayingIndex(current.getCurrentIndex());
                    }
                });
            }
        }, "seekbar-progress");
        pollThread.start();
    }

    private void stopPolling() {
        polling = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    // ---------------- 服务回调与界面刷新 ----------------

    @Override
    public void onSongChanged(Song song) {
        runOnUiThread(() -> {
            tvNowTitle.setText(song.title);
            tvNowArtist.setText(song.displayArtist());
            if (service != null) {
                adapter.setPlayingIndex(service.getCurrentIndex());
            }
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean playing) {
        runOnUiThread(() -> setPlayIcon(playing));
    }

    private void updateNowPlaying() {
        Song song = service == null ? null : service.getCurrentSong();
        if (song == null) {
            tvNowTitle.setText("未选择歌曲");
            tvNowArtist.setText("从下方列表选择一首歌曲开始播放");
            tvCurrent.setText("00:00");
            tvTotal.setText("00:00");
            seekBar.setProgress(0);
            setPlayIcon(false);
        } else {
            tvNowTitle.setText(song.title);
            tvNowArtist.setText(song.displayArtist());
            setPlayIcon(service.isPlaying());
        }
    }

    private void refreshPlayIcon() {
        setPlayIcon(service != null && service.isPlaying());
    }

    private void setPlayIcon(boolean playing) {
        btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlay.setContentDescription(playing ? "暂停" : "播放");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (bound) {
            startPolling();
        }
    }

    @Override
    protected void onStop() {
        stopPolling();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        if (bound) {
            if (service != null) {
                service.setListener(null);
            }
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }
}
