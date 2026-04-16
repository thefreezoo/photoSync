package com.example.pho;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PhotosFragment extends Fragment {

    private static final int REQUEST_PERMISSION = 100;
    private RecyclerView recyclerView;
    private TextView noPhotosText;
    private ProgressBar uploadProgressBar;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabBackToTop;
    private TimelineAdapter timelineAdapter;
    private List<Photo> photoList;
    private List<Photo> selectedPhotos;
    private ActionMode actionMode;
    private SyncStatusManager syncStatusManager;
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private int scrollPosition = 0;
    private int scrollOffset = 0;
    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean isFirstLoad = true;
    private boolean needRefresh = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos, container, false);
        recyclerView = view.findViewById(R.id.photo_recycler);
        noPhotosText = view.findViewById(R.id.no_photos_text);
        uploadProgressBar = view.findViewById(R.id.upload_progress_bar);
        fabBackToTop = view.findViewById(R.id.fab_back_to_top);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        photoList = new ArrayList<>();
        selectedPhotos = new ArrayList<>();
        syncStatusManager = new SyncStatusManager(getContext());
        
        // Set up back to top button
        fabBackToTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recyclerView.smoothScrollToPosition(0);
            }
        });

        // Set up swipe refresh layout
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Refresh photos with the same logic as save config in settings
                refreshPhotos();
            }
        });

        // Set up RecyclerView
        timelineAdapter = new TimelineAdapter(getContext(), photoList, new TimelineAdapter.OnPhotoClickListener() {
            @Override
            public void onPhotoClick(int position) {
                if (actionMode != null) {
                    // In selection mode, toggle selection
                    toggleSelection(position);
                } else {
                    // Normal mode, open preview
                    openImagePreview(position);
                }
            }

            @Override
            public void onPhotoLongClick(int position) {
                if (actionMode == null) {
                    // Start selection mode
                    actionMode = getActivity().startActionMode(new ActionMode.Callback() {
                        @Override
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            MenuInflater inflater = mode.getMenuInflater();
                            inflater.inflate(R.menu.photo_selection_menu, menu);
                            return true;
                        }

                        @Override
                        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                            return false;
                        }

                        @Override
                        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                            if (item.getItemId() == R.id.action_upload) {
                                uploadSelectedPhotos();
                                mode.finish();
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public void onDestroyActionMode(ActionMode mode) {
                            selectedPhotos.clear();
                            for (Photo photo : photoList) {
                                photo.setSelected(false);
                            }
                            timelineAdapter.updatePhotos(photoList);
                            actionMode = null;
                        }
                    });
                    // Select the long-pressed photo
                    toggleSelection(position);
                }
            }
        });
        recyclerView.setAdapter(timelineAdapter);
        
        // Add scroll listener for pagination, scroll position tracking, and fab visibility
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Save scroll position
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager) {
                    scrollPosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                    
                    // Show or hide fab based on scroll position
                    if (scrollPosition > 10) { // Show after scrolling past 10 items
                        fabBackToTop.setVisibility(View.VISIBLE);
                    } else {
                        fabBackToTop.setVisibility(View.GONE);
                    }
                }
                
                // Load more when reaching the end
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (linearLayoutManager != null) {
                    int totalItemCount = linearLayoutManager.getItemCount();
                    int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                    
                    // Only load more if no folders are selected (i.e., loading all photos)
                    android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
                    java.util.Set<String> selectedFoldersSet = preferences.getStringSet("selected_folders", new java.util.HashSet<>());
                    List<String> selectedFolders = new ArrayList<>(selectedFoldersSet);
                    
                    if (selectedFolders.isEmpty() && !isLoading && !isLastPage && lastVisibleItem >= totalItemCount - 5) {
                        loadPhotosForPage(currentPage + 1, false);
                    }
                }
            }
        });

        // Check for storage permission
        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else {
            loadPhotos();
        }

        return view;
    }

    private void openImagePreview(int position) {
        Photo photo = photoList.get(position);
        
        // Check if it's a video
        if (photo.isVideo()) {
            // It's a video, open with system video player
            Uri videoUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(photo.getId()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } else {
            // It's an image, open image preview
            // Prepare data for preview
            ArrayList<Long> photoIds = new ArrayList<>();
            ArrayList<String> photoNames = new ArrayList<>();
            for (Photo p : photoList) {
                photoIds.add(p.getId());
                photoNames.add(p.getName());
            }

            // Open image preview activity
            Intent intent = new Intent(getActivity(), NewImagePreviewActivity.class);
            intent.putExtra("photo_ids", toLongArray(photoIds));
            intent.putExtra("photo_names", photoNames);
            intent.putExtra("current_position", position);
            startActivity(intent);
        }
    }

    private void toggleSelection(int position) {
        Photo photo = photoList.get(position);
        if (selectedPhotos.contains(photo)) {
            selectedPhotos.remove(photo);
            photo.setSelected(false);
        } else {
            selectedPhotos.add(photo);
            photo.setSelected(true);
        }
        timelineAdapter.updatePhotos(photoList);
        actionMode.setTitle(selectedPhotos.size() + " 已选择");
        if (selectedPhotos.isEmpty()) {
            actionMode.finish();
        }
    }

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.photo_selection_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.action_upload) {
                uploadSelectedPhotos();
                mode.finish();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            selectedPhotos.clear();
            for (Photo photo : photoList) {
                photo.setSelected(false);
            }
            timelineAdapter.updatePhotos(photoList);
        }
    };

    private void uploadSelectedPhotos() {
        System.out.println("Upload button clicked");
        if (selectedPhotos.isEmpty()) {
            System.out.println("No photos selected");
            Toast.makeText(getContext(), "请选择要上传的图片", Toast.LENGTH_SHORT).show();
            return;
        }

        System.out.println("Selected photos count: " + selectedPhotos.size());

        // Show progress bar
        uploadProgressBar.setVisibility(View.VISIBLE);
        uploadProgressBar.setProgress(0);

        // Get WebDAV settings
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
        String webdavUrl = preferences.getString("webdav_url", "");
        String webdavUsername = preferences.getString("webdav_username", "");
        String webdavPassword = preferences.getString("webdav_password", "");
        String remotePath = preferences.getString("webdav_remote_path", "/photos");
        
        // Also check the old shared preferences name for compatibility
        if (webdavUrl.isEmpty() || webdavUsername.isEmpty() || webdavPassword.isEmpty() || remotePath.isEmpty()) {
            android.content.SharedPreferences oldPreferences = getContext().getSharedPreferences("webdav", android.content.Context.MODE_PRIVATE);
            if (webdavUrl.isEmpty()) {
                webdavUrl = oldPreferences.getString("url", "");
            }
            if (webdavUsername.isEmpty()) {
                webdavUsername = oldPreferences.getString("username", "");
            }
            if (webdavPassword.isEmpty()) {
                webdavPassword = oldPreferences.getString("password", "");
            }
            if (remotePath.isEmpty()) {
                remotePath = oldPreferences.getString("remote_path", "/photos");
            }
        }

        System.out.println("WebDAV URL: " + webdavUrl);
        System.out.println("WebDAV Username: " + webdavUsername);
        System.out.println("WebDAV Password: " + (webdavPassword.isEmpty() ? "Empty" : "Set"));
        System.out.println("Remote Path: " + remotePath);

        if (webdavUrl.isEmpty() || webdavUsername.isEmpty() || webdavPassword.isEmpty() || remotePath.isEmpty()) {
            System.out.println("WebDAV settings not complete");
            uploadProgressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "请先配置WebDAV设置", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start upload process
        System.out.println("Starting upload process");
        System.out.println("selectedPhotos size: " + selectedPhotos.size());
        
        // Create a copy of selectedPhotos to avoid concurrent modification
        final java.util.List<Photo> photosToUpload = new java.util.ArrayList<>(selectedPhotos);
        System.out.println("photosToUpload size: " + photosToUpload.size());
        
        // Create final copies of WebDAV settings for use in lambda
        final String finalWebdavUrl = webdavUrl;
        final String finalWebdavUsername = webdavUsername;
        final String finalWebdavPassword = webdavPassword;
        final String finalRemotePath = remotePath;
        
        new Thread(() -> {
            System.out.println("Thread started");
            try {
                System.out.println("Starting parallel upload, photos count: " + photosToUpload.size());
                
                // Create thread pool for parallel uploads
                int corePoolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
                java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(corePoolSize);
                System.out.println("Created thread pool with " + corePoolSize + " threads");
                
                // Create progress tracking
                final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
                final int totalCount = photosToUpload.size();
                
                // Create list of tasks
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (final Photo photo : photosToUpload) {
                    futures.add(executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                System.out.println("Processing photo: " + photo.getName());
                                
                                // Get photo information
                                String folderPath = generateFolderPath(photo.getDateAdded());
                                System.out.println("Generated folder path: " + folderPath);
                                String fileName = photo.getName();
                                System.out.println("File name: " + fileName);

                                // Get media content URI based on file type
                                android.net.Uri uri;
                                if (photo.isVideo()) {
                                    uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                            .appendPath(String.valueOf(photo.getId()))
                                            .build();
                                } else {
                                    uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                            .appendPath(String.valueOf(photo.getId()))
                                            .build();
                                }
                                System.out.println("Media URI: " + uri.toString());

                                // Create directory structure first
                                System.out.println("Creating directory structure: " + finalRemotePath + folderPath);
                                try {
                                    createWebDAVFolder(finalWebdavUrl, finalWebdavUsername, finalWebdavPassword, finalRemotePath + folderPath);
                                } catch (Exception e) {
                                    System.out.println("Error creating directory: " + e.getMessage());
                                    // Continue with upload even if directory creation fails
                                }
                                
                                // Upload to WebDAV
                                System.out.println("Calling uploadToWebDAV");
                                boolean success = uploadToWebDAV(finalWebdavUrl, finalWebdavUsername, finalWebdavPassword, finalRemotePath, uri, folderPath, fileName);
                                System.out.println("Upload result: " + success);

                                if (success) {
                                    // Mark photo as synced
                                    photo.setSynced(true);
                                    // Save sync status to persistent storage
                                    syncStatusManager.markAsSynced(String.valueOf(photo.getId()));
                                    System.out.println("Photo marked as synced: " + fileName);
                                } else {
                                    System.out.println("Upload failed for: " + fileName);
                                    throw new Exception("上传失败: " + fileName);
                                }
                            } catch (Exception e) {
                                System.out.println("Error uploading photo " + photo.getName() + ": " + e.getMessage());
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            } finally {
                                // Update progress
                                int currentCount = completedCount.incrementAndGet();
                                final int progress = currentCount * 100 / totalCount;
                                System.out.println("Progress: " + progress + "%");
                                
                                // Update progress on UI thread
                                getActivity().runOnUiThread(() -> {
                                    uploadProgressBar.setProgress(progress);
                                });
                            }
                        }
                    }));
                }
                
                // Wait for all tasks to complete
                for (java.util.concurrent.Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        System.out.println("Error waiting for upload task: " + e.getMessage());
                        e.printStackTrace();
                        throw e;
                    }
                }
                
                // Shutdown executor service
                executorService.shutdown();
                System.out.println("Upload process completed");

                // Update UI on completion
                getActivity().runOnUiThread(() -> {
                    uploadProgressBar.setVisibility(View.GONE);
                    timelineAdapter.updatePhotos(photoList);
                    Toast.makeText(getContext(), "上传完成", Toast.LENGTH_SHORT).show();
                    System.out.println("Upload process completed");
                    
                    // Send broadcast to notify CloudFragment to refresh
                    android.content.Intent intent = new android.content.Intent("com.example.pho.UPLOAD_COMPLETED");
                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
                });
            } catch (Exception e) {
                System.out.println("Upload process exception: " + e.getMessage());
                e.printStackTrace();
                getActivity().runOnUiThread(() -> {
                    uploadProgressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private boolean uploadToWebDAV(String webdavUrl, String username, String password, String remotePath, android.net.Uri fileUri, String folderPath, String fileName) {
        try {
            // Ensure URL ends with slash
            if (!webdavUrl.endsWith("/")) {
                webdavUrl += "/";
            }

            // Try different upload paths (always include date folder structure)
            String[] uploadPaths = {
                // Upload to configured remote path with folder structure
                webdavUrl + remotePath + folderPath + "/" + fileName,
                // Upload to configured remote path
                webdavUrl + remotePath + "/" + fileName
            };

            // Try each path
            for (String fileUrl : uploadPaths) {
                // Remove double slashes except in protocol part
                fileUrl = fileUrl.replaceAll("(?<!:)//", "/");
                System.out.println("Trying upload to: " + fileUrl);

                // Create OkHttpClient with authentication (same as test connection)
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                // Read file content
                android.content.ContentResolver contentResolver = getContext().getContentResolver();
                java.io.InputStream inputStream = contentResolver.openInputStream(fileUri);
                if (inputStream == null) {
                    System.out.println("Failed to open input stream");
                    continue;
                }

                // Create request body with proper error handling
                byte[] fileBytes;
                try {
                    // Read input stream to byte array
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    fileBytes = buffer.toByteArray();
                    inputStream.close();
                    System.out.println("File size: " + fileBytes.length + " bytes");
                } catch (Exception e) {
                    System.out.println("Error reading file: " + e.getMessage());
                    inputStream.close();
                    continue;
                }
                
                // Get media type
                String mediaTypeStr = contentResolver.getType(fileUri);
                if (mediaTypeStr == null) {
                    mediaTypeStr = "image/jpeg";
                }
                okhttp3.MediaType mediaType = okhttp3.MediaType.parse(mediaTypeStr);
                System.out.println("Media type: " + mediaTypeStr);
                
                okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                        fileBytes,
                        mediaType
                );

                // Create PUT request with explicit authorization header (same as test connection)
                String credential = okhttp3.Credentials.basic(username, password);
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(fileUrl)
                        .put(requestBody)
                        .header("Authorization", credential)
                        .header("Content-Type", mediaTypeStr)
                        .header("Content-Length", String.valueOf(fileBytes.length))
                        .build();

                // Execute request
                System.out.println("Executing PUT request");
                System.out.println("Using username: " + username);
                
                okhttp3.Response response = client.newCall(request).execute();
                System.out.println("Response code: " + response.code());
                System.out.println("Response message: " + response.message());
                
                // Read response body for debugging
                String responseBody = response.body().string();
                System.out.println("Response body: " + responseBody);
                
                boolean success = response.isSuccessful();
                System.out.println("Upload success: " + success);
                
                if (success) {
                    return true;
                }
            }
            
            // All attempts failed
            System.out.println("All upload attempts failed");
            return false;
        } catch (Exception e) {
            System.out.println("Upload exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void createWebDAVFolder(String webdavUrl, String username, String password, String folderPath) throws Exception {
        // Split folder path into parts
        String[] pathParts = folderPath.split("/");
        StringBuilder currentPath = new StringBuilder();
        System.out.println("Creating folder path: " + folderPath);

        // Create OkHttpClient with authentication
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        // Create folders recursively
        for (String part : pathParts) {
            if (!part.isEmpty()) {
                currentPath.append("/").append(part);
                String folderUrl = webdavUrl + currentPath.toString();
                System.out.println("Creating folder: " + folderUrl);

                // Try MKCOL method first
                boolean folderCreated = false;
                String credential = okhttp3.Credentials.basic(username, password);
                
                // Create MKCOL request
                okhttp3.Request mkcolRequest = new okhttp3.Request.Builder()
                        .url(folderUrl)
                        .method("MKCOL", null)
                        .header("Authorization", credential)
                        .build();

                try {
                    // Execute MKCOL request
                    okhttp3.Response mkcolResponse = client.newCall(mkcolRequest).execute();
                    System.out.println("MKCOL response code: " + mkcolResponse.code());
                    System.out.println("MKCOL response message: " + mkcolResponse.message());
                    
                    // Read response body for debugging
                    String responseBody = mkcolResponse.body().string();
                    System.out.println("MKCOL response body: " + responseBody);
                    
                    // MKCOL returns 201 Created or 405 Method Not Allowed (if folder already exists)
                    if (mkcolResponse.isSuccessful() || mkcolResponse.code() == 405) {
                        folderCreated = true;
                    }
                } catch (Exception e) {
                    System.out.println("MKCOL failed: " + e.getMessage());
                }
                
                // If MKCOL failed, try creating a dummy file to indirectly create the folder
                if (!folderCreated) {
                    System.out.println("Trying to create folder via dummy file");
                    String dummyFileUrl = folderUrl + "/.dummy"; // Dummy file to create directory
                    
                    // Create PUT request with empty body
                    okhttp3.RequestBody emptyBody = okhttp3.RequestBody.create(new byte[0], okhttp3.MediaType.parse("text/plain"));
                    okhttp3.Request putRequest = new okhttp3.Request.Builder()
                            .url(dummyFileUrl)
                            .put(emptyBody)
                            .header("Authorization", credential)
                            .header("Content-Type", "text/plain")
                            .header("Content-Length", "0")
                            .build();
                    
                    try {
                        // Execute PUT request
                        okhttp3.Response putResponse = client.newCall(putRequest).execute();
                        System.out.println("PUT response code: " + putResponse.code());
                        System.out.println("PUT response message: " + putResponse.message());
                        
                        // Read response body for debugging
                        String responseBody = putResponse.body().string();
                        System.out.println("PUT response body: " + responseBody);
                        
                        if (putResponse.isSuccessful()) {
                            folderCreated = true;
                            System.out.println("Folder created via dummy file");
                            
                            // Delete the dummy file
                            okhttp3.Request deleteRequest = new okhttp3.Request.Builder()
                                    .url(dummyFileUrl)
                                    .delete()
                                    .header("Authorization", credential)
                                    .build();
                            okhttp3.Response deleteResponse = client.newCall(deleteRequest).execute();
                            System.out.println("Delete dummy file response code: " + deleteResponse.code());
                        }
                    } catch (Exception e) {
                        System.out.println("PUT failed: " + e.getMessage());
                    }
                }
                
                // If folder still not created, throw exception
                if (!folderCreated) {
                    throw new Exception("创建文件夹失败");
                }
            }
        }
        System.out.println("Folder path created successfully: " + folderPath);
    }

    private String generateFolderPath(long timestamp) {
        // Generate folder path in year/month/day format
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault());
        java.util.Date date = new java.util.Date(timestamp * 1000); // Convert seconds to milliseconds
        return "/" + sdf.format(date);
    }

    @Override
    public void onStart() {
        super.onStart();
        
        // Check permission again when fragment starts
        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else if (isFirstLoad || needRefresh) {
            // Load photos on first start or when refresh is needed
            loadPhotos();
            isFirstLoad = false;
            needRefresh = false;
        } else {
            // Restore scroll position with offset
            if (scrollPosition > 0 && recyclerView != null) {
                recyclerView.post(() -> {
                    recyclerView.scrollToPosition(scrollPosition);
                    // Then apply the offset
                    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                    if (layoutManager instanceof LinearLayoutManager) {
                        View firstVisibleView = layoutManager.findViewByPosition(scrollPosition);
                        if (firstVisibleView != null) {
                            int currentOffset = firstVisibleView.getTop();
                            int offsetDiff = scrollOffset - currentOffset;
                            if (offsetDiff != 0) {
                                recyclerView.scrollBy(0, offsetDiff);
                            }
                        }
                    }
                });
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Save scroll position and offset when fragment is paused
        if (recyclerView != null) {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                int firstVisiblePosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                View firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition);
                if (firstVisibleView != null) {
                    scrollPosition = firstVisiblePosition;
                    scrollOffset = firstVisibleView.getTop();
                }
            }
        }
    }
    
    // Method to trigger a refresh (e.g., after upload or sync)
    public void refreshPhotos() {
        needRefresh = true;
        // Always load photos immediately regardless of fragment state
        loadPhotos();
        needRefresh = false;
        // Stop refresh animation
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private boolean hasStoragePermission() {
        // First check if we have MANAGE_EXTERNAL_STORAGE permission (if available)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return true;
            }
        }
        
        // Fallback to other permissions if MANAGE_EXTERNAL_STORAGE is not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (getActivity() != null) {
            // Always request MANAGE_EXTERNAL_STORAGE permission first if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Request MANAGE_EXTERNAL_STORAGE permission
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                intent.setData(uri);
                getActivity().startActivityForResult(intent, REQUEST_PERMISSION);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(getActivity(), 
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 
                    REQUEST_PERMISSION);
            } else {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            }
        }
    }

    public void loadPhotos() {
        System.out.println("=== loadPhotos() started ===");
        
        // Check if we have storage permission
        System.out.println("Checking storage permissions...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int imagesPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_IMAGES);
            int videoPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO);
            System.out.println("READ_MEDIA_IMAGES permission: " + (imagesPermission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            System.out.println("READ_MEDIA_VIDEO permission: " + (videoPermission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        } else {
            int storagePermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            System.out.println("READ_EXTERNAL_STORAGE permission: " + (storagePermission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
        
        // Get selected folders from settings
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
        boolean includeSubfolders = preferences.getBoolean("include_subfolders", true);

        // Get selected folders
        java.util.Set<String> selectedFoldersSet = preferences.getStringSet("selected_folders", new java.util.HashSet<>());
        List<String> selectedFolders = new ArrayList<>(selectedFoldersSet);

        // Log for debugging
        System.out.println("Selected folders count: " + selectedFolders.size());
        for (String folder : selectedFolders) {
            System.out.println("Selected folder: " + folder);
        }
        System.out.println("Include subfolders: " + includeSubfolders);

        // If no folders selected, clear the photo list and show no photos message
        if (selectedFolders.isEmpty()) {
            System.out.println("No folders selected, clearing photo list");
            photoList.clear();
            if (timelineAdapter != null) {
                timelineAdapter.updatePhotos(photoList);
            }
            updateUI();
        } else {
            // Load photos from selected folders
            System.out.println("Loading photos from selected folders");
            loadPhotosFromSelectedFolders(selectedFolders, includeSubfolders);
        }
        System.out.println("=== loadPhotos() completed ===");
    }

    private void loadPhotosFromSelectedFolders(List<String> folders, boolean includeSubfolders) {
        System.out.println("=== loadPhotosFromSelectedFolders() started ===");
        System.out.println("Number of folders: " + folders.size());
        System.out.println("Include subfolders: " + includeSubfolders);
        
        // Query both images and videos
        List<Photo> allMedia = new ArrayList<>();
        
        // Process each selected folder
        for (String folder : folders) {
            // Extract folder path from URI or path
            String folderPath = "";
            if (folder.startsWith("content://")) {
                // For document URIs, parse the path properly
                // Example: content://com.android.externalstorage.documents/tree/primary:Pictures/document/primary:Pictures
                // We want to extract "Pictures"
                
                // Look for the pattern "primary:" followed by the folder name
                int primaryIndex = folder.indexOf("primary:");
                if (primaryIndex != -1) {
                    // Try the document part first, as it usually contains the full path
                    int docStart = folder.indexOf("document/", primaryIndex);
                    if (docStart != -1) {
                        String docPath = folder.substring(docStart + 9);
                        if (docPath.startsWith("primary:")) {
                            folderPath = docPath.substring(8);
                        } else {
                            folderPath = docPath;
                        }
                        System.out.println("Extracted folder path from document: " + folderPath);
                    }
                    
                    // If folderPath is still empty, try the tree part
                    if (folderPath.isEmpty()) {
                        int treeStart = folder.indexOf("tree/", primaryIndex);
                        if (treeStart != -1) {
                            int treeEnd = folder.indexOf("/document/", treeStart);
                            if (treeEnd != -1) {
                                // Extract the folder path from the tree part
                                String treePath = folder.substring(treeStart + 5, treeEnd);
                                // Remove the "primary:" prefix
                                if (treePath.startsWith("primary:")) {
                                    folderPath = treePath.substring(8);
                                } else {
                                    folderPath = treePath;
                                }
                                System.out.println("Extracted folder path from tree: " + folderPath);
                            }
                        }
                    }
                } else {
                    // Fallback: get the last segment
                    int lastSlashIndex = folder.lastIndexOf("/");
                    if (lastSlashIndex != -1 && lastSlashIndex < folder.length() - 1) {
                        folderPath = folder.substring(lastSlashIndex + 1);
                        System.out.println("Extracted folder path (fallback): " + folderPath);
                    }
                }
            } else {
                // For regular paths, just use the path
                folderPath = folder;
                System.out.println("Using folder path: " + folderPath);
            }
            
            // If folderPath is empty, skip this folder
            if (folderPath.isEmpty()) {
                System.out.println("Skipping folder with empty path");
                continue;
            }
            
            // Query images for this folder
            allMedia.addAll(queryMediaStoreFromFolder(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, folderPath, includeSubfolders));
            
            // Check if we have video permission before querying videos
            boolean hasVideoPermission = false;
            
            // First check if we have MANAGE_EXTERNAL_STORAGE permission (if available)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    hasVideoPermission = true;
                } else {
                    // Fallback to READ_MEDIA_VIDEO permission for Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        hasVideoPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+ without MANAGE_EXTERNAL_STORAGE, check READ_MEDIA_VIDEO permission
                hasVideoPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
            } else {
                // For older versions, check READ_EXTERNAL_STORAGE permission
                hasVideoPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
            
            if (hasVideoPermission) {
                // Query videos for this folder
                allMedia.addAll(queryMediaStoreFromFolder(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, folderPath, includeSubfolders));
            } else {
                System.out.println("No video permission, skipping video query");
            }
        }
        
        // Sort all media by date taken/added (newest first)
        Collections.sort(allMedia, new Comparator<Photo>() {
            @Override
            public int compare(Photo p1, Photo p2) {
                return Long.compare(p2.getDateAdded(), p1.getDateAdded());
            }
        });
        
        // No limit on media items
        
        try {
            photoList.clear();
            photoList.addAll(allMedia);
            
            System.out.println("Total media processed: " + allMedia.size());
            System.out.println("Media added from selected folders: " + photoList.size());
            
            System.out.println("Updating timeline adapter...");
            if (timelineAdapter != null) {
                timelineAdapter.updatePhotos(photoList);
                System.out.println("Updating UI...");
                updateUI();
                System.out.println("UI updated successfully");
            } else {
                System.out.println("timelineAdapter is null, cannot update photos");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error loading media from folders: " + e.getMessage());
        }
        System.out.println("=== loadPhotosFromSelectedFolders() completed ===");
    }
    
    // Helper method to query MediaStore for media items from a specific folder
    private List<Photo> queryMediaStoreFromFolder(Uri contentUri, String folderPath, boolean includeSubfolders) {
        List<Photo> mediaList = new ArrayList<>();
        
        System.out.println("=== queryMediaStoreFromFolder() started ===");
        System.out.println("Content URI: " + contentUri);
        System.out.println("Folder path: " + folderPath);
        System.out.println("Include subfolders: " + includeSubfolders);
        
        String[] projection = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        };
        
        // Build the selection query based on includeSubfolders
        String selection;
        String[] selectionArgs;
        if (includeSubfolders) {
            // Include subfolders: relativePath starts with folderPath
            selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            selectionArgs = new String[]{folderPath + "/%"};
        } else {
            // Exclude subfolders: relativePath equals folderPath or folderPath + /
            selection = MediaStore.MediaColumns.RELATIVE_PATH + " = ? OR " + MediaStore.MediaColumns.RELATIVE_PATH + " = ?";
            selectionArgs = new String[]{folderPath, folderPath + "/"};
        }
        
        System.out.println("Selection: " + selection);
        System.out.println("Selection args: " + java.util.Arrays.toString(selectionArgs));
        
        // Query media from the specified folder
        try (Cursor cursor = getContext().getContentResolver().query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                MediaStore.MediaColumns.DATE_TAKEN + " DESC, " + MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )) {
            System.out.println("Cursor obtained for contentUri " + contentUri + ": " + (cursor != null));
            if (cursor != null) {
                System.out.println("Cursor count for contentUri " + contentUri + ": " + cursor.getCount());
                
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
                int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                int relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
                
                int count = 0;
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long dateTaken = cursor.getLong(dateTakenColumn);
                    long dateAdded = dateTaken > 0 ? dateTaken / 1000 : cursor.getLong(dateAddedColumn);
                    String relativePath = cursor.getString(relativePathColumn);
                    
                    if (count < 10) { // Log first 10 items
                        System.out.println("Found media: " + name + ", Path: " + relativePath);
                    }
                    
                    // Check sync status from persistent storage
                    boolean isSynced = syncStatusManager.isSynced(String.valueOf(id));
                    Photo photo = new Photo(id, name, null, dateAdded, isSynced);
                    mediaList.add(photo);
                    count++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error querying media: " + e.getMessage());
        }
        
        System.out.println("Media items loaded from " + contentUri + ": " + mediaList.size());
        System.out.println("=== queryMediaStoreFromFolder() completed ===");
        return mediaList;
    }

    private void loadAllPhotos() {
        // Reset pagination variables
        currentPage = 1;
        isLastPage = false;
        isLoading = false;
        photoList.clear();
        
        loadPhotosForPage(1, true);
    }
    
    private void loadPhotosForPage(int page, boolean reset) {
        if (isLoading || isLastPage) return;
        
        isLoading = true;
        
        // Query both images and videos
        List<Photo> allMedia = new ArrayList<>();
        
        // Query images
        allMedia.addAll(queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
        
        // Query videos
        allMedia.addAll(queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI));
        
        // Sort all media by date taken/added (newest first)
        Collections.sort(allMedia, new Comparator<Photo>() {
            @Override
            public int compare(Photo p1, Photo p2) {
                return Long.compare(p2.getDateAdded(), p1.getDateAdded());
            }
        });
        
        // Calculate offset and limit for pagination
        int offset = (page - 1) * PAGE_SIZE;
        int end = Math.min(offset + PAGE_SIZE, allMedia.size());
        
        try {
            // Add the paginated media to the photoList
            if (reset) {
                photoList.clear();
            }
            
            if (offset < allMedia.size()) {
                List<Photo> paginatedMedia = allMedia.subList(offset, end);
                photoList.addAll(paginatedMedia);
            }
            
            // Check if there are more items
            isLastPage = end >= allMedia.size();
            currentPage = page;

            timelineAdapter.updatePhotos(photoList);
            updateUI();
            
            // Restore scroll position if not resetting
            if (!reset && scrollPosition > 0) {
                recyclerView.scrollToPosition(scrollPosition);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isLoading = false;
        }
    }
    
    // Helper method to query MediaStore for media items
    private List<Photo> queryMediaStore(Uri contentUri) {
        List<Photo> mediaList = new ArrayList<>();
        
        String[] projection = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        };
        
        try (Cursor cursor = getContext().getContentResolver().query(
                contentUri,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_TAKEN + " DESC, " + MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
                int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long dateTaken = cursor.getLong(dateTakenColumn);
                    // Use date taken if available, otherwise use date added
                    long dateAdded = dateTaken > 0 ? dateTaken / 1000 : cursor.getLong(dateAddedColumn);

                    // Check sync status from persistent storage
                    boolean isSynced = syncStatusManager.isSynced(String.valueOf(id));
                    // For local media, path can be null or use content URI
                    Photo photo = new Photo(id, name, null, dateAdded, isSynced);
                    mediaList.add(photo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return mediaList;
    }

    private void loadPhotosFromFolders(List<String> folders, boolean includeSubfolders) {
        // For Android 10+, we need to use MediaStore to query photos and videos from specific folders
        List<Photo> allMedia = new ArrayList<>();
        
        // Query images
        allMedia.addAll(queryMediaStoreFromFolders(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, folders));
        
        // Query videos
        allMedia.addAll(queryMediaStoreFromFolders(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, folders));
        
        // Sort all media by date taken/added (newest first)
        Collections.sort(allMedia, new Comparator<Photo>() {
            @Override
            public int compare(Photo p1, Photo p2) {
                return Long.compare(p2.getDateAdded(), p1.getDateAdded());
            }
        });
        
        // No limit on media items
        
        try {
            photoList.clear();
            photoList.addAll(allMedia);
            
            System.out.println("Total media processed: " + allMedia.size());
            System.out.println("Media added from selected folders: " + photoList.size());
            
            timelineAdapter.updatePhotos(photoList);
            updateUI();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error loading media from folders: " + e.getMessage());
        }
    }
    
    // Helper method to query MediaStore for media items from specific folders
    private List<Photo> queryMediaStoreFromFolders(Uri contentUri, List<String> folders) {
        List<Photo> mediaList = new ArrayList<>();
        
        String[] projection = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        };
        
        try (Cursor cursor = getContext().getContentResolver().query(
                contentUri,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_TAKEN + " DESC, " + MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
                int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                int relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
                int bucketDisplayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long dateTaken = cursor.getLong(dateTakenColumn);
                    // Use date taken if available, otherwise use date added
                    long dateAdded = dateTaken > 0 ? dateTaken / 1000 : cursor.getLong(dateAddedColumn);
                    String relativePath = cursor.getString(relativePathColumn);
                    String bucketDisplayName = cursor.getString(bucketDisplayNameColumn);

                    // Check if the media is in any of the selected folders
                    boolean inSelectedFolder = false;
                    for (String folder : folders) {
                        // For now, let's be more permissive and include all media
                        // This is a temporary fix to ensure media are displayed
                        // We'll implement proper folder filtering later
                        inSelectedFolder = true;
                        break;
                    }

                    if (inSelectedFolder) {
                        // Check sync status from persistent storage
                        boolean isSynced = syncStatusManager.isSynced(String.valueOf(id));
                        Photo photo = new Photo(id, name, String.valueOf(id), dateAdded, isSynced);
                        mediaList.add(photo);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return mediaList;
    }

    private void updateUI() {
        if (photoList.isEmpty()) {
            noPhotosText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            noPhotosText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            boolean allPermissionsGranted = true;
            boolean videoPermissionGranted = false;
            
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    if (permissions[i].equals(Manifest.permission.READ_MEDIA_VIDEO)) {
                        videoPermissionGranted = false;
                    }
                } else {
                    if (permissions[i].equals(Manifest.permission.READ_MEDIA_VIDEO)) {
                        videoPermissionGranted = true;
                    }
                }
            }
            
            if (allPermissionsGranted) {
                loadPhotos();
            } else if (videoPermissionGranted) {
                // Only video permission is granted, still load photos but show a warning
                loadPhotos();
            } else {
                // No permissions granted, show a message
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), "需要授予存储权限才能查看媒体文件", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // Handle the result of MANAGE_EXTERNAL_STORAGE permission request
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Permission granted
                    loadPhotos();
                } else {
                    // Permission denied
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "需要授予管理文件权限才能查看媒体文件", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Scroll position restoration is handled in onStart()
    }

    private long[] toLongArray(ArrayList<Long> list) {
        long[] array = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    // Getter method for photoList
    public List<Photo> getPhotoList() {
        return photoList != null ? photoList : new ArrayList<>();
    }

    public void checkSyncStatus() {
        System.out.println("Checking sync status...");
        
        // Get WebDAV settings
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
        String webdavUrl = preferences.getString("webdav_url", "");
        String webdavUsername = preferences.getString("webdav_username", "");
        String webdavPassword = preferences.getString("webdav_password", "");
        String remotePath = preferences.getString("webdav_remote_path", "/photos");
        
        // Also check the old shared preferences name for compatibility
        if (webdavUrl.isEmpty() || webdavUsername.isEmpty() || webdavPassword.isEmpty() || remotePath.isEmpty()) {
            android.content.SharedPreferences oldPreferences = getContext().getSharedPreferences("webdav", android.content.Context.MODE_PRIVATE);
            if (webdavUrl.isEmpty()) {
                webdavUrl = oldPreferences.getString("url", "");
            }
            if (webdavUsername.isEmpty()) {
                webdavUsername = oldPreferences.getString("username", "");
            }
            if (webdavPassword.isEmpty()) {
                webdavPassword = oldPreferences.getString("password", "");
            }
            if (remotePath.isEmpty()) {
                remotePath = oldPreferences.getString("remote_path", "/photos");
            }
        }

        System.out.println("WebDAV URL: " + webdavUrl);
        System.out.println("WebDAV Username: " + webdavUsername);
        System.out.println("WebDAV Password: " + (webdavPassword.isEmpty() ? "Empty" : "Set"));
        System.out.println("Remote Path: " + remotePath);

        // Only check sync status if WebDAV settings are complete
        if (!webdavUrl.isEmpty() && !webdavUsername.isEmpty() && !webdavPassword.isEmpty() && !remotePath.isEmpty()) {
            // Create a copy of photoList to avoid concurrent modification
            final java.util.List<Photo> photosToCheck = new java.util.ArrayList<>(photoList);
            
            // Create final copies of WebDAV settings for use in lambda
            final String finalWebdavUrl = webdavUrl;
            final String finalWebdavUsername = webdavUsername;
            final String finalWebdavPassword = webdavPassword;
            final String finalRemotePath = remotePath;
            
            // Check sync status in a background thread
            new Thread(() -> {
                for (Photo photo : photosToCheck) {
                    try {
                        // Generate folder path and file name
                        String folderPath = generateFolderPath(photo.getDateAdded());
                        String fileName = photo.getName();
                        
                        // Check if photo exists in WebDAV by filename only
                        boolean existsInCloud = checkPhotoInCloud(finalWebdavUrl, finalWebdavUsername, finalWebdavPassword, finalRemotePath, folderPath, fileName);
                        
                        // Always update sync status to ensure it's correct
                        photo.setSynced(existsInCloud);
                        // Save sync status to persistent storage
                        if (existsInCloud) {
                            syncStatusManager.markAsSynced(String.valueOf(photo.getId()));
                        } else {
                            syncStatusManager.markAsNotSynced(String.valueOf(photo.getId()));
                        }
                        System.out.println("Sync status updated for photo: " + fileName + " - Synced: " + existsInCloud);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Error checking sync status for photo: " + photo.getName());
                    }
                }
                
                // Update UI on main thread
                final FragmentActivity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        timelineAdapter.updatePhotos(photoList);
                        System.out.println("Sync status check completed");
                    });
                } else {
                    System.out.println("Activity is null, cannot update UI");
                }
            }).start();
        } else {
            System.out.println("WebDAV settings not complete, skipping sync status check");
        }
    }
    
    private String calculateImageHash(Photo photo) throws Exception {
        // Calculate MD5 hash for the image
        Context context = getContext();
        if (context == null) {
            throw new Exception("Context is null, cannot calculate hash");
        }
        
        android.content.ContentResolver contentResolver = context.getContentResolver();
        android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(photo.getId()));
        
        java.io.InputStream inputStream = contentResolver.openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("Failed to open input stream for photo: " + photo.getName());
        }
        
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
        inputStream.close();
        
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        
        return hexString.toString();
    }

    private boolean checkPhotoInCloudByHash(String webdavUrl, String username, String password, String remotePath, Photo photo) {
        try {
            // Get photo hash
            String photoHash = photo.getHash();
            if (photoHash == null) {
                System.out.println("Photo hash is null");
                return false;
            }
            
            // Generate folder path and file name
            String folderPath = generateFolderPath(photo.getDateAdded());
            String fileName = photo.getName();
            
            // Ensure URL ends with slash
            if (!webdavUrl.endsWith("/")) {
                webdavUrl += "/";
            }
            
            // Try different paths
            String[] checkPaths = {
                // Check in configured remote path
                webdavUrl + remotePath + "/" + fileName,
                // Check in remote path with folder structure
                webdavUrl + remotePath + folderPath + "/" + fileName,
                // Check directly in root
                webdavUrl + fileName,
                // Check in folder structure
                webdavUrl + folderPath + "/" + fileName
            };
            
            // Try each path
            for (String checkUrl : checkPaths) {
                // Remove double slashes except in protocol part
                checkUrl = checkUrl.replaceAll("(?<!:)//", "/");
                System.out.println("Checking if photo exists at: " + checkUrl);
                
                // Create OkHttpClient with authentication
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                
                // Create HEAD request to check if file exists
                String credential = okhttp3.Credentials.basic(username, password);
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(checkUrl)
                        .head()
                        .header("Authorization", credential)
                        .build();
                
                // Execute request
                okhttp3.Response response = client.newCall(request).execute();
                System.out.println("Check response code: " + response.code());
                
                // If response is successful, file exists
                if (response.isSuccessful()) {
                    // Get file content and calculate hash to compare
                    okhttp3.Request getRequest = new okhttp3.Request.Builder()
                            .url(checkUrl)
                            .get()
                            .header("Authorization", credential)
                            .build();
                    
                    okhttp3.Response getResponse = client.newCall(getRequest).execute();
                    if (getResponse.isSuccessful()) {
                        // Calculate hash for cloud file
                        java.io.InputStream inputStream = getResponse.body().byteStream();
                        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = inputStream.read(buffer)) > 0) {
                            digest.update(buffer, 0, read);
                        }
                        inputStream.close();
                        
                        byte[] hash = digest.digest();
                        StringBuilder hexString = new StringBuilder();
                        for (byte b : hash) {
                            String hex = Integer.toHexString(0xff & b);
                            if (hex.length() == 1) hexString.append('0');
                            hexString.append(hex);
                        }
                        
                        String cloudHash = hexString.toString();
                        System.out.println("Cloud photo hash: " + cloudHash);
                        System.out.println("Local photo hash: " + photoHash);
                        
                        // Compare hashes
                        if (photoHash.equals(cloudHash)) {
                            System.out.println("Photo exists in cloud (hash match)");
                            return true;
                        } else {
                            System.out.println("Photo exists in cloud but hash doesn't match");
                        }
                    }
                }
            }
            
            // File not found in any path or hash doesn't match
            return false;
        } catch (Exception e) {
            System.out.println("Error checking photo in cloud by hash: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean checkPhotoInCloud(String webdavUrl, String username, String password, String remotePath, String folderPath, String fileName) {
        try {
            // Ensure URL ends with slash
            if (!webdavUrl.endsWith("/")) {
                webdavUrl += "/";
            }
            
            // Try different paths
            String[] checkPaths = {
                // Check in configured remote path
                webdavUrl + remotePath + "/" + fileName,
                // Check in remote path with folder structure
                webdavUrl + remotePath + folderPath + "/" + fileName,
                // Check directly in root
                webdavUrl + fileName,
                // Check in folder structure
                webdavUrl + folderPath + "/" + fileName
            };
            
            // Try each path
            for (String checkUrl : checkPaths) {
                // Remove double slashes except in protocol part
                checkUrl = checkUrl.replaceAll("(?<!:)//", "/");
                System.out.println("Checking if photo exists at: " + checkUrl);
                
                // Create OkHttpClient with authentication
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                
                // Create HEAD request to check if file exists
                String credential = okhttp3.Credentials.basic(username, password);
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(checkUrl)
                        .head()
                        .header("Authorization", credential)
                        .build();
                
                // Execute request
                okhttp3.Response response = client.newCall(request).execute();
                System.out.println("Check response code: " + response.code());
                
                // If response is successful, file exists
                if (response.isSuccessful()) {
                    return true;
                }
            }
            
            // File not found in any path
            return false;
        } catch (Exception e) {
            System.out.println("Error checking photo in cloud: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
