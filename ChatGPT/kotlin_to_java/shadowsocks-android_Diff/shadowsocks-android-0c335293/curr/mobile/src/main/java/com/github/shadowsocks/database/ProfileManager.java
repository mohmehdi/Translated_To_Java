package com.github.shadowsocks.database;

import android.util.Log;
import com.github.shadowsocks.App;

import com.github.shadowsocks.ProfilesFragment;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DirectBoot;

import java.util.List;

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
            List<Object[]> last = PrivateDatabase.profileDao.queryRaw(PrivateDatabase.profileDao.queryBuilder()
                    .selectRaw("MAX(userOrder)").prepareStatementString()).getResults();
            if (last != null && last.size() == 1 && last.get(0)[0] != null)
                profile.setUserOrder((Long) last.get(0)[0] + 1);
            PrivateDatabase.profileDao.createOrUpdate(profile);
            ProfilesFragment.getInstance().getProfilesAdapter().add(profile);
        } catch (Exception ex) {
            Log.e(TAG, "addProfile", ex);
            App.Companion.getApp().track(ex);
        }
        return profile;
    }

    public static boolean updateProfile(Profile profile) {
        try {
            PrivateDatabase.profileDao.update(profile);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "updateProfile", ex);
            App.Companion.getApp().track(ex);
            return false;
        }
    }

    public static Profile getProfile(int id) {
        try {
            return PrivateDatabase.profileDao.queryForId(id);
        } catch (Exception ex) {
            Log.e(TAG, "getProfile", ex);
            App.Companion.getApp().track(ex);
            return null;
        }
    }

    public static boolean delProfile(int id) {
        try {
            PrivateDatabase.profileDao.deleteById(id);
            ProfilesFragment.getInstance().getProfilesAdapter().removeId(id);
            if (id == DataStore.profileId && DataStore.directBootAware) DirectBoot.clean();
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "delProfile", ex);
            App.Companion.getApp().track(ex);
            return false;
        }
    }

    public static Profile getFirstProfile() {
        try {
            List<Profile> result = PrivateDatabase.profileDao.query(PrivateDatabase.profileDao.queryBuilder().limit(1L).prepare());
            if (result.size() == 1) return result.get(0);
            else return null;
        } catch (Exception ex) {
            Log.e(TAG, "getAllProfiles", ex);
            App.Companion.getApp().track(ex);
            return null;
        }
    }

    public static List<Profile> getAllProfiles() {
        try {
            return PrivateDatabase.profileDao.query(PrivateDatabase.profileDao.queryBuilder().orderBy("userOrder", true).prepare());
        } catch (Exception ex) {
            Log.e(TAG, "getAllProfiles", ex);
            App.Companion.getApp().track(ex);
            return null;
        }
    }
}