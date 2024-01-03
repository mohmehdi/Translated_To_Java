package com.github.shadowsocks;

import android.app.admin.DevicePolicyManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompatDividers;

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
        Preference.OnPreferenceChangeListener directBootAwareListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if ((boolean) newValue) {
                    DirectBoot.update();
                } else {
                    DirectBoot.clean();
                }
                return true;
            }
        };
        boot.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                BootReceiver.enabled = (boolean) value;
                directBootAwareListener.onPreferenceChange(null, DataStore.directBootAware);
                return true;
            }
        });
        boot.setChecked(BootReceiver.enabled);

        Preference dba = findPreference(Key.directBootAware);
        if (Build.VERSION.SDK_INT >= 24 && getActivity().getSystemService(DevicePolicyManager.class)
                .getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER) {
            dba.setOnPreferenceChangeListener(directBootAwareListener);
        } else {
            dba.getParent().removePreference(dba);
        }

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
        BaseService.IDLE, BaseService.STOPPED -> {
            serviceMode.setEnabled(true);
            portProxy.setEnabled(true);
            onServiceModeChange.onPreferenceChange(null, DataStore.serviceMode);
        }
        else -> {
            serviceMode.setEnabled(false);
            portProxy.setEnabled(false);
            portLocalDns.setEnabled(false);
            portTransproxy.setEnabled(false);
        }
    }
    MainActivity.stateListener = listener;
    serviceMode.setOnPreferenceChangeListener(onServiceModeChange);
}

@Override
public void onDestroy() {
    MainActivity.stateListener = null;
    super.onDestroy();
}
}