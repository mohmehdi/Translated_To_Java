package com.github.shadowsocks.tasker;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.github.shadowsocks.R;
import com.github.shadowsocks.database.ProfileManager;
import com.twofortyfouram.locale.api.Intent as ApiIntent;

public class Settings {
    private static final String KEY_SWITCH_ON = "switch_on";
    private static final String KEY_PROFILE_ID = "profile_id";

    public static Settings fromIntent(Intent intent) {
        return new Settings(intent.getBundleExtra(ApiIntent.EXTRA_BUNDLE));
    }

    private boolean switchOn;
    private int profileId;

    public Settings(Bundle bundle) {
        switchOn = bundle.getBoolean(KEY_SWITCH_ON, true);
        profileId = bundle.getInt(KEY_PROFILE_ID, -1);
    }

    public Intent toIntent(Context context) {
        Bundle bundle = new Bundle();
        if (!switchOn) bundle.putBoolean(KEY_SWITCH_ON, false);
        if (profileId >= 0) bundle.putInt(KEY_PROFILE_ID, profileId);
        Profile profile = ProfileManager.getProfile(profileId);
        return new Intent().putExtra(ApiIntent.EXTRA_BUNDLE, bundle).putExtra(ApiIntent.EXTRA_STRING_BLURB,
                (profile != null)
                        ? context.getString((switchOn) ? R.string.start_service : R.string.stop_service, profile.getFormattedName())
                        : context.getString((switchOn) ? R.string.start_service_default : R.string.stop));
    }
}