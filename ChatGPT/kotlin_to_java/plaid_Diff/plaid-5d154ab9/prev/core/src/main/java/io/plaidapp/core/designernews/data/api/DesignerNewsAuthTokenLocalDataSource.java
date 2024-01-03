package io.plaidapp.core.designernews.data.api;

import android.content.SharedPreferences;

public class DesignerNewsAuthTokenLocalDataSource {

    private SharedPreferences prefs;
    private String _authToken;

    public DesignerNewsAuthTokenLocalDataSource(SharedPreferences prefs) {
        this.prefs = prefs;
        this._authToken = prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getAuthToken() {
        return _authToken;
    }

    public void setAuthToken(String value) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply();
        _authToken = value;
    }

    public void clearData() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply();
        setAuthToken(null);
    }

    public static final String DESIGNER_NEWS_AUTH_PREF = "DESIGNER_NEWS_AUTH_PREF";
    private static final String KEY_ACCESS_TOKEN = "KEY_ACCESS_TOKEN";
    private static volatile DesignerNewsAuthTokenLocalDataSource INSTANCE;

    public static DesignerNewsAuthTokenLocalDataSource getInstance(SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (DesignerNewsAuthTokenLocalDataSource.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DesignerNewsAuthTokenLocalDataSource(sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }
}