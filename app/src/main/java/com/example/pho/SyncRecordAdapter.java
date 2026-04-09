package com.example.pho;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SyncRecordAdapter extends BaseAdapter {

    private Context context;
    private List<SyncRecord> syncRecordList;
    private SimpleDateFormat dateFormat;

    public SyncRecordAdapter(Context context, List<SyncRecord> syncRecordList) {
        this.context = context;
        this.syncRecordList = syncRecordList;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public int getCount() {
        return syncRecordList.size();
    }

    @Override
    public Object getItem(int position) {
        return syncRecordList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return syncRecordList.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_sync_record, parent, false);
        }

        ImageView statusIcon = convertView.findViewById(R.id.status_icon);
        TextView photoNameText = convertView.findViewById(R.id.photo_name);
        TextView syncTimeText = convertView.findViewById(R.id.sync_time);

        SyncRecord record = syncRecordList.get(position);

        if (record.isSuccess()) {
            statusIcon.setImageResource(android.R.drawable.checkbox_on_background);
            statusIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_green_dark));
        } else {
            statusIcon.setImageResource(android.R.drawable.ic_delete);
            statusIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_red_dark));
        }

        photoNameText.setText(record.getPhotoName());
        syncTimeText.setText(context.getString(R.string.synced_at, dateFormat.format(new Date(record.getSyncedAt()))));

        return convertView;
    }
}
