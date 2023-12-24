
package com.google.samples.apps.sunflower.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UnsplashSearchResponse {
    @SerializedName("results")
    private List<UnsplashPhoto> results;
    
    @SerializedName("total_pages")
    private int totalPages;
    
    public UnsplashSearchResponse(List<UnsplashPhoto> results, int totalPages) {
        this.results = results;
        this.totalPages = totalPages;
    }
    
    public List<UnsplashPhoto> getResults() {
        return results;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
}
