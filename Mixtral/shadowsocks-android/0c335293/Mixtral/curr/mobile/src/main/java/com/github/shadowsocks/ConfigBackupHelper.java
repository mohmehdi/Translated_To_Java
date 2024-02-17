

package com.github.shadowsocks;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import com.github.shadowsocks.utils.Key;

import java.io.File;

@Deprecated("Only used in API level < 23. For 6.0+, Auto Backup for Apps is used.")
public class ConfigBackupHelper extends BackupAgentHelper {

    @Override
    public void onCreate() {
        addHelper("com.github.shadowsocks.database.profile", new FileBackupHelper(this,
                new File(new File(getApplicationContext().getFilesDir(), ".."),
                        Key.DB_PROFILE).getAbsolutePath(),
                new File(new File(getApplicationContext().getFilesDir(), ".."),
                        Key.DB_PUBLIC).getAbsolutePath()));
    }
}