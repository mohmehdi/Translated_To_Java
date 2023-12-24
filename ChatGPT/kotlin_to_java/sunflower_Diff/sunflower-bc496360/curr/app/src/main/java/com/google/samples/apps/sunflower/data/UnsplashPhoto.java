package com.google.samples.apps.sunflower.data;

import com.google.gson.annotations.SerializedName;

public class UnsplashPhoto {
    @SerializedName("id")
    private String id;
    
    @SerializedName("urls")
    private UnsplashPhotoUrls urls;
    
    @SerializedName("user")
    private UnsplashUser user;
    
    public UnsplashPhoto(String id, UnsplashPhotoUrls urls, UnsplashUser user) {
        this.id = id;
        this.urls = urls;
        this.user = user;
    }
    
    public String getId() {
        return id;
    }
    
    public UnsplashPhotoUrls getUrls() {
        return urls;
    }
    
    public UnsplashUser getUser() {
        return user;
    }
}