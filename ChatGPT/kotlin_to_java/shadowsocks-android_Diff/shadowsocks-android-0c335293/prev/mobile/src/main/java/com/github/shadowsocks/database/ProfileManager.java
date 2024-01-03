package com.github.shadowsocks.database;

import android.util.Log;
import com.github.shadowsocks.App;

import com.github.shadowsocks.ProfilesFragment;

public class ProfileManager {
    private static final String TAG = "ProfileManager";

    public static Profile createProfile(Profile p) {
        Profile profile = p != null ? p : new Profile();
        profile.setId(0);
        try {
            Profile oldProfile = App.Companion.getApp().getCurrentProfile();
            if (oldProfile != null) {
                profile.setRoute(oldProfile.getRoute());
                profile.setIpv6(oldProfile.getIpv6());
                profile.setProxyApps(oldProfile.getProxyApps());
                profile.setBypass(oldProfile.getBypass());
                profile.setIndividual(oldProfile.getIndividual());
                profile.setUdpdns(oldProfile.getUdpdns());
            }
            Object[] last = DBHelper.profileDao.queryRaw(DBHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)")
                    .prepareStatementString()).firstResult();
            if (last != null && last.length == 1 && last[0] != null) {
                profile.setUserOrder(((Number) last[0]).longValue() + 1);
            }
            DBHelper.profileDao.createOrUpdate(profile);
            ProfilesFragment.instance.getProfilesAdapter().add(profile);
        } catch (Exception ex) {
            Log.e(TAG, "addProfile", ex);
            App.Companion.getApp().track(ex);
        }
        return profile;
    }

    public static boolean updateProfile(Profile profile) {
        try {
            DBHelper.profileDao.update(profile);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "updateProfile", ex);
            App.Companion.getApp().track(ex);
            return false;
        }
    }

    public static Profile getProfile(int id) {
        try {
            return DBHelper.profileDao.queryForId(id);
        } catch (Exception ex) {
            Log.e(TAG, "getProfile", ex);
            App.Companion.getApp().track(ex);
            return null;
        }
    }

    public static boolean delProfile(int id) {
        try {
            DBHelper.profileDao.deleteById(id);
            ProfilesFragment.instance.getProfilesAdapter().removeId(id);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "delProfile", ex);
            App.Companion.getApp().track(ex);
            return false;
        }
    }

    public static Profile getFirstProfile() {
        try {
            List<Profile> result = DBHelper.profileDao.query(DBHelper.profileDao.queryBuilder().limit(1L).prepare());
            if (result.size() == 1) {
                return result.get(0);
            } else {
                return null;
            }
        } catch (Exception ex) {
            Log.e(TAG, "getAllProfiles", ex);
            App.Companion.getApp().track(ex);
            return null;
        }
    }

    public static List<Profile> getAllProfiles() {
        try {
            return DBHelper.profileDao.query(DBHelper.profileDao.queryBuilder().orderBy("userOrder", true).prepare());
        } catch (Exception ex) {
            Log.e(TAG, "getAllProfiles", ex);
            App.Companion.getApp().track(ex);
            return null;
        }
    }
}