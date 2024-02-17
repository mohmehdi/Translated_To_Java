

package com.github.shadowsocks;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompatDividers;
import com.github.shadowsocks.App;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;

public class GlobalSettingsPreferenceFragment extends PreferenceFragmentCompatDividers {
    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        preferenceManager.setPreferenceDataStore(DataStore.publicStore);
        DataStore.initGlobal();
        addPreferencesFromResource(R.xml.pref_global);
        SwitchPreference boot = (SwitchPreference) findPreference(Key.isAutoConnect);
        Preference.OnPreferenceChangeListener directBootAwareListener = (preference, newValue) -> {
            if ((boolean) newValue) DirectBoot.update();
            else DirectBoot.clean();
            return true;
        };
        boot.setOnPreferenceChangeListener((preference, value) -> {
            BootReceiver.enabled = (boolean) value;
            return directBootAwareListener.onPreferenceChange(null, DataStore.directBootAware);
        });
        boot.setChecked(BootReceiver.enabled);

        Preference dba = findPreference(Key.directBootAware);
        if (Build.VERSION.SDK_INT >= 24 && context.getSystemService(DevicePolicyManager.class)
                .getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER)
            dba.setOnPreferenceChangeListener(directBootAwareListener);
        else dba.getParent().removePreference(dba);

        SwitchPreference tfo = (SwitchPreference) findPreference(Key.tfo);
        tfo.setChecked(TcpFastOpen.sendEnabled);
        tfo.setOnPreferenceChangeListener((preference, value) -> {
            String result = TcpFastOpen.enabled((boolean) value);
            if (result != null && !result.equals("Success."))
                Snackbar.make(activity.findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show();
            return (boolean) value == TcpFastOpen.sendEnabled;
        });
        if (!TcpFastOpen.supported) {
            tfo.setEnabled(false);
            tfo.setSummary(getString(R.string.tcp_fastopen_summary_unsupported, System.getProperty("os.version")));
        }

        Preference serviceMode = findPreference(Key.serviceMode);
        Preference portProxy = findPreference(Key.portProxy);
        Preference portLocalDns = findPreference(Key.portLocalDns);
        Preference portTransproxy = findPreference(Key.portTransproxy);
        Preference.OnPreferenceChangeListener onServiceModeChange = (preference, newValue) -> {
            boolean enabledLocalDns = false, enabledTransproxy = false;
            if (newValue == Key.modeProxy) ;
            else if (newValue == Key.modeVpn) enabledLocalDns = true;
            else if (newValue == Key.modeTransproxy) {
                enabledLocalDns = true;
                enabledTransproxy = true;
            } else throw new IllegalArgumentException();
            portLocalDns.setEnabled(enabledLocalDns);
            portTransproxy.setEnabled(enabledTransproxy);
            return true;
        };
        Preference.OnPreferenceChangeListener listener = level -> {
            switch (level) {
                case BaseService.IDLE:
                case BaseService.STOPPED:
                    serviceMode.setEnabled(true);
                    portProxy.setEnabled(true);
                    onServiceModeChange.onPreferenceChange(null, DataStore.serviceMode);
                    break;
                default:
                    serviceMode.setEnabled(false);
                    portProxy.setEnabled(false);
                    portLocalDns.setEnabled(false);
                    portTransproxy.setEnabled(false);
            }
            return true;
        };
        listener(((MainActivity) activity).state);
        MainActivity.stateListener = level -> listener.onPreferenceChange(null, level);
        serviceMode.setOnPreferenceChangeListener(onServiceModeChange);
    }

    @Override
    public void onDestroy() {
        MainActivity.stateListener = null;
        super.onDestroy();
    }
}