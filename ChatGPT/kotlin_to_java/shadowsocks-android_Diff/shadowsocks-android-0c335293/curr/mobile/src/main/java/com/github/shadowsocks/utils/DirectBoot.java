package com.github.shadowsocks.utils;

import android.annotation.TargetApi;
import com.github.shadowsocks.App;

import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.preference.DataStore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@TargetApi(24)
public class DirectBoot {
    private static File file = new File(App.Companion.getApp().getDeviceContext().getNoBackupFilesDir(), "directBootProfile");

    public static Profile getDeviceProfile() {
        try {
            return (Profile) new ObjectInputStream(file.getInputStream()).readObject();
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static void clean() {
        file.delete();
        new File(App.Companion.getApp().getDeviceContext().getNoBackupFilesDir(), BaseService.CONFIG_FILE).delete();
    }

    public static void update() {
        Profile profile = ProfileManager.getProfile(DataStore.profileId);
        if (profile == null) {
            clean();
        } else {
            try {
                new ObjectOutputStream(file.getOutputStream()).writeObject(profile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}