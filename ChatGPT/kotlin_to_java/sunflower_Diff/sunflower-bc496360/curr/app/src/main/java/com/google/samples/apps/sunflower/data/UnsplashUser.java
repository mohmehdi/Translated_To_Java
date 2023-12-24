
package com.google.samples.apps.sunflower.data;

import com.google.gson.annotations.SerializedName;

public class UnsplashUser {
    @SerializedName("name")
    private String name;
    
    @SerializedName("username")
    private String username;
    
    public UnsplashUser(String name, String username) {
        this.name = name;
        this.username = username;
    }
    
    public String getName() {
        return name;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getAttributionUrl() {
        return "https:";
    }
}
