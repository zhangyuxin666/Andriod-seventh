package cn.edu.sicnu.stu.zhangyuxin.seventh;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐列表适配器，正在播放的行用强调色标记。
 */
public class SongAdapter extends BaseAdapter {

    private final Context context;
    private final List<Song> songs = new ArrayList<>();
    private int playingIndex = -1;

    public SongAdapter(Context context) {
        this.context = context;
    }

    public void setSongs(List<Song> data) {
        songs.clear();
        if (data != null) {
            songs.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void setPlayingIndex(int index) {
        if (playingIndex == index) {
            return;
        }
        playingIndex = index;
        notifyDataSetChanged();
    }

    public int getPlayingIndex() {
        return playingIndex;
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Song getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return songs.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
            holder = new ViewHolder();
            holder.index = convertView.findViewById(R.id.tv_index);
            holder.title = convertView.findViewById(R.id.tv_song_title);
            holder.artist = convertView.findViewById(R.id.tv_song_artist);
            holder.duration = convertView.findViewById(R.id.tv_song_duration);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Song song = songs.get(position);
        boolean playing = position == playingIndex;
        holder.index.setText(playing ? "▶" : String.valueOf(position + 1));
        holder.title.setText(song.title);
        holder.artist.setText(song.displayArtist());
        holder.duration.setText(song.durationText());

        int accent = context.getColor(R.color.accent);
        int primary = context.getColor(R.color.text_primary);
        int secondary = context.getColor(R.color.text_secondary);
        holder.index.setTextColor(playing ? accent : secondary);
        holder.title.setTextColor(playing ? accent : primary);
        holder.artist.setTextColor(secondary);
        convertView.setBackgroundColor(playing
                ? context.getColor(R.color.row_playing)
                : context.getColor(android.R.color.transparent));
        return convertView;
    }

    private static class ViewHolder {
        TextView index;
        TextView title;
        TextView artist;
        TextView duration;
    }
}
