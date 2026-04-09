package com.example.pho;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.content.ContentResolver;
import android.database.Cursor;
import java.io.InputStream;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.ArrayList;

public class ImagePreviewActivity extends AppCompatActivity {

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
        System.out.println("ImagePreviewActivity onCreate started");
        System.out.println("previewImageView: " + previewImageView);

        // Get intent extras
        Intent intent = getIntent();
        photoIds = intent.getLongArrayExtra("photo_ids") != null ? 
                convertLongArrayToArrayList(intent.getLongArrayExtra("photo_ids")) : new ArrayList<>();
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

        // Set initial state for preview image view
        previewImageView.setTranslationX(0);
        previewImageView.setAlpha(1);
        // Set a placeholder image immediately
        System.out.println("Setting initial placeholder image");
        previewImageView.setImageResource(android.R.drawable.ic_menu_gallery);
        // Force initial redraw
        previewImageView.invalidate();
        previewImageView.requestLayout();
        System.out.println("Initial placeholder set and redrawn");

        // Initialize gesture detector for swipe and long press
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1.getX() - e2.getX() > 100) {
                    // Swipe left
                    System.out.println("Swipe left detected");
                    if (isCloudPhotos && photoPaths != null && currentPosition < photoPaths.size() - 1) {
                        currentPosition++;
                        System.out.println("Current position after left swipe: " + currentPosition);
                        loadCurrentImage();
                    } else if (!isCloudPhotos && currentPosition < photoIds.size() - 1) {
                        currentPosition++;
                        System.out.println("Current position after left swipe: " + currentPosition);
                        loadCurrentImage();
                    }
                    return true;
                } else if (e2.getX() - e1.getX() > 100) {
                    // Swipe right
                    System.out.println("Swipe right detected");
                    if (currentPosition > 0) {
                        currentPosition--;
                        System.out.println("Current position after right swipe: " + currentPosition);
                        loadCurrentImage();
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
                            new android.app.AlertDialog.Builder(ImagePreviewActivity.this)
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
                    new android.app.AlertDialog.Builder(ImagePreviewActivity.this)
                            .setTitle("文件信息")
                            .setMessage(message)
                            .setPositiveButton("确定", null)
                            .show();
                }
            }
        });

        // Load initial image in onResume
        

        // Set click listener to close the activity
        previewImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Set touch listener for swipe detection
        previewImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
    }

    private void loadCurrentImage() {
        // Reset image view position
        previewImageView.setTranslationX(0);
        previewImageView.setAlpha(1); // Set alpha to 1 immediately

        // Log debug information
        System.out.println("loadCurrentImage called");
        System.out.println("isCloudPhotos: " + isCloudPhotos);
        System.out.println("photoPaths: " + (photoPaths != null ? photoPaths.size() : "null"));
        System.out.println("photoIds: " + (photoIds != null ? photoIds.size() : "null"));
        System.out.println("currentPosition: " + currentPosition);
        System.out.println("previewImageView visibility: " + previewImageView.getVisibility());
        System.out.println("previewImageView width: " + previewImageView.getWidth());
        System.out.println("previewImageView height: " + previewImageView.getHeight());

        // First, always set a placeholder to ensure something is displayed
        System.out.println("Setting placeholder image");
        previewImageView.setImageResource(android.R.drawable.ic_menu_gallery);
        // Force a redraw
        previewImageView.invalidate();
        previewImageView.requestLayout();
        System.out.println("Placeholder set and redrawn");

        if (isCloudPhotos && photoPaths != null && !photoPaths.isEmpty() && currentPosition < photoPaths.size()) {
            // Load cloud photo
            final String photoPath = photoPaths.get(currentPosition);
            final String photoName = photoNames != null && photoNames.size() > currentPosition ? photoNames.get(currentPosition) : "";

            System.out.println("Loading cloud photo: " + photoPath);

            // Load image using Glide
            try {
                System.out.println("Loading cloud photo with Glide");
                
                // Ensure using original image URL, not thumbnail
                String originalPhotoPath = photoPath;
                if (originalPhotoPath.contains(".thumbnail")) {
                    // Replace .thumbnail with original path
                    originalPhotoPath = originalPhotoPath.replace(".thumbnail", "");
                    System.out.println("Using original image URL for preview: " + originalPhotoPath);
                }
                
                // Get WebDAV credentials
                android.content.SharedPreferences preferences = getSharedPreferences("webdav_settings", MODE_PRIVATE);
                String username = preferences.getString("webdav_username", "");
                String password = preferences.getString("webdav_password", "");
                
                // Create GlideUrl with authentication
                com.bumptech.glide.load.model.GlideUrl glideUrl = new com.bumptech.glide.load.model.GlideUrl(
                        originalPhotoPath,
                        new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                .addHeader("Authorization", "Basic " + android.util.Base64.encodeToString((username + ":" + password).getBytes(), android.util.Base64.NO_WRAP))
                                .build()
                );
                
                // Load image using Glide with original size and no transformations
                Glide.with(this)
                        .load(glideUrl)
                        .error(android.R.drawable.ic_menu_gallery)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .dontTransform() // Don't apply any transformations
                        .into(previewImageView);
                System.out.println("Cloud photo loaded with Glide");
                // Force a redraw
                previewImageView.invalidate();
                previewImageView.requestLayout();
                System.out.println("Cloud photo redrawn");
            } catch (Exception e) {
                System.out.println("Error loading cloud photo: " + e.getMessage());
                e.printStackTrace();
                previewImageView.setImageResource(android.R.drawable.ic_menu_gallery);
                // Force a redraw
                previewImageView.invalidate();
                previewImageView.requestLayout();
            }
        } else if (!photoIds.isEmpty() && currentPosition < photoIds.size()) {
            // Load local photo
            final long photoId = photoIds.get(currentPosition);
            final String photoName = photoNames != null && photoNames.size() > currentPosition ? photoNames.get(currentPosition) : "";

            System.out.println("Loading local photo: " + photoId);

            // Try to load the image using ContentResolver
            try {
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(photoId));
                System.out.println("Local photo URI: " + uri.toString());
                
                // Check if the URI exists
                ContentResolver contentResolver = getContentResolver();
                System.out.println("ContentResolver: " + contentResolver);
                
                // Try to open input stream to check if the URI is valid
                InputStream inputStream = contentResolver.openInputStream(uri);
                System.out.println("Input stream opened: " + (inputStream != null));
                if (inputStream != null) {
                    inputStream.close();
                    System.out.println("Input stream closed");
                }
                
                // Set URI directly to ImageView
                System.out.println("Setting URI directly to ImageView");
                previewImageView.setImageURI(uri);
                System.out.println("URI set to ImageView");
                
                // Force a redraw
                previewImageView.invalidate();
                previewImageView.requestLayout();
                System.out.println("Local photo redrawn");
            } catch (Exception e) {
                System.out.println("Error loading local photo: " + e.getMessage());
                e.printStackTrace();
                previewImageView.setImageResource(android.R.drawable.ic_menu_gallery);
                // Force a redraw
                previewImageView.invalidate();
                previewImageView.requestLayout();
            }
        } else {
            // No image data, show placeholder
            System.out.println("No image data, showing placeholder");
            previewImageView.setImageResource(android.R.drawable.ic_menu_gallery);
            
            // Force a redraw after setting the placeholder
            previewImageView.invalidate();
            previewImageView.requestLayout();
            System.out.println("Placeholder redrawn");
        }
    }

    private ArrayList<Long> convertLongArrayToArrayList(long[] array) {
        ArrayList<Long> list = new ArrayList<>();
        for (long value : array) {
            list.add(value);
        }
        return list;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load initial image when activity is resumed
        loadCurrentImage();
    }
}
