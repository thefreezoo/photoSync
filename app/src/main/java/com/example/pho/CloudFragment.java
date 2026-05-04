package com.example.pho;

import android.content.Intent;
import android.content.ContentProvider;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CloudFragment extends Fragment {

    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView photoRecyclerView;
    private TextView noPhotosText;
    private ProgressBar progressBar;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabBackToTop;
    private TimelineAdapter timelineAdapter;
    private List<Photo> photoList;
    private android.content.BroadcastReceiver uploadCompletedReceiver;
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private int scrollPosition = 0;
    private int scrollOffset = 0;
    private boolean isFirstLoad = true;
    private boolean needRefresh = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cloud, container, false);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        photoRecyclerView = view.findViewById(R.id.photo_recycler);
        noPhotosText = view.findViewById(R.id.no_photos_text);
        progressBar = view.findViewById(R.id.progress_bar);
        fabBackToTop = view.findViewById(R.id.fab_back_to_top);
        photoList = new ArrayList<>();
        isFirstLoad = true;
        
        // Set up back to top button
        fabBackToTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                photoRecyclerView.smoothScrollToPosition(0);
            }
        });

        // Set up RecyclerView
        timelineAdapter = new TimelineAdapter(getContext(), photoList, new TimelineAdapter.OnPhotoClickListener() {
            @Override
            public void onPhotoClick(int position) {
                openImagePreview(position);
            }

            @Override
            public void onPhotoLongClick(int position) {
                // Cloud photos don't support selection and upload
            }
        });
        photoRecyclerView.setAdapter(timelineAdapter);
        
        // Add scroll listener for pagination, scroll position tracking, and fab visibility
        photoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                    
                    if (!isLoading && !isLastPage && lastVisibleItem >= totalItemCount - 5) {
                        loadMoreCloudPhotos();
                    }
                }
            }
        });

        // Set up swipe refresh
        swipeRefreshLayout.setOnRefreshListener(new androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadCloudPhotos();
            }
        });

        // Initialize broadcast receiver
        uploadCompletedReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                if ("com.example.pho.UPLOAD_COMPLETED".equals(intent.getAction())) {
                    // Refresh cloud photos when upload is completed
                    loadCloudPhotos();
                }
            }
        };

        // Load photos from persistent storage if available, otherwise load from server
        if (!loadPhotosFromStorage()) {
            // Only load photos from server on first app launch
            if (shouldLoadCloudPhotos()) {
                loadCloudPhotos();
                markCloudPhotosLoaded();
            }
        }

        return view;
    }
    
    private void openImagePreview(int position) {
        // Create a copy of photoList to avoid being affected by subsequent async loading
        List<Photo> currentPhotoList = new ArrayList<>(photoList);
        Photo photo = currentPhotoList.get(position);
        
        // Check if it's a video
        if (photo.isVideo()) {
            // It's a video, download to local cache first then open with system video player
            downloadAndPlayVideo(photo);
        } else {
            // It's an image, open image preview
            // Prepare data for preview
            ArrayList<Long> photoIds = new ArrayList<>();
            ArrayList<String> photoNames = new ArrayList<>();
            for (Photo p : currentPhotoList) {
                photoIds.add(p.getId());
                photoNames.add(p.getName());
            }

            // Open image preview activity
            Intent intent = new Intent(getActivity(), NewImagePreviewActivity.class);
            intent.putExtra("photo_ids", toLongArray(photoIds));
            intent.putExtra("photo_names", photoNames);
            intent.putExtra("current_position", position);
            // Add cloud photos flag
            intent.putExtra("is_cloud_photos", true);
            // Add cloud photos paths
            ArrayList<String> photoPaths = new ArrayList<>();
            for (Photo p : currentPhotoList) {
                photoPaths.add(p.getPath());
            }
            intent.putExtra("photo_paths", photoPaths);
            
            // Start activity with animation
            startActivity(intent);
            getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }
    
    private void downloadAndPlayVideo(Photo photo) {
        // Show loading indicator using AlertDialog instead of ProgressDialog
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
        builder.setTitle("下载视频");
        builder.setMessage("正在下载视频，请稍候...");
        builder.setCancelable(false);
        final android.app.AlertDialog alertDialog = builder.create();
        alertDialog.show();
        HashMap<Object, Object> objectObjectHashMap = new HashMap<>();

        new Thread(() -> {
            try {
                // Get WebDAV settings
                android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
                String webdavUrl = preferences.getString("webdav_url", "");
                String webdavUsername = preferences.getString("webdav_username", "");
                String webdavPassword = preferences.getString("webdav_password", "");
                
                // Also check the old shared preferences name for compatibility
                if (webdavUrl.isEmpty() || webdavUsername.isEmpty() || webdavPassword.isEmpty()) {
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
                }
                
                // Create OkHttpClient with authentication
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                
                // Create request with authentication
                String credential = okhttp3.Credentials.basic(webdavUsername, webdavPassword);
                Request request = new Request.Builder()
                        .url(photo.getPath())
                        .header("Authorization", credential)
                        .build();
                
                // Execute request
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new Exception("Failed to download video: " + response.code());
                }
                
                // Create cache file
                String fileName = photo.getName();
                java.io.File cacheDir = getContext().getCacheDir();
                java.io.File videoFile = new java.io.File(cacheDir, fileName);
                
                // Write response body to cache file
                try (java.io.InputStream inputStream = response.body().byteStream();
                     java.io.FileOutputStream outputStream = new java.io.FileOutputStream(videoFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                // Close dialog and open video
                getActivity().runOnUiThread(() -> {
                    alertDialog.dismiss();
                    
                    // Open video with system player
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri videoUri = androidx.core.content.FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", videoFile);
                    intent.setDataAndType(videoUri, "video/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                    getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> {
                    alertDialog.dismiss();
                    Toast.makeText(getContext(), "下载视频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private long[] toLongArray(ArrayList<Long> list) {
        long[] array = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private void loadCloudPhotos() {
        // 检查WebDAV连接状态
        if (!isWebDavConnected()) {
            // 未连接，显示空列表
            photoList.clear();
            timelineAdapter.updatePhotos(photoList);
            updateUI();
            return;
        }
        
        // 避免重复加载
        if (isLoading) {
            System.out.println("loadCloudPhotos: Already loading, skipping");
            return;
        }
        
        // Reset pagination variables
        currentPage = 1;
        isLastPage = false;
        isLoading = true;
        photoList.clear();
        
        System.out.println("loadCloudPhotos: Starting to load cloud photos");
        new LoadCloudPhotosTask().execute();
    }
    
    private void loadMoreCloudPhotos() {
        if (isLoading || isLastPage) return;
        
        isLoading = true;
        new LoadMoreCloudPhotosTask().execute();
    }

    private boolean isWebDavConnected() {
        // 检查WebDAV连接状态
        // 在实际应用中，这里应该检查WebDAV服务器是否可访问
        // 这里从SettingsFragment的SharedPreferences中获取设置，判断是否已配置WebDAV
        if (getContext() != null) {
            android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
            String url = preferences.getString("webdav_url", "");
            String username = preferences.getString("webdav_username", "");
            String password = preferences.getString("webdav_password", "");
            // 只有当所有必要的参数都设置了，才认为连接成功
            return !url.isEmpty() && !username.isEmpty() && !password.isEmpty();
        }
        return false;
    }

    private class LoadCloudPhotosTask extends AsyncTask<Void, Void, List<Photo>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<Photo> doInBackground(Void... voids) {
            // 从WebDAV服务器加载图片
            try {
                if (getContext() == null) {
                    return null;
                }
                
                // 获取WebDAV设置
                android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
                String fullUrl = preferences.getString("webdav_url", "");
                String username = preferences.getString("webdav_username", "");
                String password = preferences.getString("webdav_password", "");
                String remotePath = preferences.getString("webdav_remote_path", "");
                
                // 构建WebDAV URL
                String webdavUrl = fullUrl;
                if (!webdavUrl.endsWith("/")) {
                    webdavUrl += "/";
                }
                
                // 构建完整的远程路径
                if (!remotePath.isEmpty() && !remotePath.startsWith("/")) {
                    remotePath = "/" + remotePath;
                }
                if (!remotePath.isEmpty() && !remotePath.endsWith("/")) {
                    remotePath += "/";
                }
                
                String fullBaseUrl = webdavUrl + remotePath;
                // 移除双斜杠
                fullBaseUrl = fullBaseUrl.replaceAll("(?<!:)//", "/");
                
                // 直接加载所有图片，不使用多线程
                List<Photo> allPhotos = loadPhotosFromFolder(fullBaseUrl, username, password);
                
                // 按时间排序（最新的在前）
                Collections.sort(allPhotos, new Comparator<Photo>() {
                    @Override
                    public int compare(Photo p1, Photo p2) {
                        return Long.compare(p2.getDateAdded(), p1.getDateAdded());
                    }
                });
                
                return allPhotos;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error loading cloud photos: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Photo> photos) {
            super.onPostExecute(photos);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            isLoading = false;
            
            System.out.println("=== LoadCloudPhotosTask.onPostExecute() started ===");
            
            if (photos != null) {
                System.out.println("Number of photos loaded: " + photos.size());
                
                // 清空现有列表
                photoList.clear();
                System.out.println("Cleared photoList, size: " + photoList.size());
                
                // 检查并添加唯一的照片
                int duplicateCount = 0;
                for (Photo photo : photos) {
                    boolean isDuplicate = false;
                    for (Photo existingPhoto : photoList) {
                        if (photo.getId() == existingPhoto.getId()) {
                            isDuplicate = true;
                            duplicateCount++;
                            System.out.println("Found duplicate photo: " + photo.getName() + " (ID: " + photo.getId() + ")");
                            break;
                        }
                    }
                    if (!isDuplicate) {
                        photoList.add(photo);
                        System.out.println("Added photo: " + photo.getName() + " (ID: " + photo.getId() + ")");
                    }
                }
                
                System.out.println("After deduplication, photoList size: " + photoList.size() + ", duplicates found: " + duplicateCount);
                
                System.out.println("Calling timelineAdapter.updatePhotos()");
                timelineAdapter.updatePhotos(photoList);
                updateUI();
                
                // Save photos to persistent storage
                savePhotosToStorage();
                
                // Check if there are more photos
                isLastPage = photoList.size() < PAGE_SIZE;
                System.out.println("isLastPage: " + isLastPage + ", photoList.size(): " + photoList.size() + ", PAGE_SIZE: " + PAGE_SIZE);
                
                // Reset scroll position when refreshing
                scrollPosition = 0;
                scrollOffset = 0;
                System.out.println("Reset scroll position");
            } else {
                System.out.println("Photos is null, loading failed");
                Toast.makeText(getContext(), "加载云端图片失败", Toast.LENGTH_SHORT).show();
            }
            
            System.out.println("=== LoadCloudPhotosTask.onPostExecute() completed ===");
        }
    }
    
    private class LoadMoreCloudPhotosTask extends AsyncTask<Void, Void, List<Photo>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<Photo> doInBackground(Void... voids) {
            // 从WebDAV服务器加载更多图片
            try {
                if (getContext() == null) {
                    return null;
                }
                
                // 获取WebDAV设置
                android.content.SharedPreferences preferences = getContext().getSharedPreferences("webdav_settings", android.content.Context.MODE_PRIVATE);
                String fullUrl = preferences.getString("webdav_url", "");
                String username = preferences.getString("webdav_username", "");
                String password = preferences.getString("webdav_password", "");
                String remotePath = preferences.getString("webdav_remote_path", "");
                
                // 构建WebDAV URL
                String webdavUrl = fullUrl;
                if (!webdavUrl.endsWith("/")) {
                    webdavUrl += "/";
                }
                
                // 构建完整的远程路径
                if (!remotePath.isEmpty() && !remotePath.startsWith("/")) {
                    remotePath = "/" + remotePath;
                }
                if (!remotePath.isEmpty() && !remotePath.endsWith("/")) {
                    remotePath += "/";
                }
                
                String fullBaseUrl = webdavUrl + remotePath;
                // 移除双斜杠
                fullBaseUrl = fullBaseUrl.replaceAll("(?<!:)//", "/");
                
                // 直接加载所有图片，不使用多线程
                List<Photo> allPhotos = loadPhotosFromFolder(fullBaseUrl, username, password);
                
                // 按时间排序（最新的在前）
                Collections.sort(allPhotos, new Comparator<Photo>() {
                    @Override
                    public int compare(Photo p1, Photo p2) {
                        return Long.compare(p2.getDateAdded(), p1.getDateAdded());
                    }
                });
                
                // 计算分页
                int start = currentPage * PAGE_SIZE;
                int end = start + PAGE_SIZE;
                if (start >= allPhotos.size()) {
                    return new ArrayList<>();
                }
                if (end > allPhotos.size()) {
                    end = allPhotos.size();
                }
                
                return allPhotos.subList(start, end);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error loading more cloud photos: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Photo> photos) {
            super.onPostExecute(photos);
            progressBar.setVisibility(View.GONE);
            isLoading = false;
            
            System.out.println("=== LoadMoreCloudPhotosTask.onPostExecute() started ===");
            
            if (photos != null) {
                System.out.println("Number of photos loaded: " + photos.size());
                
                if (!photos.isEmpty()) {
                    System.out.println("Photos is not empty, checking for duplicates");
                    
                    // 检查并移除重复图片
                    List<Photo> uniquePhotos = new ArrayList<>();
                    int duplicateCount = 0;
                    for (Photo photo : photos) {
                        boolean isDuplicate = false;
                        for (Photo existingPhoto : photoList) {
                            if (photo.getId() == existingPhoto.getId()) {
                                isDuplicate = true;
                                duplicateCount++;
                                System.out.println("Found duplicate photo: " + photo.getName() + " (ID: " + photo.getId() + ")");
                                break;
                            }
                        }
                        if (!isDuplicate) {
                            uniquePhotos.add(photo);
                            System.out.println("Added photo: " + photo.getName() + " (ID: " + photo.getId() + ")");
                        }
                    }
                    
                    System.out.println("After deduplication, uniquePhotos size: " + uniquePhotos.size() + ", duplicates found: " + duplicateCount);
                    
                    if (!uniquePhotos.isEmpty()) {
                        System.out.println("Adding unique photos to photoList");
                        photoList.addAll(uniquePhotos);
                        System.out.println("photoList size after addition: " + photoList.size());
                        
                        System.out.println("Calling timelineAdapter.updatePhotos()");
                        timelineAdapter.updatePhotos(photoList);
                        updateUI();
                        currentPage++;
                        System.out.println("Current page incremented to: " + currentPage);
                    }
                } else {
                    System.out.println("Photos is empty");
                }
                
                // Check if there are more photos
                isLastPage = photos.size() < PAGE_SIZE;
                System.out.println("isLastPage: " + isLastPage + ", photos.size(): " + photos.size() + ", PAGE_SIZE: " + PAGE_SIZE);
            } else {
                System.out.println("Photos is null, loading failed");
                Toast.makeText(getContext(), "加载更多云端图片失败", Toast.LENGTH_SHORT).show();
            }
            
            System.out.println("=== LoadMoreCloudPhotosTask.onPostExecute() completed ===");
        }
    }

    private void updateUI() {
        if (photoList.isEmpty()) {
            noPhotosText.setVisibility(View.VISIBLE);
            photoRecyclerView.setVisibility(View.GONE);
        } else {
            noPhotosText.setVisibility(View.GONE);
            photoRecyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    private long extractDateFromPath(String path) {
        // 从路径中提取年月日信息，格式为 /YYYY/MM/DD
        try {
            // 分割路径
            String[] parts = path.split("/");
            
            // 查找年月日路径部分
            for (int i = 0; i < parts.length - 2; i++) {
                try {
                    int year = Integer.parseInt(parts[i]);
                    int month = Integer.parseInt(parts[i + 1]);
                    int day = Integer.parseInt(parts[i + 2]);
                    
                    // 验证日期是否有效
                    if (year >= 2000 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                        // 创建日期对象
                        java.util.Calendar calendar = java.util.Calendar.getInstance();
                        calendar.set(year, month - 1, day, 0, 0, 0);
                        calendar.set(java.util.Calendar.MILLISECOND, 0);
                        
                        // 转换为秒时间戳
                        return calendar.getTimeInMillis() / 1000;
                    }
                } catch (NumberFormatException e) {
                    // 不是数字，继续查找
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 提取失败，返回0
        return 0;
    }

    private List<String> parseWebDavFolders(String responseBody, String baseUrl) {
        List<String> folders = new ArrayList<>();
        
        try {
            // 解析XML响应
            org.xmlpull.v1.XmlPullParserFactory factory = org.xmlpull.v1.XmlPullParserFactory.newInstance();
            org.xmlpull.v1.XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.StringReader(responseBody));
            
            int eventType = parser.getEventType();
            String currentPath = "";
            boolean isFolder = false;
            
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    
                    if (tagName.equals("D:response")) {
                        // 开始处理一个新的响应项
                        currentPath = "";
                        isFolder = false;
                    } else if (tagName.equals("D:href")) {
                        // 获取文件路径
                        currentPath = parser.nextText();
                    } else if (tagName.equals("D:resourcetype")) {
                        // 检查是否是文件夹
                        int nextEventType = parser.next();
                        if (nextEventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.getName().equals("D:collection")) {
                            // 这是一个文件夹
                            isFolder = true;
                        } else {
                            // 这是一个文件
                            isFolder = false;
                        }
                    }
                } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    if (parser.getName().equals("D:response")) {
                        // 结束处理一个响应项
                        if (isFolder && currentPath != null && !currentPath.isEmpty()) {
                            // 构建完整的文件夹URL
                            String folderUrl;
                            
                            // 检查currentPath是否已经是完整的URL
                            if (currentPath.startsWith("http://") || currentPath.startsWith("https://")) {
                                // 如果currentPath已经是完整的URL，直接使用
                                folderUrl = currentPath;
                            } else {
                                // 否则，从baseUrl中提取协议和主机部分
                                String protocol = "http://";
                                String host = "";
                                
                                if (baseUrl.startsWith("https://")) {
                                    protocol = "https://";
                                    host = baseUrl.substring(8);
                                } else if (baseUrl.startsWith("http://")) {
                                    host = baseUrl.substring(7);
                                }
                                
                                // 提取主机部分（不包含路径）
                                int pathStartIndex = host.indexOf("/");
                                if (pathStartIndex != -1) {
                                    host = host.substring(0, pathStartIndex);
                                }
                                
                                // 构建完整的URL：协议 + 主机 + currentPath
                                folderUrl = protocol + host + currentPath;
                            }
                            
                            // 移除双斜杠
                            folderUrl = folderUrl.replaceAll("(?<!:)//", "/");
                            
                            // 只添加非空的文件夹URL
                            if (!folderUrl.equals(baseUrl)) {
                                // 检查是否已经添加过该文件夹
                                boolean alreadyAdded = false;
                                for (String existingFolder : folders) {
                                    if (existingFolder.equals(folderUrl)) {
                                        alreadyAdded = true;
                                        break;
                                    }
                                }
                                if (!alreadyAdded) {
                                    folders.add(folderUrl);
                                }
                            }
                        }
                    }
                }
                eventType = parser.next();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // 如果解析失败，返回空列表
            folders.clear();
        }
        
        return folders;
    }
    
    private List<Photo> loadPhotosFromFolder(String folderUrl, String username, String password) throws Exception {
        List<Photo> photos = new ArrayList<>();
        
        // 使用OkHttpClient连接WebDAV服务器
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        // 创建PROPFIND请求
        String credential = okhttp3.Credentials.basic(username, password);
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(folderUrl)
                .method("PROPFIND", null)
                .header("Authorization", credential)
                .header("Depth", "infinity") // 递归获取所有子文件夹和文件
                .build();
        
        // 执行请求
        okhttp3.Response response = client.newCall(request).execute();
        
        if (!response.isSuccessful()) {
            System.out.println("WebDAV PROPFIND failed for folder " + folderUrl + ": " + response.message());
            return photos;
        }
        
        // 解析响应
        String responseBody = response.body().string();
        
        // 解析WebDAV响应，获取图片
        try {
            // 解析XML响应
            org.xmlpull.v1.XmlPullParserFactory factory = org.xmlpull.v1.XmlPullParserFactory.newInstance();
            org.xmlpull.v1.XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.StringReader(responseBody));
            
            int eventType = parser.getEventType();
            String currentPath = "";
            String currentName = "";
            long currentTime = 0;
            boolean isFile = false;
            
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    
                    if (tagName.equals("D:response")) {
                        // 开始处理一个新的响应项
                        currentPath = "";
                        currentName = "";
                        currentTime = 0;
                        isFile = false;
                    } else if (tagName.equals("D:href")) {
                        // 获取文件路径
                        currentPath = parser.nextText();
                        // 提取文件名
                        int lastSlashIndex = currentPath.lastIndexOf("/");
                        if (lastSlashIndex != -1 && lastSlashIndex < currentPath.length() - 1) {
                            currentName = currentPath.substring(lastSlashIndex + 1);
                        }
                    } else if (tagName.equals("D:displayname")) {
                        // 获取显示名称
                        currentName = parser.nextText();
                    } else if (tagName.equals("D:getlastmodified")) {
                        // 获取最后修改时间
                        String modifiedStr = parser.nextText();
                        try {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US);
                            java.util.Date date = sdf.parse(modifiedStr);
                            currentTime = date.getTime();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (tagName.equals("D:resourcetype")) {
                        // 检查是否是文件
                        int nextEventType = parser.next();
                        if (nextEventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.getName().equals("D:collection")) {
                            // 这是一个文件夹，跳过
                            isFile = false;
                        } else {
                            // 这是一个文件
                            isFile = true;
                        }
                    }
                } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    if (parser.getName().equals("D:response")) {
                        // 结束处理一个响应项
                        if (isFile && currentName != null && !currentName.isEmpty()) {
                            // 检查是否是图片或视频文件
                            String lowerName = currentName.toLowerCase();
                            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif") || 
                                lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".avi") || lowerName.endsWith(".wmv") || 
                                lowerName.endsWith(".mkv") || lowerName.endsWith(".flv") || lowerName.endsWith(".3gp") || lowerName.endsWith(".webm")) {
                                // 创建图片URL
                                // 直接使用WebDAV服务器返回的路径，避免路径重复
                                String photoUrl;
                                
                                // 检查currentPath是否已经是完整的URL
                                if (currentPath.startsWith("http://") || currentPath.startsWith("https://")) {
                                    // 如果currentPath已经是完整的URL，直接使用
                                    photoUrl = currentPath;
                                } else {
                                    // 否则，从folderUrl中提取协议和主机部分
                                    String protocol = "http://";
                                    String host = "";
                                    
                                    if (folderUrl.startsWith("https://")) {
                                        protocol = "https://";
                                        host = folderUrl.substring(8);
                                    } else if (folderUrl.startsWith("http://")) {
                                        host = folderUrl.substring(7);
                                    }
                                    
                                    // 提取主机部分（不包含路径）
                                    int pathStartIndex = host.indexOf("/");
                                    if (pathStartIndex != -1) {
                                        host = host.substring(0, pathStartIndex);
                                    }
                                    
                                    // 构建完整的URL：协议 + 主机 + currentPath
                                    photoUrl = protocol + host + currentPath;
                                }
                                
                                // 移除双斜杠
                                photoUrl = photoUrl.replaceAll("(?<!:)//", "/");
                                
                                // 创建Photo对象（云端图片不需要同步状态标识）
                                // 使用文件名的哈希值作为稳定的ID，避免重复
                                // 只使用文件名的哈希值，不使用路径，这样相同的照片即使在不同的路径下也会有相同的ID
                                long photoId = currentName.hashCode();
                                // 确保ID为正数
                                if (photoId < 0) {
                                    photoId = -photoId;
                                }
                                // 从文件路径中提取年月日信息
                                long dateAddedInSeconds = extractDateFromPath(currentPath);
                                // 如果从路径中提取失败，使用最后修改时间
                                if (dateAddedInSeconds == 0 && currentTime > 0) {
                                    dateAddedInSeconds = currentTime / 1000;
                                }
                                Photo photo = new Photo(photoId, currentName, photoUrl, dateAddedInSeconds, false);
                                
                                // 检查是否已经添加过相同ID的图片
                                boolean alreadyAdded = false;
                                for (Photo existingPhoto : photos) {
                                    if (existingPhoto.getId() == photo.getId()) {
                                        alreadyAdded = true;
                                        break;
                                    }
                                }
                                if (!alreadyAdded) {
                                    System.out.println("Adding photo: " + currentName + " (ID: " + photoId + ")");
                                    photos.add(photo);
                                } else {
                                    System.out.println("Skipping duplicate photo: " + currentName + " (ID: " + photoId + ")");
                                }
                            }
                        }
                    }
                }
                eventType = parser.next();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // 如果解析失败，返回空列表
            photos.clear();
        }
        
        return photos;
    }
    
    private List<Photo> parseWebDavResponse(String responseBody, String baseUrl) {
        List<Photo> photos = new ArrayList<>();
        
        try {
            // 解析XML响应
            org.xmlpull.v1.XmlPullParserFactory factory = org.xmlpull.v1.XmlPullParserFactory.newInstance();
            org.xmlpull.v1.XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.StringReader(responseBody));
            
            int eventType = parser.getEventType();
            String currentPath = "";
            String currentName = "";
            long currentTime = 0;
            boolean isFile = false;
            
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    
                    if (tagName.equals("D:response")) {
                        // 开始处理一个新的响应项
                        currentPath = "";
                        currentName = "";
                        currentTime = 0;
                        isFile = false;
                    } else if (tagName.equals("D:href")) {
                        // 获取文件路径
                        currentPath = parser.nextText();
                        // 提取文件名
                        int lastSlashIndex = currentPath.lastIndexOf("/");
                        if (lastSlashIndex != -1 && lastSlashIndex < currentPath.length() - 1) {
                            currentName = currentPath.substring(lastSlashIndex + 1);
                        }
                    } else if (tagName.equals("D:displayname")) {
                        // 获取显示名称
                        currentName = parser.nextText();
                    } else if (tagName.equals("D:getlastmodified")) {
                        // 获取最后修改时间
                        String modifiedStr = parser.nextText();
                        try {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US);
                            java.util.Date date = sdf.parse(modifiedStr);
                            currentTime = date.getTime();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (tagName.equals("D:resourcetype")) {
                        // 检查是否是文件
                        int nextEventType = parser.next();
                        if (nextEventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.getName().equals("D:collection")) {
                            // 这是一个文件夹
                            isFile = false;
                        } else {
                            // 这是一个文件
                            isFile = true;
                        }
                    }
                } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    if (parser.getName().equals("D:response")) {
                        // 结束处理一个响应项
                        if (isFile && currentName != null && !currentName.isEmpty()) {
                            // 检查是否是图片或视频文件
                            String lowerName = currentName.toLowerCase();
                            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif") || 
                                lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".avi") || lowerName.endsWith(".wmv") || 
                                lowerName.endsWith(".mkv") || lowerName.endsWith(".flv") || lowerName.endsWith(".3gp") || lowerName.endsWith(".webm")) {
                                // 创建图片URL
                                // 直接使用WebDAV服务器返回的路径，避免路径重复
                                String photoUrl;
                                
                                // 检查currentPath是否已经是完整的URL
                                if (currentPath.startsWith("http://") || currentPath.startsWith("https://")) {
                                    // 如果currentPath已经是完整的URL，直接使用
                                    photoUrl = currentPath;
                                } else {
                                    // 否则，从baseUrl中提取协议和主机部分
                                    String protocol = "http://";
                                    String host = "";
                                    
                                    if (baseUrl.startsWith("https://")) {
                                        protocol = "https://";
                                        host = baseUrl.substring(8);
                                    } else if (baseUrl.startsWith("http://")) {
                                        host = baseUrl.substring(7);
                                    }
                                    
                                    // 提取主机部分（不包含路径）
                                    int pathStartIndex = host.indexOf("/");
                                    if (pathStartIndex != -1) {
                                        host = host.substring(0, pathStartIndex);
                                    }
                                    
                                    // 构建完整的URL：协议 + 主机 + currentPath
                                    photoUrl = protocol + host + currentPath;
                                }
                                
                                // 移除双斜杠
                                photoUrl = photoUrl.replaceAll("(?<!:)//", "/");
                                
                                // 创建Photo对象（云端图片不需要同步状态标识）
                                // 使用文件名的哈希值作为稳定的ID，避免重复
                                // 只使用文件名的哈希值，不使用路径，这样相同的照片即使在不同的路径下也会有相同的ID
                                long photoId = currentName.hashCode();
                                // 确保ID为正数
                                if (photoId < 0) {
                                    photoId = -photoId;
                                }
                                // 从文件路径中提取年月日信息
                                long dateAddedInSeconds = extractDateFromPath(currentPath);
                                // 如果从路径中提取失败，使用最后修改时间
                                if (dateAddedInSeconds == 0 && currentTime > 0) {
                                    dateAddedInSeconds = currentTime / 1000;
                                }
                                Photo photo = new Photo(photoId, currentName, photoUrl, dateAddedInSeconds, false);
                                
                                // 检查是否已经添加过相同ID的图片
                                boolean alreadyAdded = false;
                                for (Photo existingPhoto : photos) {
                                    if (existingPhoto.getId() == photo.getId()) {
                                        alreadyAdded = true;
                                        break;
                                    }
                                }
                                if (!alreadyAdded) {
                                    System.out.println("Adding photo: " + currentName + " (ID: " + photoId + ")");
                                    photos.add(photo);
                                } else {
                                    System.out.println("Skipping duplicate photo: " + currentName + " (ID: " + photoId + ")");
                                }
                            }
                        }
                    }
                }
                eventType = parser.next();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // 如果解析失败，返回空列表
            photos.clear();
        }
        
        // 按时间排序（最新的在前）
        photos.sort((photo1, photo2) -> Long.compare(photo2.getDateAdded(), photo1.getDateAdded()));
        
        return photos;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register broadcast receiver
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext()).registerReceiver(
                uploadCompletedReceiver, 
                new android.content.IntentFilter("com.example.pho.UPLOAD_COMPLETED")
        );
        
        // Only load photos if refresh is explicitly needed
        // Don't refresh when switching from other interfaces
        if (needRefresh) {
            // Load photos when refresh is needed
            loadCloudPhotos();
            needRefresh = false;
        } else {
            // Restore scroll position with offset
            if (scrollPosition > 0 && photoRecyclerView != null) {
                photoRecyclerView.post(() -> {
                    photoRecyclerView.scrollToPosition(scrollPosition);
                    // Then apply the offset
                    RecyclerView.LayoutManager layoutManager = photoRecyclerView.getLayoutManager();
                    if (layoutManager instanceof LinearLayoutManager) {
                        View firstVisibleView = layoutManager.findViewByPosition(scrollPosition);
                        if (firstVisibleView != null) {
                            int currentOffset = firstVisibleView.getTop();
                            int offsetDiff = scrollOffset - currentOffset;
                            if (offsetDiff != 0) {
                                photoRecyclerView.scrollBy(0, offsetDiff);
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
        if (photoRecyclerView != null) {
            RecyclerView.LayoutManager layoutManager = photoRecyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                int firstVisiblePosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                View firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition);
                if (firstVisibleView != null) {
                    scrollPosition = firstVisiblePosition;
                    scrollOffset = firstVisibleView.getTop();
                }
            }
        }
        
        // Save photos to persistent storage when fragment is paused
        savePhotosToStorage();
        
        // Unregister broadcast receiver
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(uploadCompletedReceiver);
    }
    
    // Method to trigger a refresh (e.g., after upload)
    public void refreshPhotos() {
        needRefresh = true;
        if (isResumed()) {
            loadCloudPhotos();
            needRefresh = false;
        }
    }
    
    // Check if cloud photos should be loaded (only on first app launch)
    private boolean shouldLoadCloudPhotos() {
        if (getContext() == null) {
            return false;
        }
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("cloud_photos", android.content.Context.MODE_PRIVATE);
        return !preferences.getBoolean("cloud_photos_loaded", false);
    }
    
    // Mark cloud photos as loaded
    private void markCloudPhotosLoaded() {
        if (getContext() == null) {
            return;
        }
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("cloud_photos", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("cloud_photos_loaded", true);
        editor.apply();
    }
    
    // Reset cloud photos loaded status (for debugging or user-initiated refresh)
    public void resetCloudPhotosLoadedStatus() {
        if (getContext() == null) {
            return;
        }
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("cloud_photos", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("cloud_photos_loaded", false);
        editor.apply();
    }
    
    // Load photos from persistent storage
    private boolean loadPhotosFromStorage() {
        if (getContext() == null) {
            return false;
        }
        
        try {
            android.content.SharedPreferences preferences = getContext().getSharedPreferences("cloud_photos", android.content.Context.MODE_PRIVATE);
            String photosJson = preferences.getString("cloud_photos_data", null);
            
            if (photosJson != null) {
                // Parse JSON and load photos
                org.json.JSONArray jsonArray = new org.json.JSONArray(photosJson);
                List<Photo> loadedPhotos = new ArrayList<>();
                
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
                    long id = jsonObject.getLong("id");
                    String name = jsonObject.getString("name");
                    String path = jsonObject.getString("path");
                    long dateAdded = jsonObject.getLong("dateAdded");
                    boolean synced = jsonObject.getBoolean("synced");
                    
                    Photo photo = new Photo(id, name, path, dateAdded, synced);
                    loadedPhotos.add(photo);
                }
                
                if (!loadedPhotos.isEmpty()) {
                    photoList.clear();
                    photoList.addAll(loadedPhotos);
                    timelineAdapter.updatePhotos(photoList);
                    updateUI();
                    System.out.println("Loaded " + loadedPhotos.size() + " photos from storage");
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error loading photos from storage: " + e.getMessage());
        }
        
        return false;
    }
    
    // Save photos to persistent storage
    private void savePhotosToStorage() {
        if (getContext() == null || photoList.isEmpty()) {
            return;
        }
        
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            
            for (Photo photo : photoList) {
                org.json.JSONObject jsonObject = new org.json.JSONObject();
                jsonObject.put("id", photo.getId());
                jsonObject.put("name", photo.getName());
                jsonObject.put("path", photo.getPath());
                jsonObject.put("dateAdded", photo.getDateAdded());
                jsonObject.put("synced", photo.isSynced());
                jsonArray.put(jsonObject);
            }
            
            android.content.SharedPreferences preferences = getContext().getSharedPreferences("cloud_photos", android.content.Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = preferences.edit();
            editor.putString("cloud_photos_data", jsonArray.toString());
            editor.apply();
            
            System.out.println("Saved " + photoList.size() + " photos to storage");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error saving photos to storage: " + e.getMessage());
        }
    }
}
