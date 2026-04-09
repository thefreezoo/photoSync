package com.example.pho;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class SyncStatusManager {
    private static final String PREF_NAME = "sync_status";
    private static final String KEY_SYNCED_PHOTOS = "synced_photos";
    
    private SharedPreferences preferences;
    
    public SyncStatusManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public void markAsSynced(String photoId) {
        Set<String> syncedPhotos = getSyncedPhotos();
        syncedPhotos.add(photoId);
        preferences.edit().putStringSet(KEY_SYNCED_PHOTOS, syncedPhotos).apply();
    }
    
    public void markAsNotSynced(String photoId) {
        Set<String> syncedPhotos = getSyncedPhotos();
        syncedPhotos.remove(photoId);
        preferences.edit().putStringSet(KEY_SYNCED_PHOTOS, syncedPhotos).apply();
    }
    
    public boolean isSynced(String photoId) {
        return getSyncedPhotos().contains(photoId);
    }
    
    public Set<String> getSyncedPhotos() {
        Set<String> syncedPhotos = preferences.getStringSet(KEY_SYNCED_PHOTOS, null);
        if (syncedPhotos == null) {
            return new HashSet<>();
        }
        return new HashSet<>(syncedPhotos);
    }
    
    public void clearSyncStatus() {
        preferences.edit().remove(KEY_SYNCED_PHOTOS).apply();
    }
}