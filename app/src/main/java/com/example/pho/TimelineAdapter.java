package com.example.pho;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DATE = 0;
    private static final int TYPE_PHOTO_GROUP = 1;

    private Context context;
    private List<Object> timelineItems;
    private OnPhotoClickListener onPhotoClickListener;

    public interface OnPhotoClickListener {
        void onPhotoClick(int position);
        void onPhotoLongClick(int position);
    }

    public TimelineAdapter(Context context, List<Photo> photoList, OnPhotoClickListener listener) {
        this.context = context;
        this.onPhotoClickListener = listener;
        this.timelineItems = groupPhotosByDate(photoList);
    }

    private List<Object> groupPhotosByDate(List<Photo> photoList) {
        List<Object> items = new ArrayList<>();
        String currentDate = "";
        List<Photo> sameDatePhotos = new ArrayList<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());

        for (Photo photo : photoList) {
            String photoDate = dateFormat.format(new Date(photo.getDateAdded() * 1000));
            if (!photoDate.equals(currentDate)) {
                if (!sameDatePhotos.isEmpty()) {
                    // Add the group of photos for the previous date
                    items.add(sameDatePhotos);
                    sameDatePhotos = new ArrayList<>();
                }
                currentDate = photoDate;
                items.add(currentDate);
            }
            sameDatePhotos.add(photo);
        }

        // Add the last group of photos
        if (!sameDatePhotos.isEmpty()) {
            items.add(sameDatePhotos);
        }

        return items;
    }

    @Override
    public int getItemViewType(int position) {
        if (timelineItems.get(position) instanceof String) {
            return TYPE_DATE;
        } else {
            return TYPE_PHOTO_GROUP;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_photo_group, parent, false);
            return new PhotoGroupViewHolder(view, onPhotoClickListener, timelineItems);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DateHeaderViewHolder) {
            String date = (String) timelineItems.get(position);
            ((DateHeaderViewHolder) holder).dateTextView.setText(date);
        } else if (holder instanceof PhotoGroupViewHolder) {
            List<Photo> photoGroup = (List<Photo>) timelineItems.get(position);
            PhotoGroupViewHolder photoGroupViewHolder = (PhotoGroupViewHolder) holder;
            
            // Set up the horizontal recycler view
            photoGroupViewHolder.setPhotos(photoGroup);
        }
    }

    @Override
    public int getItemCount() {
        return timelineItems.size();
    }

    public void updatePhotos(List<Photo> photoList) {
        System.out.println("=== TimelineAdapter.updatePhotos() started ===");
        System.out.println("Number of photos received: " + photoList.size());
        this.timelineItems = groupPhotosByDate(photoList);
        System.out.println("Number of timeline items after grouping: " + timelineItems.size());
        // Log the timeline items
        for (int i = 0; i < timelineItems.size(); i++) {
            Object item = timelineItems.get(i);
            if (item instanceof String) {
                System.out.println("Timeline item " + i + ": Date header - " + item);
            } else if (item instanceof List) {
                List<Photo> photos = (List<Photo>) item;
                System.out.println("Timeline item " + i + ": Photo group with " + photos.size() + " photos");
            }
        }
        System.out.println("Calling notifyDataSetChanged()");
        // Post the notifyDataSetChanged() to the next frame to avoid IllegalStateException in scroll callback
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
                System.out.println("=== TimelineAdapter.updatePhotos() completed ===");
            }
        });
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView dateTextView;

        DateHeaderViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.date_text);
        }
    }

    static class PhotoGroupViewHolder extends RecyclerView.ViewHolder {
        RecyclerView horizontalRecyclerView;
        PhotoItemAdapter photoItemAdapter;
        private OnPhotoClickListener onPhotoClickListener;
        private List<Object> timelineItems;

        PhotoGroupViewHolder(View itemView, OnPhotoClickListener listener, List<Object> items) {
            super(itemView);
            horizontalRecyclerView = itemView.findViewById(R.id.horizontal_recycler);
            this.onPhotoClickListener = listener;
            this.timelineItems = items;
            // Set up grid layout manager with 3 columns
            final androidx.recyclerview.widget.GridLayoutManager layoutManager = new androidx.recyclerview.widget.GridLayoutManager(
                    itemView.getContext(),
                    3,
                    androidx.recyclerview.widget.GridLayoutManager.VERTICAL,
                    false
            );
            horizontalRecyclerView.setLayoutManager(layoutManager);
            // Add spacing between items
            horizontalRecyclerView.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(android.graphics.Rect outRect, android.view.View view, androidx.recyclerview.widget.RecyclerView parent, androidx.recyclerview.widget.RecyclerView.State state) {
                    int horizontalSpacing = 4; // Horizontal spacing between items
                    int verticalSpacing = 8;   // Vertical spacing between items
                    
                    // For grid layout with 3 columns
                    int position = parent.getChildAdapterPosition(view);
                    int spanCount = 3;
                    
                    // Calculate column and row
                    int column = position % spanCount;
                    int row = position / spanCount;
                    
                    // Set spacing for all sides
                    outRect.left = horizontalSpacing;
                    outRect.right = horizontalSpacing;
                    outRect.top = verticalSpacing;
                    outRect.bottom = verticalSpacing;
                }
            });
        }

        void setPhotos(List<Photo> photos) {
            if (photoItemAdapter == null) {
                photoItemAdapter = new PhotoItemAdapter(itemView.getContext(), photos, new PhotoItemAdapter.OnPhotoItemClickListener() {
                    @Override
                    public void onPhotoClick(int position) {
                        if (onPhotoClickListener != null) {
                            // Find the actual photo position in the original list
                            int photoPosition = 0;
                            int currentGroupPosition = getAdapterPosition();
                            for (int i = 0; i < currentGroupPosition; i++) {
                                if (timelineItems.get(i) instanceof List) {
                                    List<Photo> previousGroup = (List<Photo>) timelineItems.get(i);
                                    photoPosition += previousGroup.size();
                                }
                            }
                            // Add the position within the current group
                            photoPosition += position;
                            onPhotoClickListener.onPhotoClick(photoPosition);
                        }
                    }

                    @Override
                    public void onPhotoLongClick(int position) {
                        if (onPhotoClickListener != null) {
                            // Find the actual photo position in the original list
                            int photoPosition = 0;
                            int currentGroupPosition = getAdapterPosition();
                            for (int i = 0; i < currentGroupPosition; i++) {
                                if (timelineItems.get(i) instanceof List) {
                                    List<Photo> previousGroup = (List<Photo>) timelineItems.get(i);
                                    photoPosition += previousGroup.size();
                                }
                            }
                            // Add the position within the current group
                            photoPosition += position;
                            onPhotoClickListener.onPhotoLongClick(photoPosition);
                        }
                    }
                });
                horizontalRecyclerView.setAdapter(photoItemAdapter);
            } else {
                photoItemAdapter.updatePhotos(photos);
            }
        }
    }

    // Adapter for individual photos in the horizontal recycler view
    private static class PhotoItemAdapter extends RecyclerView.Adapter<PhotoItemAdapter.PhotoItemViewHolder> {
        private Context context;
        private List<Photo> photos;
        private OnPhotoItemClickListener onPhotoItemClickListener;

        interface OnPhotoItemClickListener {
            void onPhotoClick(int position);
            void onPhotoLongClick(int position);
        }

        PhotoItemAdapter(Context context, List<Photo> photos, OnPhotoItemClickListener listener) {
            this.context = context;
            this.photos = photos;
            this.onPhotoItemClickListener = listener;
        }

        void updatePhotos(List<Photo> photos) {
            this.photos = photos;
            notifyDataSetChanged();
        }

        @Override
        public PhotoItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_timeline_photo_item, parent, false);
            return new PhotoItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoItemViewHolder holder, int position) {
            Photo photo = photos.get(position);

            // Check if it's a cloud photo (path starts with http or https) or local photo
            if (photo.getPath() != null && (photo.getPath().startsWith("http://") || photo.getPath().startsWith("https://"))) {
                // It's a cloud photo, load from URL with authentication
                // Get WebDAV credentials
                android.content.SharedPreferences preferences = context.getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
                String username = preferences.getString("webdav_username", "");
                String password = preferences.getString("webdav_password", "");
                
                // Create Glide request with authentication and optimized caching
                if (photo.isSynced()) {
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
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // Cache both original and resized images
                            .skipMemoryCache(false) // Use memory cache
                            .into(holder.photoImageView);
                } else {
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
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // Cache both original and resized images
                            .skipMemoryCache(false) // Use memory cache
                            .into(holder.photoImageViewUnsynced);
                }
            } else {
                // It's a local media, load from content URI
                if (photo.isVideo()) {
                    // It's a video, load video thumbnail
                    Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(photo.getId()));
                    if (photo.isSynced()) {
                        Glide.with(context)
                                .load(uri)
                                .centerCrop()
                                .error(android.R.drawable.ic_media_play)
                                .placeholder(android.R.drawable.ic_media_play)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .skipMemoryCache(false)
                                .into(holder.photoImageView);
                    } else {
                        Glide.with(context)
                                .load(uri)
                                .centerCrop()
                                .error(android.R.drawable.ic_media_play)
                                .placeholder(android.R.drawable.ic_media_play)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .skipMemoryCache(false)
                                .into(holder.photoImageViewUnsynced);
                    }
                } else {
                    // It's a photo, load from content URI
                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(photo.getId()));
                    if (photo.isSynced()) {
                        Glide.with(context)
                                .load(uri)
                                .centerCrop()
                                .error(android.R.drawable.ic_menu_gallery) // Show placeholder if image not found
                                .placeholder(android.R.drawable.ic_menu_gallery) // Show placeholder while loading
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // Cache both original and resized images
                                .skipMemoryCache(false) // Use memory cache
                                .into(holder.photoImageView);
                    } else {
                        Glide.with(context)
                                .load(uri)
                                .centerCrop()
                                .error(android.R.drawable.ic_menu_gallery) // Show placeholder if image not found
                                .placeholder(android.R.drawable.ic_menu_gallery) // Show placeholder while loading
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // Cache both original and resized images
                                .skipMemoryCache(false) // Use memory cache
                                .into(holder.photoImageViewUnsynced);
                    }
                }
            }

            // Show synced icon and border if photo is synced
            if (photo.isSynced()) {
                holder.syncedIcon.setVisibility(View.VISIBLE);
                holder.photoContainer.setVisibility(View.VISIBLE);
                holder.photoImageViewUnsynced.setVisibility(View.GONE);
            } else {
                holder.syncedIcon.setVisibility(View.GONE);
                holder.photoContainer.setVisibility(View.GONE);
                holder.photoImageViewUnsynced.setVisibility(View.VISIBLE);
            }

            // Show selected icon and overlay if photo is selected
            if (photo.isSelected()) {
                holder.selectedIcon.setVisibility(View.VISIBLE);
                holder.selectedOverlay.setVisibility(View.VISIBLE);
            } else {
                holder.selectedIcon.setVisibility(View.GONE);
                holder.selectedOverlay.setVisibility(View.GONE);
            }

            // Show play icon if it's a video
            if (photo.isVideo()) {
                holder.playIcon.setVisibility(View.VISIBLE);
            } else {
                holder.playIcon.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }

        class PhotoItemViewHolder extends RecyclerView.ViewHolder {
            ImageView photoImageView;
            ImageView photoImageViewUnsynced;
            ImageView syncedIcon;
            ImageView selectedIcon;
            ImageView playIcon;
            View photoContainer;
            View selectedOverlay;

            PhotoItemViewHolder(View itemView) {
                super(itemView);
                photoImageView = itemView.findViewById(R.id.photo_image);
                photoImageViewUnsynced = itemView.findViewById(R.id.photo_image_unsynced);
                syncedIcon = itemView.findViewById(R.id.synced_icon);
                selectedIcon = itemView.findViewById(R.id.selected_icon);
                playIcon = itemView.findViewById(R.id.play_icon);
                photoContainer = itemView.findViewById(R.id.photo_container);
                selectedOverlay = itemView.findViewById(R.id.selected_overlay);

                // Set click listener for the photo item
                itemView.setOnClickListener(v -> {
                    if (onPhotoItemClickListener != null) {
                        onPhotoItemClickListener.onPhotoClick(getAdapterPosition());
                    }
                });

                // Set long click listener for the photo item
                itemView.setOnLongClickListener(v -> {
                    if (onPhotoItemClickListener != null) {
                        onPhotoItemClickListener.onPhotoLongClick(getAdapterPosition());
                        return true;
                    }
                    return false;
                });
            }
        }
    }
}