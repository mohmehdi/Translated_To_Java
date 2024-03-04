package com.github.shadowsocks;

import android.app.admin.DevicePolicyManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;

public class GlobalSettingsPreferenceFragment extends PreferenceFragmentCompatDividers {
    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        preferenceManager.preferenceDataStore = DataStore.publicStore;
        DataStore.initGlobal();
        addPreferencesFromResource(R.xml.pref_global);

        SwitchPreference boot = findPreference(Key.isAutoConnect);
        Preference.OnPreferenceChangeListener directBootAwareListener = (preference, newValue) -> {
            if ((Boolean) newValue) DirectBoot.update();
            else DirectBoot.clean();
            return true;
        };

        boot.setOnPreferenceChangeListener((preference, value) -> {
            BootReceiver.enabled = (Boolean) value;
            directBootAwareListener.onPreferenceChange(null, DataStore.directBootAware);
            return true;
        });
        boot.setChecked(BootReceiver.enabled);

        Preference dba = findPreference(Key.directBootAware);
        if (Build.VERSION.SDK_INT >= 24 && getContext().getSystemService(DevicePolicyManager.class)
                .getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER) {
            dba.setOnPreferenceChangeListener(directBootAwareListener);
        } else {
            dba.getParent().removePreference(dba);
        }

        SwitchPreference tfo = findPreference(Key.tfo);
        tfo.setChecked(TcpFastOpen.sendEnabled);
        tfo.setOnPreferenceChangeListener((preference, value) -> {
            String result = TcpFastOpen.enabled((Boolean) value);
            if (result != null && !result.equals("Success.")) {
                Snackbar.make(getActivity().findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show();
            }
            return value == TcpFastOpen.sendEnabled;
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
            boolean enabledLocalDns, enabledTransproxy;
            switch ((String) newValue) {
                case Key.modeProxy:
                    enabledLocalDns = false;
                    enabledTransproxy = false;
                    break;
                case Key.modeVpn:
                    enabledLocalDns = true;
                    enabledTransproxy = false;
                    break;
                case Key.modeTransproxy:
                    enabledLocalDns = true;
                    enabledTransproxy = true;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            portLocalDns.setEnabled(enabledLocalDns);
            portTransproxy.setEnabled(enabledTransproxy);
            return true;
        };
        int state = ((MainActivity) getActivity()).state;
        switch (state) {
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
                break;
        }
        ((MainActivity) getActivity()).stateListener = state1 -> {
            switch (state1) {
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
                    break;
            }
        };
        serviceMode.setOnPreferenceChangeListener(onServiceModeChange);
    }

    @Override
    public void onDestroy() {
        ((MainActivity) getActivity()).stateListener = null;
        super.onDestroy();
    }
}
