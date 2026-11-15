package com.yn.musicplayerlab7;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class SongAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<Song> songs = new ArrayList<>();

    public SongAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setSongs(List<Song> newSongs) {
        songs.clear();
        songs.addAll(newSongs);
        notifyDataSetChanged();
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
        return songs.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_song, parent, false);
            holder = new ViewHolder(
                    convertView.findViewById(R.id.tvItemTitle),
                    convertView.findViewById(R.id.tvItemSubtitle)
            );
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Song song = getItem(position);
        String duration = DateUtils.formatElapsedTime(song.getDurationMs() / 1000);
        holder.title.setText(song.getTitle());
        holder.subtitle.setText(song.getArtist() + " · " + duration);
        return convertView;
    }

    private static final class ViewHolder {
        private final TextView title;
        private final TextView subtitle;

        private ViewHolder(TextView title, TextView subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}
