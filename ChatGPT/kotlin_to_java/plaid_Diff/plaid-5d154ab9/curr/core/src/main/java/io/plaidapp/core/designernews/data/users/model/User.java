package io.plaidapp.core.designernews.data.users.model;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("id")
    private long id;
    @SerializedName("first_name")
    private String firstName;
    @SerializedName("last_name")
    private String lastName;
    @SerializedName("display_name")
    private String displayName;
    @SerializedName("portrait_url")
    private String portraitUrl;

    public User(long id, String firstName, String lastName, String displayName, String portraitUrl) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.portraitUrl = portraitUrl;
    }

    public long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPortraitUrl() {
        return portraitUrl;
    }
}