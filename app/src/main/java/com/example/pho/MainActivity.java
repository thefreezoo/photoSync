package com.example.pho;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;
    private BottomNavigationView bottomNavigationView;
    private PhotosFragment photosFragment;
    private CloudFragment cloudFragment;
    private SettingsFragment settingsFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化崩溃处理器
        CrashHandler.getInstance().init(this);
        // Hide the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_main);

        // Request storage permission on app start
        requestStoragePermission();

        // Initialize fragments
        photosFragment = new PhotosFragment();
        cloudFragment = new CloudFragment();
        settingsFragment = new SettingsFragment();

        // Add all fragments to the container
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.fragment_container, photosFragment, "photos");
        fragmentTransaction.add(R.id.fragment_container, cloudFragment, "cloud");
        fragmentTransaction.add(R.id.fragment_container, settingsFragment, "settings");
        fragmentTransaction.hide(cloudFragment);
        fragmentTransaction.hide(settingsFragment);
        fragmentTransaction.commit();

        currentFragment = photosFragment;

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.nav_photos) {
                    switchFragment(photosFragment);
                } else if (itemId == R.id.nav_sync) {
                    switchFragment(cloudFragment);
                } else if (itemId == R.id.nav_settings) {
                    switchFragment(settingsFragment);
                }

                return true;
            }
        });

        // Load default fragment
        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_photos);
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_PERMISSION);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Pass the permission result to all fragments
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment != null) {
                fragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    private void switchFragment(Fragment targetFragment) {
        if (currentFragment == targetFragment) {
            return; // Already showing this fragment
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.hide(currentFragment);
        fragmentTransaction.show(targetFragment);
        fragmentTransaction.commit();

        currentFragment = targetFragment;
    }

    private void loadFragment(Fragment fragment) {
        // This method is now deprecated, use switchFragment instead
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }

    public void checkSyncStatus() {
        // Use the saved photosFragment instance
        if (photosFragment != null) {
            photosFragment.checkSyncStatus();
        }
    }

    public void refreshLocalPhotos() {
        // Use the saved photosFragment instance
        if (photosFragment != null) {
            photosFragment.refreshPhotos();
        }
    }
}
