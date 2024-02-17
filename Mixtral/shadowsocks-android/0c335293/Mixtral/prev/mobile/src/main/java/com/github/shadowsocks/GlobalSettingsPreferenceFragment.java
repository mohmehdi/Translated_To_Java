

package com.github.shadowsocks;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompatDividers;
import android.view.View;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;

public class GlobalSettingsPreferenceFragment extends PreferenceFragmentCompatDividers {

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        preferenceManager.setPreferenceDataStore(new DataStore());
        DataStore.initGlobal();
        addPreferencesFromResource(R.xml.pref_global);

        SwitchPreference switchPreference = (SwitchPreference) findPreference(Key.isAutoConnect);
        switchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            BootReceiver.setEnabled((boolean) newValue);
            return true;
        });
        switchPreference.setChecked(BootReceiver.isEnabled());

        SwitchPreference tfoSwitchPreference = (SwitchPreference) findPreference(Key.tfo);
        tfoSwitchPreference.setChecked(TcpFastOpen.isSendEnabled());
        tfoSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String result = TcpFastOpen.enable((boolean) newValue);
            if (result != null && !result.equals("Success.")) {
                Snackbar.make(getActivity().findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show();
            }
            return newValue.equals(String.valueOf(TcpFastOpen.isSendEnabled()));
        });

        if (!TcpFastOpen.isSupported()) {
            tfoSwitchPreference.setEnabled(false);
            tfoSwitchPreference.setSummary(getString(R.string.tcp_fastopen_summary_unsupported, System.getProperty("os.version")));
        }

        Preference serviceModePreference = findPreference(Key.serviceMode);
        Preference portProxyPreference = findPreference(Key.portProxy);
        Preference portLocalDnsPreference = findPreference(Key.portLocalDns);
        Preference portTransproxyPreference = findPreference(Key.portTransproxy);

        Preference.OnPreferenceChangeListener onPreferenceChangeListener = (preference, newValue) -> {
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
            portLocalDnsPreference.setEnabled(enabledLocalDns);
            portTransproxyPreference.setEnabled(enabledTransproxy);
            return true;
        };

        View.OnClickListener listener = (view) -> {
            int state = ((MainActivity) getActivity()).getState();
            if (state == BaseService.IDLE || state == BaseService.STOPPED) {
                serviceModePreference.setEnabled(true);
                portProxyPreference.setEnabled(true);
                onPreferenceChangeListener.onPreferenceChange(serviceModePreference, DataStore.serviceMode);
            } else {
                serviceModePreference.setEnabled(false);
                portProxyPreference.setEnabled(false);
                portLocalDnsPreference.setEnabled(false);
                portTransproxyPreference.setEnabled(false);
            }
        };

        listener.onClick(null);
        ((MainActivity) getActivity()).setStateListener(listener);
        serviceModePreference.setOnPreferenceChangeListener(onPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        ((MainActivity) getActivity()).setStateListener(null);
        super.onDestroy();
    }
}