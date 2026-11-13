package cn.edu.sicnu.stu.zhangyuxin.seventh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 音乐播放服务：MediaPlayer 的全部控制逻辑、播放列表与通知都在服务中完成，
 * Activity 只通过 Binder 读取歌曲信息、播放进度并下发控制命令。
 */
public class MusicService extends Service {

    public interface Listener {
        void onSongChanged(Song song);

        void onPlaybackStateChanged(boolean playing);
    }

    public static final String ACTION_PREV = "cn.edu.sicnu.stu.zhangyuxin.seventh.PREV";
    public static final String ACTION_NEXT = "cn.edu.sicnu.stu.zhangyuxin.seventh.NEXT";
    public static final String ACTION_TOGGLE = "cn.edu.sicnu.stu.zhangyuxin.seventh.TOGGLE";
    public static final String ACTION_STOP = "cn.edu.sicnu.stu.zhangyuxin.seventh.STOP";

    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new MusicBinder();
    private final List<Song> playlist = new ArrayList<>();

    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean prepared;
    private Listener listener;

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        initPlayer();
        // 立即进入前台，避免后台播放被系统回收；未播放时显示“准备就绪”。
        startForeground(NOTIFICATION_ID, buildNotification("音乐播放器", "准备就绪，选择歌曲开始播放", false));
    }

    private void initPlayer() {
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setOnPreparedListener(mp -> {
            prepared = true;
            mp.start();
            notifySong();
            updateNotification();
        });
        player.setOnCompletionListener(mp -> next());
        player.setOnErrorListener((mp, what, extra) -> {
            prepared = false;
            updateNotification("播放失败", "该音频暂时无法播放", false);
            return true;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action != null) {
            switch (action) {
                case ACTION_PREV:
                    previous();
                    break;
                case ACTION_NEXT:
                    next();
                    break;
                case ACTION_TOGGLE:
                    toggle();
                    break;
                case ACTION_STOP:
                    stop();
                    break;
                default:
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ---------------- 供 Activity 通过 Binder 调用的播放控制 ----------------

    public void setPlaylist(List<Song> songs) {
        playlist.clear();
        if (songs != null) {
            playlist.addAll(songs);
        }
        if (playlist.isEmpty()) {
            currentIndex = -1;
        } else if (currentIndex >= playlist.size()) {
            currentIndex = 0;
        }
    }

    public List<Song> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Song getCurrentSong() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) {
            return null;
        }
        return playlist.get(currentIndex);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void play(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }
        currentIndex = index;
        Song song = playlist.get(index);
        prepared = false;
        try {
            player.reset();
            player.setDataSource(getApplicationContext(), song.uri);
            player.prepareAsync();
            notifySong();
            updateNotification("正在加载", song.title + " · " + song.displayArtist(), true);
        } catch (IOException | IllegalStateException e) {
            updateNotification("播放失败", song.title + " 无法打开", false);
        }
    }

    public void toggle() {
        if (player.isPlaying()) {
            pause();
        } else if (prepared) {
            player.start();
            notifySong();
            updateNotification();
        } else if (!playlist.isEmpty()) {
            play(currentIndex < 0 ? 0 : currentIndex);
        }
    }

    public void pause() {
        if (player.isPlaying()) {
            player.pause();
            notifyPlaybackState(false);
            updateNotification();
        }
    }

    public void stop() {
        if (player != null) {
            try {
                player.reset();
            } catch (IllegalStateException ignored) {
            }
        }
        prepared = false;
        notifyPlaybackState(false);
        updateNotification("已停止", "点击播放按钮重新开始", false);
    }

    public void next() {
        if (playlist.isEmpty()) {
            return;
        }
        play((currentIndex + 1) % playlist.size());
    }

    public void previous() {
        if (playlist.isEmpty()) {
            return;
        }
        int index = currentIndex <= 0 ? playlist.size() - 1 : currentIndex - 1;
        play(index);
    }

    public void seekTo(int position) {
        if (prepared && player != null) {
            player.seekTo(position);
            updateNotification();
        }
    }

    public boolean isPlaying() {
        return player != null && prepared && player.isPlaying();
    }

    public boolean isPrepared() {
        return prepared;
    }

    public int getPosition() {
        if (prepared) {
            try {
                return player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
            }
        }
        return 0;
    }

    public int getDuration() {
        if (prepared) {
            try {
                return player.getDuration();
            } catch (IllegalStateException ignored) {
            }
        }
        Song song = getCurrentSong();
        return song == null ? 0 : (int) song.duration;
    }

    // ---------------- 通知 ----------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示当前播放的歌曲并提供播放控制");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(String title, String text, boolean playing) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(getColor(R.color.accent))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_skip_previous),
                        "上一首", serviceIntent(ACTION_PREV, 11)).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this,
                                playing ? R.drawable.ic_pause : R.drawable.ic_play),
                        playing ? "暂停" : "播放",
                        serviceIntent(ACTION_TOGGLE, 12)).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_skip_next),
                        "下一首", serviceIntent(ACTION_NEXT, 13)).build());
        return builder.build();
    }

    private void updateNotification() {
        Song song = getCurrentSong();
        if (song == null) {
            updateNotification("音乐播放器", "准备就绪，选择歌曲开始播放", false);
        } else if (isPlaying()) {
            updateNotification("正在播放 · " + song.title, song.displayArtist(), true);
        } else if (prepared) {
            updateNotification("已暂停 · " + song.title, song.displayArtist(), false);
        } else {
            updateNotification("已停止", "点击播放按钮重新开始", false);
        }
    }

    private void updateNotification(String title, String text, boolean playing) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(title, text, playing));
    }

    private void notifySong() {
        Song song = getCurrentSong();
        if (listener != null && song != null) {
            listener.onSongChanged(song);
        }
        notifyPlaybackState(isPlaying());
    }

    private void notifyPlaybackState(boolean playing) {
        if (listener != null) {
            listener.onPlaybackStateChanged(playing);
        }
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
