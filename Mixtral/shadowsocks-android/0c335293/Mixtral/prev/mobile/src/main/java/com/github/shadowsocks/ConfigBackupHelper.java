

package com.github.shadowsocks;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.utils.Key;

public class ConfigBackupHelper extends BackupAgentHelper {
    @Override
    public void onCreate() {
        addHelper("com.github.shadowsocks.database.profile",
                new FileBackupHelper(this, "../databases/" + Key.PROFILE, Acl.CUSTOM_RULES + ".acl"));
    }
}