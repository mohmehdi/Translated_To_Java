package com.github.shadowsocks;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;

public class GlobalSettingsPreferenceFragment extends PreferenceFragmentCompatDividers {

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        preferenceManager.preferenceDataStore = DataStore.INSTANCE;
        DataStore.INSTANCE.initGlobal();
        addPreferencesFromResource(R.xml.pref_global);
        SwitchPreference switchPreference = findPreference(Key.isAutoConnect);
        switchPreference.setOnPreferenceChangeListener((preference, value) -> {
            BootReceiver.enabled = (boolean) value;
            return true;
        });
        switchPreference.setChecked(BootReceiver.enabled);

        SwitchPreference tfoSwitch = findPreference(Key.tfo);
        tfoSwitch.setChecked(TcpFastOpen.sendEnabled);
        tfoSwitch.setOnPreferenceChangeListener((preference, value) -> {
            String result = TcpFastOpen.enabled((boolean) value);
            if (result != null && !result.equals("Success.")) {
                Snackbar.make(requireActivity().findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show();
            }
            return value == TcpFastOpen.sendEnabled;
        });
        if (!TcpFastOpen.supported) {
            tfoSwitch.setEnabled(false);
            tfoSwitch.setSummary(getString(R.string.tcp_fastopen_summary_unsupported, System.getProperty("os.version")));
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

        int currentState = ((MainActivity) requireActivity()).state;
        switch (currentState) {
            case BaseService.IDLE:
            case BaseService.STOPPED:
                serviceMode.setEnabled(true);
                portProxy.setEnabled(true);
                onServiceModeChange.onPreferenceChange(null, DataStore.INSTANCE.serviceMode);
                break;
            default:
                serviceMode.setEnabled(false);
                portProxy.setEnabled(false);
                portLocalDns.setEnabled(false);
                portTransproxy.setEnabled(false);
        }

        MainActivity.stateListener = currentState1 -> {
            switch (currentState1) {
                case BaseService.IDLE:
                case BaseService.STOPPED:
                    serviceMode.setEnabled(true);
                    portProxy.setEnabled(true);
                    onServiceModeChange.onPreferenceChange(null, DataStore.INSTANCE.serviceMode);
                    break;
                default:
                    serviceMode.setEnabled(false);
                    portProxy.setEnabled(false);
                    portLocalDns.setEnabled(false);
                    portTransproxy.setEnabled(false);
            }
        };

        serviceMode.setOnPreferenceChangeListener(onServiceModeChange);
    }

    @Override
    public void onDestroy() {
        MainActivity.stateListener = null;
        super.onDestroy();
    }
}
