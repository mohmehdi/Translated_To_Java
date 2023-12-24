package com.google.samples.apps.sunflower.data;

import com.google.gson.annotations.SerializedName;

public class UnsplashPhotoUrls {
    @SerializedName("small")
    private String small;

    public UnsplashPhotoUrls(String small) {
        this.small = small;
    }

    public String getSmall() {
        return small;
    }

    public void setSmall(String small) {
        this.small = small;
    }
}