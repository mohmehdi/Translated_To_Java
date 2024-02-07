package io.plaidapp.core.designernews.data.api;

import android.content.SharedPreferences;
import androidx.core.content.edit;

public class DesignerNewsAuthTokenLocalDataSource {

  private String _authToken;
  private final SharedPreferences prefs;

  public DesignerNewsAuthTokenLocalDataSource(SharedPreferences prefs) {
    this.prefs = prefs;
    _authToken = prefs.getString(KEY_ACCESS_TOKEN, null);
  }

  public String getAuthToken() {
    return _authToken;
  }

  public void setAuthToken(String authToken) {
    prefs.edit().putString(KEY_ACCESS_TOKEN, authToken).apply();
    _authToken = authToken;
  }

  public void clearData() {
    prefs.edit().putString(KEY_ACCESS_TOKEN, null).apply();
    _authToken = null;
  }

  public static DesignerNewsAuthTokenLocalDataSource getInstance(
    SharedPreferences sharedPreferences
  ) {
    DesignerNewsAuthTokenLocalDataSource INSTANCE =
      DesignerNewsAuthTokenLocalDataSource.INSTANCE;
    if (INSTANCE == null) {
      synchronized (DesignerNewsAuthTokenLocalDataSource.class) {
        if (INSTANCE == null) {
          INSTANCE =
            new DesignerNewsAuthTokenLocalDataSource(sharedPreferences);
          DesignerNewsAuthTokenLocalDataSource.INSTANCE = INSTANCE;
        }
      }
    }
    return INSTANCE;
  }

  private static DesignerNewsAuthTokenLocalDataSource INSTANCE;
  public static final String DESIGNER_NEWS_AUTH_PREF =
    "DESIGNER_NEWS_AUTH_PREF";
  public static final String KEY_ACCESS_TOKEN = "KEY_ACCESS_TOKEN";
}
