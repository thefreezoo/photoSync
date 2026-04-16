package com.example.pho;

public class Photo {
    private long id;
    private String name;
    private String path;
    private long dateAdded;
    private boolean isSynced;
    private boolean isSelected;
    private String hash;

    public Photo(long id, String name, String path, long dateAdded) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.dateAdded = dateAdded;
        this.isSynced = false;
        this.isSelected = false;
        this.hash = null;
    }

    public Photo(long id, String name, String path, long dateAdded, boolean isSynced) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.dateAdded = dateAdded;
        this.isSynced = isSynced;
        this.isSelected = false;
        this.hash = null;
    }

    public Photo(long id, String name, String path, long dateAdded, boolean isSynced, String hash) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.dateAdded = dateAdded;
        this.isSynced = isSynced;
        this.isSelected = false;
        this.hash = hash;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Photo photo = (Photo) o;
        return id == photo.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
    
    // Check if the file is a video
    public boolean isVideo() {
        if (name == null) return false;
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || 
               lowerName.endsWith(".avi") || lowerName.endsWith(".wmv") || 
               lowerName.endsWith(".mkv") || lowerName.endsWith(".flv") || 
               lowerName.endsWith(".3gp") || lowerName.endsWith(".webm");
    }
}
