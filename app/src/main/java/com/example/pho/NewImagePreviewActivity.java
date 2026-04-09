package com.example.pho;

import android.content.ContentResolver;
import android.database.Cursor;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;

public class NewImagePreviewActivity extends AppCompatActivity {

    private ImageView previewImageView;
    private ArrayList<Long> photoIds;
    private ArrayList<String> photoNames;
    private ArrayList<String> photoPaths;
    private int currentPosition;
    private GestureDetector gestureDetector;
    private boolean isCloudPhotos = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        // Initialize views
        previewImageView = findViewById(R.id.preview_image);
        
        // Log initialization
        System.out.println("NewImagePreviewActivity onCreate started");
        System.out.println("previewImageView: " + previewImageView);

        // Get intent extras
        Intent intent = getIntent();
        long[] photoIdsArray = intent.getLongArrayExtra("photo_ids");
        photoIds = new ArrayList<>();
        if (photoIdsArray != null) {
            for (long id : photoIdsArray) {
                photoIds.add(id);
            }
        }
        photoNames = intent.getStringArrayListExtra("photo_names");
        photoPaths = intent.getStringArrayListExtra("photo_paths");
        isCloudPhotos = intent.getBooleanExtra("is_cloud_photos", false);
        currentPosition = intent.getIntExtra("current_position", 0);
        
        // Log intent extras
        System.out.println("Intent extras:");
        System.out.println("photoIds size: " + photoIds.size());
        System.out.println("photoNames: " + (photoNames != null ? photoNames.size() : "null"));
        System.out.println("photoPaths: " + (photoPaths != null ? photoPaths.size() : "null"));
        System.out.println("isCloudPhotos: " + isCloudPhotos);
        System.out.println("currentPosition: " + currentPosition);

        // Set up gesture detector for swipe navigation and long press
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1.getX() - e2.getX() > 100) {
                    // Swipe left
                    if (isCloudPhotos && photoPaths != null && currentPosition < photoPaths.size() - 1) {
                        currentPosition++;
                        // Slide in from right
                        previewImageView.setTranslationX(previewImageView.getWidth());
                        previewImageView.animate()
                                .translationX(0)
                                .setDuration(300)
                                .start();
                        loadImage();
                    } else if (!isCloudPhotos && currentPosition < photoIds.size() - 1) {
                        currentPosition++;
                        // Slide in from right
                        previewImageView.setTranslationX(previewImageView.getWidth());
                        previewImageView.animate()
                                .translationX(0)
                                .setDuration(300)
                                .start();
                        loadImage();
                    }
                    return true;
                } else if (e2.getX() - e1.getX() > 100) {
                    // Swipe right
                    if (currentPosition > 0) {
                        currentPosition--;
                        // Slide in from left
                        previewImageView.setTranslationX(-previewImageView.getWidth());
                        previewImageView.animate()
                                .translationX(0)
                                .setDuration(300)
                                .start();
                        loadImage();
                    }
                    return true;
                }
                return false;
            }
            
            @Override
            public void onLongPress(MotionEvent e) {
                if (!isCloudPhotos && !photoIds.isEmpty() && currentPosition < photoIds.size()) {
                    // Get local photo path
                    long photoId = photoIds.get(currentPosition);
                    String photoName = photoNames != null && photoNames.size() > currentPosition ? photoNames.get(currentPosition) : "";
                    
                    // Query MediaStore for the file path
                    String[] projection = {MediaStore.Images.Media.DATA};
                    String selection = MediaStore.Images.Media._ID + " = ?";
                    String[] selectionArgs = {String.valueOf(photoId)};
                    
                    try (Cursor cursor = getContentResolver().query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            null
                    )) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            String filePath = cursor.getString(dataColumn);
                            
                            // Show file path and name
                            String message = "文件路径: " + filePath + "\n文件名: " + photoName;
                            new android.app.AlertDialog.Builder(NewImagePreviewActivity.this)
                                    .setTitle("文件信息")
                                    .setMessage(message)
                                    .setPositiveButton("确定", null)
                                    .show();
                        }
                    } catch (Exception ex) {
                        System.out.println("Error getting file path: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                } else if (isCloudPhotos && photoPaths != null && !photoPaths.isEmpty() && currentPosition < photoPaths.size()) {
                    // For cloud photos, show the URL
                    String photoPath = photoPaths.get(currentPosition);
                    String photoName = photoNames != null && photoNames.size() > currentPosition ? photoNames.get(currentPosition) : "";
                    
                    // Show cloud photo URL and name
                    String message = "文件路径: " + photoPath + "\n文件名: " + photoName;
                    new android.app.AlertDialog.Builder(NewImagePreviewActivity.this)
                            .setTitle("文件信息")
                            .setMessage(message)
                            .setPositiveButton("确定", null)
                            .show();
                }
            }
        });

        // Set touch listener for the image view
        previewImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });



        // Set click listener to close the activity
        previewImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Load initial image
        loadImage();
        
        System.out.println("NewImagePreviewActivity onCreate completed");
    }

    private void loadImage() {
        loadImage(false);
    }
    
    private void loadImage(boolean skipCache) {
        System.out.println("loadImage called, skipCache: " + skipCache);
        System.out.println("Current position: " + currentPosition);

        if (isCloudPhotos && photoPaths != null && !photoPaths.isEmpty() && currentPosition < photoPaths.size()) {
            // Load cloud photo
            String photoPath = photoPaths.get(currentPosition);
            
            // Ensure using original image URL, not thumbnail
            if (photoPath.contains(".thumbnail")) {
                // Replace .thumbnail with original path
                photoPath = photoPath.replace(".thumbnail", "");
                System.out.println("Using original image URL for preview: " + photoPath);
            }
            
            String photoName = photoNames != null && photoNames.size() > currentPosition ? photoNames.get(currentPosition) : "";
            System.out.println("Loading cloud photo: " + photoPath);
            
            // Get WebDAV credentials
            android.content.SharedPreferences preferences = getSharedPreferences("webdav_settings", MODE_PRIVATE);
            String username = preferences.getString("webdav_username", "");
            String password = preferences.getString("webdav_password", "");
            
            // Create GlideUrl with authentication
            GlideUrl glideUrl = new GlideUrl(
                    photoPath,
                    new LazyHeaders.Builder()
                            .addHeader("Authorization", "Basic " + android.util.Base64.encodeToString((username + ":" + password).getBytes(), android.util.Base64.NO_WRAP))
                            .build()
            );
            
            // Clear Glide cache for this specific URL
            if (skipCache) {
                Glide.get(this).clearMemory();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Glide.get(NewImagePreviewActivity.this).clearDiskCache();
                    }
                }).start();
            }
            
            // Load image using Glide with original size and no transformations
            Glide.with(this)
                    .load(glideUrl)
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .skipMemoryCache(skipCache)
                    .diskCacheStrategy(skipCache ? DiskCacheStrategy.NONE : DiskCacheStrategy.ALL)
                    .dontTransform() // Don't apply any transformations
                    .into(previewImageView);
            
            System.out.println("Cloud photo loaded, skipCache: " + skipCache);
        } else if (!photoIds.isEmpty() && currentPosition < photoIds.size()) {
            // Load local photo
            long photoId = photoIds.get(currentPosition);
            String photoName = photoNames != null && photoNames.size() > currentPosition ? photoNames.get(currentPosition) : "";
            System.out.println("Loading local photo: " + photoId);
            
            // Load image using content URI with Glide
            Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(photoId));
            System.out.println("Local photo URI: " + uri.toString());
            
            // Use Glide to load the image, which can handle large files better
            Glide.with(this)
                    .load(uri)
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(previewImageView);
            
            System.out.println("Local photo loaded with Glide");
        } else {
            // No image to load
            previewImageView.setImageResource(android.R.drawable.ic_menu_gallery);
            System.out.println("No image to load");
        }
    }
    
    private void refreshCurrentImage() {
        System.out.println("Refreshing current image");
        if (isCloudPhotos) {
            // Force refresh by skipping cache
            loadImage(true);
            android.widget.Toast.makeText(this, "图片已刷新", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
