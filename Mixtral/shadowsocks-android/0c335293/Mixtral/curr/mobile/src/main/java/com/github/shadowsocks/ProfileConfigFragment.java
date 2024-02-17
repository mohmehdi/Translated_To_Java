

package com.github.shadowsocks;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDataStore;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import com.github.shadowsocks.App;
import com.github.shadowsocks.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.plugin.PluginContract;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.plugin.PluginOptions;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.IconListPreference;
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener;
import com.github.shadowsocks.preference.PluginConfigurationDialogFragment;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.takisoft.fix.support.v7.preference.EditTextPreference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;
import java.util.HashMap;
import java.util.Map;

public class ProfileConfigFragment extends PreferenceFragmentCompatDividers implements Toolbar.OnMenuItemClickListener,
        Preference.OnPreferenceChangeListener, OnPreferenceDataStoreChangeListener {

    private static final int REQUEST_CODE_PLUGIN_CONFIGURE = 1;

    private Profile profile;
    private SwitchPreference isProxyApps;
    private ListPreference plugin;
    private EditTextPreference pluginConfigure;
    private PluginConfiguration pluginConfiguration;

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setPreferenceDataStore(DataStore.privateStore);
        Activity activity = getActivity();
        profile = ProfileManager.getProfile(activity.getIntent().getIntExtra(Action.EXTRA_PROFILE_ID, -1));
        if (profile == null) {
            activity.finish();
            return;
        }
        profile.serialize();
        addPreferencesFromResource(R.xml.pref_profile);

        if (Build.VERSION.SDK_INT >= 25 && activity.getSystemService(UserManager.class).isDemoUser) {
            findPreference(Key.host).setSummary("shadowsocks.example.org");
            findPreference(Key.remotePort).setSummary("1337");
            findPreference(Key.password).setSummary("\u2022".repeat(32));
        }

        String serviceMode = DataStore.serviceMode;
        findPreference(Key.remoteDns).setEnabled(serviceMode.equals(Key.modeProxy));
        isProxyApps = (SwitchPreference) findPreference(Key.proxyApps);
        isProxyApps.setEnabled(serviceMode.equals(Key.modeVpn));
        isProxyApps.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(activity, AppManager.class));
            isProxyApps.setChecked(true);
            return false;
        });
        findPreference(Key.udpdns).setEnabled(serviceMode.equals(Key.modeProxy));

        plugin = (ListPreference) findPreference(Key.plugin);
        pluginConfigure = (EditTextPreference) findPreference(Key.pluginConfigure);
        plugin.setUnknownValueSummary(getString(R.string.plugin_unknown));
        plugin.setOnPreferenceChangeListener(this);
        pluginConfigure.setOnPreferenceChangeListener(this);

        initPlugins();
        App.getInstance().listenForPackageChanges(() -> initPlugins());
        DataStore.privateStore.registerOnPreferenceDataStoreChangeListener(this);
    }

    private void initPlugins() {
        Map<String, PluginManager.PluginInfo> plugins = PluginManager.fetchPlugins();
        plugin.setEntries(plugins.values().stream().map(PluginManager.PluginInfo::getLabel).toArray(String[]::new));
        plugin.setEntryValues(plugins.values().stream().map(PluginManager.PluginInfo::getId).toArray(String[]::new));
        plugin.setIconIds(plugins.values().stream().mapToInt(PluginManager.PluginInfo::getIcon).toArray());
        plugin.setPackageNames(plugins.values().stream().map(PluginManager.PluginInfo::getPackageName).toArray(String[]::new));

        pluginConfiguration = new PluginConfiguration(DataStore.plugin);
        plugin.setValue(pluginConfiguration.getSelected());
        plugin.init();
        plugin.checkSummary();
        pluginConfigure.setEnabled(pluginConfiguration.getSelected().length() > 0);
        pluginConfigure.setText(pluginConfiguration.getSelectedOptions().toString());
    }

    private void showPluginEditor() {
        Bundle bundle = new Bundle();
        bundle.putString("key", Key.pluginConfigure);
        bundle.putString(PluginConfigurationDialogFragment.PLUGIN_ID_FRAGMENT_TAG, pluginConfiguration.getSelected());
        displayPreferenceDialog(new PluginConfigurationDialogFragment(), Key.pluginConfigure, bundle);
    }

    public void saveAndExit() {
        profile.deserialize();
        ProfileManager.updateProfile(profile);
        ProfilesFragment.instance.profilesAdapter.deepRefreshId(profile.getId());
        if (DataStore.profileId == profile.getId() && DataStore.directBootAware) DirectBoot.update();
        getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        isProxyApps.setChecked(DataStore.proxyApps);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == plugin) {
            String selected = pluginConfiguration.getSelected();
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions() +
                    new HashMap.SimpleEntry<>(selected, new PluginOptions(selected, (String) newValue)), selected);
            DataStore.plugin = pluginConfiguration.toString();
            DataStore.dirty = true;
            return true;
        } else {
            throw new IllegalArgumentException("Unsupported preference");
        }
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (!Key.proxyApps.equals(key) && findPreference(key) != null) DataStore.dirty = true;
    }

    @Override
    public boolean onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey().equals(Key.pluginConfigure)) {
            Intent intent = PluginManager.buildIntent(pluginConfiguration.getSelected(), PluginContract.ACTION_CONFIGURE);
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(intent.putExtra(PluginContract.EXTRA_OPTIONS,
                        pluginConfiguration.getSelectedOptions().toString()), REQUEST_CODE_PLUGIN_CONFIGURE);
            } else {
                showPluginEditor();
            }
        } else {
            return super.onDisplayPreferenceDialog(preference);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PLUGIN_CONFIGURE) {
            if (resultCode == Activity.RESULT_OK) {
                String options = data.getStringExtra(PluginContract.EXTRA_OPTIONS);
                pluginConfigure.setText(options);
                onPreferenceChange(null, options);
            } else if (resultCode == PluginContract.RESULT_FALLBACK) {
                showPluginEditor();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.delete_confirm_prompt)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            ProfileManager.delProfile(profile.getId());
                            getActivity().finish();
                        })
                        .setNegativeButton(R.string.no, null)
                        .create()
                        .show();
                return true;
            case R.id.action_apply:
                saveAndExit();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroy() {
        DataStore.privateStore.unregisterOnPreferenceDataStoreChangeListener(this);
        super.onDestroy();
    }
}