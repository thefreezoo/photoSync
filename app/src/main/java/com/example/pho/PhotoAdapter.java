package com.example.pho;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PhotoAdapter extends BaseAdapter {

    private Context context;
    private List<Photo> photoList;

    public PhotoAdapter(Context context, List<Photo> photoList) {
        this.context = context;
        this.photoList = photoList;
    }

    @Override
    public int getCount() {
        return photoList.size();
    }

    @Override
    public Object getItem(int position) {
        return photoList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return photoList.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        }

        ImageView imageView = convertView.findViewById(R.id.photo_image);
        ImageView syncedIcon = convertView.findViewById(R.id.synced_icon);
        View selectedOverlay = convertView.findViewById(R.id.selected_overlay);
        Photo photo = photoList.get(position);

        // Check if it's a cloud photo (path starts with http or https) or local photo
        if (photo.getPath() != null && (photo.getPath().startsWith("http://") || photo.getPath().startsWith("https://"))) {
            // It's a cloud photo, load from URL with authentication
            // Get WebDAV credentials
            android.content.SharedPreferences preferences = context.getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
            String username = preferences.getString("webdav_username", "");
            String password = preferences.getString("webdav_password", "");
            
            // Create Glide request with authentication
            Glide.with(context)
                    .load(new com.bumptech.glide.load.model.GlideUrl(
                            photo.getPath(),
                            new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                    .addHeader("Authorization", "Basic " + android.util.Base64.encodeToString((username + ":" + password).getBytes(), android.util.Base64.NO_WRAP))
                                    .build()
                    ))
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_gallery) // Show placeholder if image not found
                    .placeholder(android.R.drawable.ic_menu_gallery) // Show placeholder while loading
                    .into(imageView);
        } else {
            // It's a local photo, load from content URI
            Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(photo.getId()));
            Glide.with(context)
                    .load(uri)
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_gallery) // Show placeholder if image not found
                    .placeholder(android.R.drawable.ic_menu_gallery) // Show placeholder while loading
                    .into(imageView);
        }

        // Show synced icon if photo is synced
        if (photo.isSynced()) {
            syncedIcon.setVisibility(View.VISIBLE);
        } else {
            syncedIcon.setVisibility(View.GONE);
        }

        // Show selected overlay if photo is selected
        if (photo.isSelected()) {
            selectedOverlay.setVisibility(View.VISIBLE);
        } else {
            selectedOverlay.setVisibility(View.GONE);
        }

        return convertView;
    }
}
