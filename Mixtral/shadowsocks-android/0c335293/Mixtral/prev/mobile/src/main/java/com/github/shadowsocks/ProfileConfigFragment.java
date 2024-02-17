

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
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import com.github.shadowsocks.App;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.plugin.PluginContract;
import com.github.shadowsocks.plugin.PluginContract.EXTRA_OPTIONS;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.plugin.PluginOptions;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.IconListPreference;
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener;
import com.github.shadowsocks.preference.PluginConfigurationDialogFragment;
import com.github.shadowsocks.preference.PreferenceFragmentCompatDividers;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.Utils;
import com.takisoft.fix.support.v7.preference.EditTextPreference;

public class ProfileConfigFragment extends PreferenceFragmentCompatDividers
        implements Toolbar.OnMenuItemClickListener, Preference.OnPreferenceChangeListener,
        OnPreferenceDataStoreChangeListener {

    private static final int REQUEST_CODE_PLUGIN_CONFIGURE = 1;

    private Profile profile;
    private SwitchPreference isProxyApps;
    private IconListPreference plugin;
    private EditTextPreference pluginConfigure;
    private PluginConfiguration pluginConfiguration;

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        PreferenceManager.setDefaultValues(getActivity(), R.xml.pref_profile, false);
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

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
            findPreference(Key.password).setSummary(Utils.repeat("\u2022", 32));
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
        plugin.setOnPreferenceChangeListener(this);

        pluginConfigure.setOnPreferenceChangeListener(this);

        initPlugins();

        App.getInstance().listenForPackageChanges(() -> initPlugins());

        DataStore.registerChangeListener(this);
    }

    private void initPlugins() {
        PluginManager pluginManager = PluginManager.fetchPlugins();
        String[] entries = new String[pluginManager.size()];
        String[] entryValues = new String[pluginManager.size()];
        int[] entryIcons = new int[pluginManager.size()];
        String[] entryPackageNames = new String[pluginManager.size()];

        int i = 0;
        for (PluginManager.Plugin pluginInfo : pluginManager) {
            entries[i] = pluginInfo.value.label;
            entryValues[i] = pluginInfo.value.id;
            entryIcons[i] = pluginInfo.value.icon;
            entryPackageNames[i] = pluginInfo.value.packageName;
            i++;
        }

        plugin.setEntries(entries);
        plugin.setEntryValues(entryValues);
        plugin.setEntryIcons(entryIcons);
        plugin.setEntryPackageNames(entryPackageNames);

        pluginConfiguration = new PluginConfiguration(DataStore.plugin);
        plugin.setValue(pluginConfiguration.selected);
        plugin.init();
        plugin.checkSummary();

        pluginConfigure.setEnabled(pluginConfiguration.selected.length() > 0);
        pluginConfigure.setText(pluginConfiguration.selectedOptions.toString());
    }

    private void showPluginEditor() {
        Bundle bundle = new Bundle();
        bundle.putString("key", Key.pluginConfigure);
        bundle.putString(PluginConfigurationDialogFragment.PLUGIN_ID_FRAGMENT_TAG, pluginConfiguration.selected);
        PluginConfigurationDialogFragment fragment = new PluginConfigurationDialogFragment();
        fragment.setArguments(bundle);
        fragment.show(getFragmentManager(), PluginConfigurationDialogFragment.class.getName());
    }

    public void saveAndExit() {
        profile.deserialize();
        ProfileManager.updateProfile(profile);
        ProfilesFragment.instance.profilesAdapter.deepRefreshId(profile.id);
        getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        isProxyApps.setChecked(DataStore.proxyApps);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(Key.plugin)) {
            String selected = pluginConfiguration.selected;
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.pluginsOptions +
                    new PluginOptions(selected, (String) newValue), selected);
            DataStore.plugin = pluginConfiguration.toString();
            DataStore.dirty = true;

            pluginConfigure.setEnabled(true);
            pluginConfigure.setText(pluginConfiguration.selectedOptions.toString());

            if (!PluginManager.fetchPlugins().get(pluginConfiguration.selected).trusted) {
                Snackbar.make(getView(), R.string.plugin_untrusted, Snackbar.LENGTH_LONG).show();
            }

            return true;
        }

        return false;
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (!key.equals(Key.proxyApps) && findPreference(key) != null) {
            DataStore.dirty = true;
        }
    }

    @Override
    public boolean onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey().equals(Key.pluginConfigure)) {
            Intent intent = PluginManager.buildIntent(pluginConfiguration.selected, PluginContract.ACTION_CONFIGURE);
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(intent.putExtra(EXTRA_OPTIONS, pluginConfiguration.selectedOptions.toString()), REQUEST_CODE_PLUGIN_CONFIGURE);
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
                String options = data.getStringExtra(EXTRA_OPTIONS);
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
                AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.delete_confirm_prompt)
                        .setPositiveButton(R.string.yes, (dialog1, which) -> {
                            ProfileManager.delProfile(profile.id);
                            getActivity().finish();
                        })
                        .setNegativeButton(R.string.no, null)
                        .create();
                dialog.show();
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
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
        DataStore.unregisterChangeListener(this);
        super.onDestroy();
    }
}