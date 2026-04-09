package com.example.pho;

public class SyncRecord {
    private int id;
    private String photoName;
    private String remotePath;
    private long syncedAt;
    private boolean success;
    private String errorMessage;

    public SyncRecord(int id, String photoName, String remotePath, long syncedAt, boolean success, String errorMessage) {
        this.id = id;
        this.photoName = photoName;
        this.remotePath = remotePath;
        this.syncedAt = syncedAt;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPhotoName() {
        return photoName;
    }

    public void setPhotoName(String photoName) {
        this.photoName = photoName;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public long getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(long syncedAt) {
        this.syncedAt = syncedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
