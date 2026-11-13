package cn.edu.sicnu.stu.zhangyuxin.seventh;

import android.net.Uri;

/**
 * 一首本地音乐。数据来自 ContentResolver 查询 MediaStore.Audio.Media。
 */
public class Song {

    public final long id;
    public final String title;
    public final String artist;
    public final long duration;
    public final Uri uri;

    public Song(long id, String title, String artist, long duration, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.duration = duration;
        this.uri = uri;
    }

    public String displayArtist() {
        if (artist == null || artist.trim().isEmpty() || "<unknown>".equalsIgnoreCase(artist)) {
            return "未知艺术家";
        }
        return artist;
    }

    public String durationText() {
        return format(duration);
    }

    public static String format(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
