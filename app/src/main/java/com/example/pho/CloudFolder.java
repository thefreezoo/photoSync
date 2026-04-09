package com.example.pho;

public class CloudFolder {
    private String id;
    private String name;
    private int photoCount;
    private long timestamp;

    public CloudFolder(String id, String name, int photoCount, long timestamp) {
        this.id = id;
        this.name = name;
        this.photoCount = photoCount;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
