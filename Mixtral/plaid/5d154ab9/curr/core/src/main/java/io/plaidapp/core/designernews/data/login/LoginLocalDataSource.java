package io.plaidapp.core.designernews.data.login;

import android.content.SharedPreferences;
import androidx.core.content.edit;
import io.plaidapp.core.designernews.data.users.model.User;

public class LoginLocalDataSource {

  private final SharedPreferences prefs;

  public LoginLocalDataSource(SharedPreferences prefs) {
    this.prefs = prefs;
  }

  public User getUser() {
    long userId = prefs.getLong(KEY_USER_ID, 0L);
    String username = prefs.getString(KEY_USER_NAME, null);
    String userAvatar = prefs.getString(KEY_USER_AVATAR, null);
    if (userId == 0L && username == null && userAvatar == null) {
      return null;
    }

    return new User(userId, "", "", username, userAvatar);
  }

  public void setUser(User value) {
    if (value != null) {
      SharedPreferences.Editor editor = prefs.edit();
      editor.putLong(KEY_USER_ID, value.getId());
      editor.putString(KEY_USER_NAME, value.getDisplayName());
      editor.putString(KEY_USER_AVATAR, value.getPortraitUrl());
      editor.apply();
    }
  }

  public void logout() {
    SharedPreferences.Editor editor = prefs.edit();
    editor.putLong(KEY_USER_ID, 0L);
    editor.putString(KEY_USER_NAME, null);
    editor.putString(KEY_USER_AVATAR, null);
    editor.apply();
  }

  public static final String DESIGNER_NEWS_PREF = "DESIGNER_NEWS_PREF";
  private static final String KEY_USER_ID = "KEY_USER_ID";
  private static final String KEY_USER_NAME = "KEY_USER_NAME";
  private static final String KEY_USER_AVATAR = "KEY_USER_AVATAR";
}
