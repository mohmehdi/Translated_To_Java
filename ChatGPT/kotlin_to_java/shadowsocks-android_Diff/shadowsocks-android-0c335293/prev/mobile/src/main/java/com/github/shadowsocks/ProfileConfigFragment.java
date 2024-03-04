package com.github.shadowsocks;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.support.design.widget.Snackbar;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDataStore;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.github.shadowsocks.App.Companion.app;
import com.github.shadowsocks.bg.BaseService;
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
import com.github.shadowsocks.utils.Key;
import com.takisoft.fix.support.v7.preference.EditTextPreference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;

public class ProfileConfigFragment extends PreferenceFragmentCompatDividers implements Toolbar.OnMenuItemClickListener,
        Preference.OnPreferenceChangeListener, OnPreferenceDataStoreChangeListener {
    private static final int REQUEST_CODE_PLUGIN_CONFIGURE = 1;

    private Profile profile;
    private SwitchPreference isProxyApps;
    private IconListPreference plugin;
    private EditTextPreference pluginConfigure;
    private PluginConfiguration pluginConfiguration;

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        preferenceManager.setPreferenceDataStore(DataStore.INSTANCE);
        Activity activity = getActivity();
        Profile profile = ProfileManager.getProfile(activity.getIntent().getIntExtra(Action.EXTRA_PROFILE_ID, -1));
        if (profile == null) {
            activity.finish();
            return;
        }
        this.profile = profile;
        profile.serialize();
        addPreferencesFromResource(R.xml.pref_profile);
        if (Build.VERSION.SDK_INT >= 25 && activity.getSystemService(UserManager.class).isDemoUser()) {
            findPreference(Key.host).setSummary("shadowsocks.example.org");
            findPreference(Key.remotePort).setSummary("1337");
            findPreference(Key.password).setSummary("\u2022".repeat(32));
        }
        isProxyApps = (SwitchPreference) findPreference(Key.proxyApps);
        if (Build.VERSION.SDK_INT < 21) {
            isProxyApps.getParent().removePreference(isProxyApps);
        } else {
            isProxyApps.setEnabled(BaseService.usingVpnMode);
            isProxyApps.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(activity, AppManager.class));
                isProxyApps.setChecked(true);
                return false;
            });
        }
        plugin = (IconListPreference) findPreference(Key.plugin);
        pluginConfigure = (EditTextPreference) findPreference(Key.pluginConfigure);
        plugin.setUnknownValueSummary(getString(R.string.plugin_unknown));
        plugin.setOnPreferenceChangeListener((preference, newValue) -> {
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions(), (String) newValue);
            DataStore.INSTANCE.setPlugin(pluginConfiguration.toString());
            DataStore.INSTANCE.setDirty(true);
            pluginConfigure.setEnabled(!newValue.isEmpty());
            pluginConfigure.setText(pluginConfiguration.getSelectedOptions().toString());
            if (!PluginManager.fetchPlugins().get(newValue).isTrusted()) {
                Snackbar.make(getView(), R.string.plugin_untrusted, Snackbar.LENGTH_LONG).show();
            }
            return true;
        });
        pluginConfigure.setOnPreferenceChangeListener(this);
        initPlugins();
        app.listenForPackageChanges(this::initPlugins);
        DataStore.INSTANCE.registerChangeListener(this);
    }

    private void initPlugins() {
        PluginManager.PluginMap plugins = PluginManager.fetchPlugins();
        plugin.setEntries(plugins.values().stream().map(PluginManager.Plugin::getLabel).toArray(String[]::new));
        plugin.setEntryValues(plugins.values().stream().map(PluginManager.Plugin::getId).toArray(String[]::new));
        plugin.setEntryIcons(plugins.values().stream().map(PluginManager.Plugin::getIcon).toArray(Integer[]::new));
        plugin.setEntryPackageNames(plugins.values().stream().map(PluginManager.Plugin::getPackageName).toArray(String[]::new));
        pluginConfiguration = new PluginConfiguration(DataStore.INSTANCE.getPlugin());
        plugin.setValue(pluginConfiguration.getSelected());
        plugin.init();
        plugin.checkSummary();
        pluginConfigure.setEnabled(!pluginConfiguration.getSelected().isEmpty());
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
        getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        isProxyApps.setChecked(DataStore.INSTANCE.isProxyApps());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            String selected = pluginConfiguration.getSelected();
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions() +
                    (selected, new PluginOptions(selected, (String) newValue)), selected);
            DataStore.INSTANCE.setPlugin(pluginConfiguration.toString());
            DataStore.INSTANCE.setDirty(true);
            return true;
        } catch (IllegalArgumentException exc) {
            Snackbar.make(getView(), exc.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (!key.equals(Key.proxyApps) && findPreference(key) != null) {
            DataStore.INSTANCE.setDirty(true);
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey().equals(Key.pluginConfigure)) {
            Intent intent = PluginManager.buildIntent(pluginConfiguration.getSelected(), PluginContract.ACTION_CONFIGURE);
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(intent.putExtra(PluginContract.EXTRA_OPTIONS,
                        pluginConfiguration.getSelectedOptions().toString()), REQUEST_CODE_PLUGIN_CONFIGURE);
            } else {
                showPluginEditor();
            }
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PLUGIN_CONFIGURE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    String options = data.getStringExtra(PluginContract.EXTRA_OPTIONS);
                    pluginConfigure.setText(options);
                    onPreferenceChange(null, options);
                    break;
                case PluginContract.RESULT_FALLBACK:
                    showPluginEditor();
                    break;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                AlertDialog.Builder(getActivity())
                        .setTitle(R.string.delete_confirm_prompt)
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
        DataStore.INSTANCE.unregisterChangeListener(this);
        super.onDestroy();
    }
}