package com.transsion.perfengine.demo;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe log list adapter.
 * Must call notifyDataSetChanged() on UI thread after appending.
 */
public class EventLogAdapter extends BaseAdapter {
    private static final int MAX_ENTRIES = 200;

    private final Context mContext;
    private final List<String> mEntries = new ArrayList<>();

    public EventLogAdapter(Context context) {
        mContext = context;
    }

    /** Append a log line. Must be called on UI thread. */
    public void append(String line) {
        if (mEntries.size() >= MAX_ENTRIES) {
            mEntries.remove(0);
        }
        mEntries.add(line);
        notifyDataSetChanged();
    }

    public void clear() {
        mEntries.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mEntries.size();
    }
    @Override
    public String getItem(int pos) {
        return mEntries.get(pos);
    }
    @Override
    public long getItemId(int pos) {
        return pos;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv;
        if (convertView == null) {
            tv = (TextView) LayoutInflater.from(mContext).inflate(R.layout.item_log, parent, false);
        } else {
            tv = (TextView) convertView;
        }
        String entry = mEntries.get(position);
        tv.setText(entry);
        // Color-code START / END lines
        if (entry.contains("[START]")) {
            tv.setTextColor(Color.parseColor("#1565C0")); // blue
        } else if (entry.contains("[ END ]")) {
            tv.setTextColor(Color.parseColor("#2E7D32")); // green
        } else {
            tv.setTextColor(Color.DKGRAY);
        }
        return tv;
    }
}
