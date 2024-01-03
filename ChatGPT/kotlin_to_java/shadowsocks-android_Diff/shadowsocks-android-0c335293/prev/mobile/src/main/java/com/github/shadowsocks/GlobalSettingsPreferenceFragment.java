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
        preferenceManager.setPreferenceDataStore(DataStore.INSTANCE);
        DataStore.INSTANCE.initGlobal();
        addPreferencesFromResource(R.xml.pref_global);
        SwitchPreference switchPreference = (SwitchPreference) findPreference(Key.isAutoConnect);
        switchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                BootReceiver.enabled = (boolean) value;
                return true;
            }
        });
        switchPreference.setChecked(BootReceiver.enabled);

        SwitchPreference tfo = (SwitchPreference) findPreference(Key.tfo);
        tfo.setChecked(TcpFastOpen.sendEnabled);
        tfo.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                String result = TcpFastOpen.enabled((boolean) value);
                if (result != null && !result.equals("Success.")) {
                    Snackbar.make(getActivity().findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show();
                }
                return value.equals(TcpFastOpen.sendEnabled);
            }
        });
        if (!TcpFastOpen.supported) {
            tfo.setEnabled(false);
            tfo.setSummary(getString(R.string.tcp_fastopen_summary_unsupported, System.getProperty("os.version")));
        }

        Preference serviceMode = findPreference(Key.serviceMode);
        Preference portProxy = findPreference(Key.portProxy);
        Preference portLocalDns = findPreference(Key.portLocalDns);
        Preference portTransproxy = findPreference(Key.portTransproxy);
        Preference.OnPreferenceChangeListener onServiceModeChange = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
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
            }
        };
        MainActivity activity = (MainActivity) getActivity();
        BaseService baseService = activity.state;
        switch (baseService) {
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
        MainActivity.stateListener = new MainActivity.StateListener() {
            @Override
            public void onStateChanged(BaseService state) {
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