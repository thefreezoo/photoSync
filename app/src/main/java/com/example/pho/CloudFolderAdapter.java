package com.example.pho;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class CloudFolderAdapter extends BaseAdapter {

    private Context context;
    private List<CloudFolder> folderList;
    private LayoutInflater inflater;

    public CloudFolderAdapter(Context context, List<CloudFolder> folderList) {
        this.context = context;
        this.folderList = folderList;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return folderList.size();
    }

    @Override
    public Object getItem(int position) {
        return folderList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_cloud_folder, parent, false);
            holder = new ViewHolder();
            holder.folderNameTextView = convertView.findViewById(R.id.folder_name);
            holder.photoCountTextView = convertView.findViewById(R.id.photo_count);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        CloudFolder folder = folderList.get(position);
        holder.folderNameTextView.setText(folder.getName());
        holder.photoCountTextView.setText(context.getString(R.string.photo_count, folder.getPhotoCount()));

        return convertView;
    }

    static class ViewHolder {
        TextView folderNameTextView;
        TextView photoCountTextView;
    }
}
