package com.example.pho;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.CheckBox;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import android.provider.MediaStore;
import android.database.Cursor;
import androidx.core.app.NotificationCompat;

public class SettingsFragment extends Fragment {

    private Spinner protocolSpinner;
    private EditText urlEditText;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private EditText remotePathEditText;
    private Button testConnectionButton;
    private Button startSyncButton;
    private Button selectFoldersButton;
    private Button saveConfigButton;
    private Button clearCacheButton;
    private RecyclerView selectedFoldersList;
    private CheckBox includeSubfoldersCheckbox;
    private ProgressBar progressBar;
    private ArrayAdapter<CharSequence> protocolAdapter;
    private FolderAdapter foldersAdapter;
    private List<String> selectedFolders;
    
    // Collapsible views
    private LinearLayout cloudConfigHeader;
    private LinearLayout cloudConfigContent;
    private ImageView cloudConfigArrow;
    private LinearLayout localConfigHeader;
    private LinearLayout localConfigContent;
    private ImageView localConfigArrow;
    
    // Collapse states
    private boolean cloudConfigCollapsed = false;
    private boolean localConfigCollapsed = false;

    private static final String PREF_NAME = "webdav_settings";
    private static final String KEY_URL = "webdav_url";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";
    private static final String KEY_REMOTE_PATH = "webdav_remote_path";
    private static final String KEY_SELECTED_FOLDERS = "selected_folders";
    private static final String KEY_INCLUDE_SUBFOLDERS = "include_subfolders";
    
    // OnItemClickListener interface
    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        protocolSpinner = view.findViewById(R.id.protocol_spinner);
        urlEditText = view.findViewById(R.id.url_edit_text);
        usernameEditText = view.findViewById(R.id.username_edit_text);
        passwordEditText = view.findViewById(R.id.password_edit_text);
        remotePathEditText = view.findViewById(R.id.remote_path_edit_text);
        testConnectionButton = view.findViewById(R.id.test_connection_button);
        startSyncButton = view.findViewById(R.id.start_sync_button);
        selectFoldersButton = view.findViewById(R.id.select_folders_button);
        saveConfigButton = view.findViewById(R.id.save_config_button);
        clearCacheButton = view.findViewById(R.id.clear_cache_button);
        selectedFoldersList = view.findViewById(R.id.selected_folders_list);
        includeSubfoldersCheckbox = view.findViewById(R.id.include_subfolders_checkbox);
        progressBar = view.findViewById(R.id.progress_bar);
        
        // Initialize collapsible views
        cloudConfigHeader = view.findViewById(R.id.cloud_config_header);
        cloudConfigContent = view.findViewById(R.id.cloud_config_content);
        cloudConfigArrow = view.findViewById(R.id.cloud_config_arrow);
        localConfigHeader = view.findViewById(R.id.local_config_header);
        localConfigContent = view.findViewById(R.id.local_config_content);
        localConfigArrow = view.findViewById(R.id.local_config_arrow);
        
        // Set click listeners for collapsible headers
        cloudConfigHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCloudConfig();
            }
        });
        
        localConfigHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLocalConfig();
            }
        });

        // Initialize protocol spinner
        protocolAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.protocol_array, android.R.layout.simple_spinner_item);
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(protocolAdapter);

        // Initialize selected folders list
        selectedFolders = new ArrayList<>();
        foldersAdapter = new FolderAdapter(selectedFolders);
        selectedFoldersList.setLayoutManager(new LinearLayoutManager(getContext()));
        selectedFoldersList.setAdapter(foldersAdapter);

        testConnectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });

        startSyncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSync();
            }
        });

        selectFoldersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectFolders();
            }
        });

        saveConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
            }
        });

        clearCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCache();
            }
        });

        includeSubfoldersCheckbox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                saveSettings();
            }
        });

        // Add click listener to selected folders list for deletion
        foldersAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                // Show confirmation dialog to delete folder
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                builder.setTitle("删除文件夹")
                       .setMessage("确定要删除这个文件夹吗？")
                       .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(android.content.DialogInterface dialog, int which) {
                               selectedFolders.remove(position);
                               foldersAdapter.notifyDataSetChanged();
                               saveSettings();
                           }
                       })
                       .setNegativeButton("取消", null)
                       .show();
            }
        });

        // Load saved settings
        loadSettings();

        return view;
    }

    private void loadSettings() {
        if (getContext() != null) {
            android.content.SharedPreferences preferences = getContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
            String fullUrl = preferences.getString(KEY_URL, "");
            
            // Parse protocol and URL
            if (fullUrl.startsWith("https://")) {
                protocolSpinner.setSelection(1); // https
                urlEditText.setText(fullUrl.substring(8));
            } else if (fullUrl.startsWith("http://")) {
                protocolSpinner.setSelection(0); // http
                urlEditText.setText(fullUrl.substring(7));
            } else {
                protocolSpinner.setSelection(0); // default to http
                urlEditText.setText(fullUrl);
            }
            
            usernameEditText.setText(preferences.getString(KEY_USERNAME, ""));
            passwordEditText.setText(preferences.getString(KEY_PASSWORD, ""));
            remotePathEditText.setText(preferences.getString(KEY_REMOTE_PATH, "/photos"));
            
            // Load local folder settings
            Set<String> savedFolders = preferences.getStringSet(KEY_SELECTED_FOLDERS, null);
            if (savedFolders != null) {
                selectedFolders.clear();
                selectedFolders.addAll(savedFolders);
                foldersAdapter.notifyDataSetChanged();
            }
            
            boolean includeSubfolders = preferences.getBoolean(KEY_INCLUDE_SUBFOLDERS, true);
            includeSubfoldersCheckbox.setChecked(includeSubfolders);
        }
    }

    private void saveSettings() {
        if (getContext() != null) {
            android.content.SharedPreferences preferences = getContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = preferences.edit();
            
            // Combine protocol and URL
            String protocol = protocolSpinner.getSelectedItem().toString();
            String url = urlEditText.getText().toString().trim();
            String fullUrl = protocol + url;
            
            editor.putString(KEY_URL, fullUrl);
            editor.putString(KEY_USERNAME, usernameEditText.getText().toString().trim());
            editor.putString(KEY_PASSWORD, passwordEditText.getText().toString().trim());
            editor.putString(KEY_REMOTE_PATH, remotePathEditText.getText().toString().trim());
            
            // Save local folder settings
            Set<String> foldersSet = new HashSet<>(selectedFolders);
            editor.putStringSet(KEY_SELECTED_FOLDERS, foldersSet);
            editor.putBoolean(KEY_INCLUDE_SUBFOLDERS, includeSubfoldersCheckbox.isChecked());
            editor.commit();
        }
    }

    private void testConnection() {
        // Combine protocol and URL
        String protocol = protocolSpinner.getSelectedItem().toString();
        String url = urlEditText.getText().toString().trim();
        String fullUrl = protocol + url;
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), R.string.please_enter_url, Toast.LENGTH_SHORT).show();
            return;
        }

        // Save settings before testing connection
        saveSettings();

        new TestConnectionTask().execute(fullUrl, username, password);
    }

    private void selectFolders() {
        // For Android 10+, use system file picker to select folders
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addCategory(android.content.Intent.CATEGORY_DEFAULT);
            startActivityForResult(intent, 1001);
        } else {
            // For older versions, use legacy file picker
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            startActivityForResult(android.content.Intent.createChooser(intent, "选择文件夹"), 1001);
        }
    }

    private void saveConfig() {
        // Save settings
        saveSettings();
        
        // Notify MainActivity to refresh local photos
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            System.out.println("Calling activity.refreshLocalPhotos()");
            activity.refreshLocalPhotos();
        } else {
            System.out.println("Activity is null, cannot refresh local photos");
        }
        
        // Show success message
        Toast.makeText(getContext(), "配置已保存", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null) {
                android.net.Uri uri = data.getData();
                if (uri != null) {
                    // Get folder path from URI
                    String folderPath = getPathFromUri(uri);
                    if (folderPath != null && !selectedFolders.contains(folderPath)) {
                        selectedFolders.add(folderPath);
                        foldersAdapter.notifyDataSetChanged();
                        saveSettings();
                        Toast.makeText(getContext(), "文件夹已添加", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private String getPathFromUri(android.net.Uri uri) {
        String path = null;
        if (uri != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // For Android 10+, use DocumentFile
                androidx.documentfile.provider.DocumentFile docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(getContext(), uri);
                if (docFile != null && docFile.isDirectory()) {
                    path = docFile.getUri().toString();
                    // Decode URL encoded characters to make it more readable
                    try {
                        path = java.net.URLDecoder.decode(path, "UTF-8");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // For older versions, use ContentResolver
                String[] projection = {android.provider.MediaStore.MediaColumns.DATA};
                android.database.Cursor cursor = getContext().getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA);
                        path = cursor.getString(columnIndex);
                    }
                    cursor.close();
                }
            }
        }
        return path;
    }

    private void startSync() {
        // Combine protocol and URL
        String protocol = protocolSpinner.getSelectedItem().toString();
        String url = urlEditText.getText().toString().trim();
        String fullUrl = protocol + url;
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String remotePath = remotePathEditText.getText().toString().trim();

        if (url.isEmpty() || username.isEmpty() || password.isEmpty() || remotePath.isEmpty()) {
            Toast.makeText(getContext(), R.string.please_enter_url, Toast.LENGTH_SHORT).show();
            return;
        }

        // Save settings before starting sync
        saveSettings();

        new SyncTask().execute(fullUrl, username, password, remotePath);
    }

    // Toggle cloud config section
    private void toggleCloudConfig() {
        cloudConfigCollapsed = !cloudConfigCollapsed;
        if (cloudConfigCollapsed) {
            cloudConfigContent.setVisibility(View.GONE);
            cloudConfigArrow.setRotation(180);
        } else {
            cloudConfigContent.setVisibility(View.VISIBLE);
            cloudConfigArrow.setRotation(0);
        }
    }
    
    // Toggle local config section
    private void toggleLocalConfig() {
        localConfigCollapsed = !localConfigCollapsed;
        if (localConfigCollapsed) {
            localConfigContent.setVisibility(View.GONE);
            localConfigArrow.setRotation(180);
        } else {
            localConfigContent.setVisibility(View.VISIBLE);
            localConfigArrow.setRotation(0);
        }
    }

    private void clearCache() {
        // Show confirmation dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("清理缓存")
               .setMessage("确定要清理本地缩略图缓存吗？")
               .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(android.content.DialogInterface dialog, int which) {
                       // Clear Glide memory cache on main thread
                       if (getContext() != null) {
                           com.bumptech.glide.Glide.get(getContext()).clearMemory();
                       }
                       
                       // Clear Glide disk cache in a background thread
                       new Thread(new Runnable() {
                           @Override
                           public void run() {
                               try {
                                   if (getContext() != null) {
                                       // Clear Glide disk cache
                                       com.bumptech.glide.Glide.get(getContext()).clearDiskCache();
                                       
                                       // Show success message on UI thread
                                       getActivity().runOnUiThread(new Runnable() {
                                           @Override
                                           public void run() {
                                               Toast.makeText(getContext(), "缓存清理成功", Toast.LENGTH_SHORT).show();
                                           }
                                       });
                                   }
                               } catch (Exception e) {
                                   e.printStackTrace();
                                   // Show error message on UI thread
                                   if (getActivity() != null) {
                                       getActivity().runOnUiThread(new Runnable() {
                                           @Override
                                           public void run() {
                                               Toast.makeText(getContext(), "缓存清理失败", Toast.LENGTH_SHORT).show();
                                           }
                                       });
                                   }
                               }
                           }
                       }).start();
                   }
               })
               .setNegativeButton("取消", null)
               .show();
    }

    private class TestConnectionTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
            testConnectionButton.setEnabled(false);
            startSyncButton.setEnabled(false);
        }

        @Override
        protected String doInBackground(String... params) {
            String url = params[0];
            String username = params[1];
            String password = params[2];

            try {
                // 添加超时设置
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                // 直接在请求头中添加认证信息
                String credential = okhttp3.Credentials.basic(username, password);
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", credential)
                        .method("PROPFIND", null)
                        .header("Depth", "1")
                        .build();

                System.out.println("Testing connection to: " + url);
                System.out.println("Using username: " + username);
                
                Response response = client.newCall(request).execute();
                System.out.println("Response code: " + response.code());
                System.out.println("Response message: " + response.message());
                
                if (response.isSuccessful()) {
                    System.out.println("Connection successful!");
                    return "success";
                } else {
                    System.out.println("Connection failed: " + response.code() + " " + response.message());
                    return "连接失败: " + response.code() + " " + response.message();
                }
            } catch (Exception e) {
                System.out.println("Connection error: " + e.getMessage());
                e.printStackTrace();
                return "连接失败: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            progressBar.setVisibility(View.GONE);
            testConnectionButton.setEnabled(true);
            startSyncButton.setEnabled(true);

            if (result.equals("success")) {
                Toast.makeText(getContext(), R.string.connection_successful, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), result, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class SyncTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
            testConnectionButton.setEnabled(false);
            startSyncButton.setEnabled(false);
            System.out.println("Starting sync process...");
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String url = params[0];
            String username = params[1];
            String password = params[2];
            String remotePath = params[3];

            System.out.println("Syncing with WebDAV server: " + url);
            System.out.println("Username: " + username);
            System.out.println("Remote path: " + remotePath);

            try {
                // Get local photos from PhotosFragment (only the ones currently displayed)
                List<Photo> localPhotos = new ArrayList<>();
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    PhotosFragment photosFragment = (PhotosFragment) activity.getSupportFragmentManager().findFragmentByTag("photos");
                    if (photosFragment != null) {
                        localPhotos = photosFragment.getPhotoList();
                        System.out.println("Retrieved " + localPhotos.size() + " photos from PhotosFragment");
                    } else {
                        System.out.println("PhotosFragment not found, using fallback method");
                        localPhotos = getLocalPhotos();
                    }
                } else {
                    System.out.println("Activity is null, using fallback method");
                    localPhotos = getLocalPhotos();
                }
                
                if (localPhotos.isEmpty()) {
                    System.out.println("No local photos found");
                    return true;
                }

                // Initialize sync status manager
                final SyncStatusManager syncStatusManager = new SyncStatusManager(getContext());

                // Create thread pool for parallel processing
                int corePoolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
                java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(corePoolSize);
                System.out.println("Created thread pool with " + corePoolSize + " threads");

                // Create list of tasks
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (final Photo photo : localPhotos) {
                    futures.add(executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                System.out.println("Processing photo: " + photo.getName());
                                
                                // Generate folder path based on photo date
                                String folderPath = generateFolderPath(photo.getDateAdded());
                                System.out.println("Generated folder path: " + folderPath);
                                
                                // Generate WebDAV path
                                String webdavPath = remotePath + folderPath + "/" + photo.getName();
                                System.out.println("Generated WebDAV path: " + webdavPath);
                                
                                // Check if file exists in WebDAV
                                boolean exists = checkFileExists(url, username, password, webdavPath);
                                System.out.println("File exists in WebDAV: " + exists);
                                
                                // Update sync status if file exists
                                if (exists) {
                                    photo.setSynced(true);
                                    syncStatusManager.markAsSynced(String.valueOf(photo.getId()));
                                    System.out.println("Photo marked as synced: " + photo.getName());
                                }
                            } catch (Exception e) {
                                System.out.println("Error processing photo " + photo.getName() + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }));
                }

                // Wait for all tasks to complete
                for (java.util.concurrent.Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        System.out.println("Error waiting for task: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // Shutdown executor service
                executorService.shutdown();
                System.out.println("Sync completed successfully");
                return true;
            } catch (Exception e) {
                System.out.println("Sync error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            progressBar.setVisibility(View.GONE);
            testConnectionButton.setEnabled(true);
            startSyncButton.setEnabled(true);

            if (success) {
                Toast.makeText(getContext(), R.string.sync_completed, Toast.LENGTH_SHORT).show();
                System.out.println("Sync process completed, notifying MainActivity to check sync status");
                
                // Send notification
                sendSyncNotification(true);
                
                // Notify MainActivity to check sync status
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    System.out.println("Calling activity.checkSyncStatus()");
                    activity.checkSyncStatus();
                } else {
                    System.out.println("Activity is null, cannot call checkSyncStatus()");
                }
            } else {
                Toast.makeText(getContext(), R.string.sync_failed, Toast.LENGTH_SHORT).show();
                System.out.println("Sync process failed");
                
                // Send notification
                sendSyncNotification(false);
            }
        }
        
        private void sendSyncNotification(boolean success) {
            if (getContext() == null) {
                System.out.println("Context is null, cannot send notification");
                return;
            }
            
            // Create notification channel (required for Android 8.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                String channelId = "sync_channel";
                String channelName = "同步通知";
                String channelDescription = "照片同步状态通知";
                int importance = android.app.NotificationManager.IMPORTANCE_DEFAULT;
                
                android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, channelName, importance);
                channel.setDescription(channelDescription);
                
                android.app.NotificationManager notificationManager = getContext().getSystemService(android.app.NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            }
            
            // Create notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), "sync_channel")
                    .setSmallIcon(android.R.drawable.ic_menu_gallery)
                    .setContentTitle(success ? "同步完成" : "同步失败")
                    .setContentText(success ? "照片同步已完成" : "照片同步失败，请检查网络连接")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            
            // Get notification manager and show notification
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(1, builder.build());
                System.out.println("Sync notification sent");
            }
        }
        
        private List<Photo> getLocalPhotos() {
            List<Photo> photoList = new ArrayList<>();
            
            if (getContext() == null) {
                System.out.println("Context is null, cannot get local photos");
                return photoList;
            }
            
            String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED};
            String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

            try (Cursor cursor = getContext().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
            )) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                    int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
                    int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);

                    while (cursor.moveToNext()) { // Check all photos, no limit
                        long id = cursor.getLong(idColumn);
                        String name = cursor.getString(nameColumn);
                        long dateTaken = cursor.getLong(dateTakenColumn);
                        // Use date taken if available, otherwise use date added
                        long dateAdded = dateTaken > 0 ? dateTaken / 1000 : cursor.getLong(dateAddedColumn);

                        Photo photo = new Photo(id, name, String.valueOf(id), dateAdded, false);
                        photoList.add(photo);
                    }
                }
            } catch (Exception e) {
                System.out.println("Error getting local photos: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("Retrieved " + photoList.size() + " local photos");
            return photoList;
        }
        
        private String generateFolderPath(long timestamp) {
            // Generate folder path in year/month/day format
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault());
            java.util.Date date = new java.util.Date(timestamp * 1000); // Convert seconds to milliseconds
            return "/" + sdf.format(date);
        }
        
        private boolean checkFileExists(String webdavUrl, String username, String password, String filePath) {
            try {
                // Ensure URL ends with slash
                if (!webdavUrl.endsWith("/")) {
                    webdavUrl += "/";
                }
                
                // Create full URL
                String fullUrl = webdavUrl + filePath;
                // Remove double slashes except in protocol part
                fullUrl = fullUrl.replaceAll("(?<!:)//", "/");
                System.out.println("Checking file at: " + fullUrl);

                // Create OkHttpClient with authentication
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                // Create HEAD request to check if file exists
                String credential = okhttp3.Credentials.basic(username, password);
                Request request = new Request.Builder()
                        .url(fullUrl)
                        .head()
                        .header("Authorization", credential)
                        .build();

                // Execute request
                Response response = client.newCall(request).execute();
                System.out.println("Check response code: " + response.code());
                
                // If response is successful, file exists
                return response.isSuccessful();
            } catch (Exception e) {
                System.out.println("Error checking file existence: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }
    
    // FolderAdapter for RecyclerView
    private class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {
        private List<String> folders;
        private OnItemClickListener onItemClickListener;
        
        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }
        
        public FolderAdapter(List<String> folders) {
            this.folders = folders;
        }
        
        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Create a custom view with divider
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            
            // Add text view
            TextView textView = new TextView(parent.getContext());
            textView.setId(android.R.id.text1);
            textView.setTextSize(16);
            textView.setPadding(16, 16, 16, 16);
            layout.addView(textView);
            
            // Add divider
            View divider = new View(parent.getContext());
            divider.setBackgroundColor(android.graphics.Color.GRAY);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            layout.addView(divider, dividerParams);
            
            return new FolderViewHolder(layout);
        }
        
        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            String folder = folders.get(position);
            holder.folderName.setText(folder);
        }
        
        @Override
        public int getItemCount() {
            return folders.size();
        }
        
        public class FolderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView folderName;
            
            public FolderViewHolder(@NonNull View itemView) {
                super(itemView);
                folderName = itemView.findViewById(android.R.id.text1);
                itemView.setOnClickListener(this);
            }
            
            @Override
            public void onClick(View v) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(getAdapterPosition());
                }
            }
        }
    }
}
