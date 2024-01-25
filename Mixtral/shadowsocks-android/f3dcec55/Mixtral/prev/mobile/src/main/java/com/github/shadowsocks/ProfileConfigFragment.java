package com.github.shadowsocks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.MenuItem;
import android.view.View;
import android.widget.Snackbar;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.preference.*;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.*;
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment;
import com.github.shadowsocks.plugin.fragment.Empty;
import com.github.shadowsocks.preference.EditTextPreferenceModifiers;
import com.github.shadowsocks.preference.Key;
import com.github.shadowsocks.preference.PasswordSummaryProvider;
import com.github.shadowsocks.preference.PluginPreferenceDialogFragment;
import com.github.shadowsocks.preference.PluginPreferenceModifiers;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.AppManager;
import com.github.shadowsocks.utils.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.ListListener;
import com.github.shadowsocks.widget.ListPreference;
import com.google.android.material.snackbar.Snackbar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class ProfileConfigFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, OnPreferenceDataStoreChangeListener, LifecycleOwner {

    public static class PasswordSummaryProvider implements Preference.SummaryProvider<EditTextPreference> {
        @Override
        public String provideSummary(EditTextPreference preference) {
            String text = preference.getText();
            return text != null ? "\u2022".repeat(text.length()) : "";
        }
    }

    public static class DeleteConfirmationDialogFragment extends AlertDialogFragment<ProfileIdArg, Empty> {
        @Override
        protected void prepare(DialogInterface.OnClickListener listener) {
            setTitle(R.string.delete_confirm_prompt);
            setPositiveButton(R.string.yes, (dialog, which) -> {
                ProfileManager.delProfile(arg.profileId);
                requireActivity().finish();
            });
            setNegativeButton(R.string.no, null);
        }
    }

    private long profileId = -1;
    private SwitchPreference isProxyApps;
    private PluginPreference plugin;
    private EditTextPreference pluginConfigure;
    private PluginConfiguration pluginConfiguration;
    private BroadcastReceiver receiver;
    private Preference udpFallback;
    private LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(DataStore.privateStore);
        Activity activity = requireActivity();
        profileId = Objects.requireNonNull(activity.getIntent()).getLongExtra(Action.EXTRA_PROFILE_ID, -1L);
        if (profileId != -1L && profileId != DataStore.editingId) {
            activity.finish();
            return;
        }
        addPreferencesFromResource(R.xml.pref_profile);
        EditTextPreference remotePort = findPreference(Key.remotePort);
        EditTextPreferenceModifiers.Port.setOnBindEditTextListener(remotePort);
        Preference password = findPreference(Key.password);
        password.setSummaryProvider(PasswordSummaryProvider.getInstance());
        int serviceMode = DataStore.serviceMode;
        Preference ipv6 = findPreference(Key.ipv6);
        ipv6.setEnabled(serviceMode == Key.modeVpn);
        isProxyApps = findPreference(Key.proxyApps);
        isProxyApps.setEnabled(serviceMode == Key.modeVpn);
        isProxyApps.setOnPreferenceChangeListener((preference, newValue) -> {
            startActivity(new Intent(activity, AppManager.class));
            if (newValue instanceof Boolean) {
                DataStore.dirty = (boolean) newValue;
            }
            return true;
        });
        Preference metered = findPreference(Key.metered);
        if (Build.VERSION.SDK_INT >= 28) {
            metered.setEnabled(serviceMode == Key.modeVpn);
        } else {
            metered.setVisible(false);
        }
        plugin = findPreference(Key.plugin);
        pluginConfigure = findPreference(Key.pluginConfigure);
        PluginPreferenceModifiers.Monospace.setOnBindEditTextListener(pluginConfigure);
        pluginConfigure.setOnPreferenceChangeListener(this);
        pluginConfiguration = new PluginConfiguration(DataStore.plugin);
        initPlugins();
        udpFallback = findPreference(Key.udpFallback);
        DataStore.privateStore.registerChangeListener(this);

        Profile profile = ProfileManager.getProfile(profileId);
        if (profile != null && profile.subscription == Profile.SubscriptionStatus.Active) {
            findPreference(Key.name).setEnabled(false);
            findPreference(Key.host).setEnabled(false);
            findPreference(Key.password).setEnabled(false);
            findPreference(Key.method).setEnabled(false);
            findPreference(Key.remotePort).setEnabled(false);
            plugin.setEnabled(false);
            pluginConfigure.setEnabled(false);
            udpFallback.setEnabled(false);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener.getInstance());
        setFragmentResultListener(PluginPreferenceDialogFragment.class.getName(), new ActivityResultCallback<Bundle>() {
            @Override
            public void onActivityResult(Bundle result) {
                String selectedId = result.getString(PluginPreferenceDialogFragment.KEY_SELECTED_ID);
                Plugin selected = plugin.getPlugins().lookup.get(selectedId);
                String override = pluginConfiguration.getPluginsOptions().keySet().stream()
                        .filter(id -> plugin.getPlugins().lookup.get(id) == selected)
                        .findFirst()
                        .orElse(null);
                pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions(),
                        override != null ? override : selected.getId());
                DataStore.plugin = pluginConfiguration.toString();
                DataStore.dirty = true;
                plugin.setValue(selected);
                pluginConfigure.setEnabled(selected != NoPlugin.getInstance());
                pluginConfigure.setText(pluginConfiguration.getOptions().toString());
                if (!selected.isTrusted()) {
                    Snackbar.make(requireView(), R.string.plugin_untrusted, Snackbar.LENGTH_LONG).show();
                }
            }
        });
        AlertDialogFragment.setResultListener(this, new AlertDialogFragment.ResultListener<Empty>() {
            @Override
            public void onResult(int which, Empty result) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    saveAndExit();
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    requireActivity().finish();
                }
            }
        });
    }

    private void initPlugins() {
        plugin.setValue(pluginConfiguration.getSelected());
        plugin.init();
        pluginConfigure.setEnabled(plugin.getSelectedEntry() instanceof NoPlugin);
        pluginConfigure.setText(pluginConfiguration.getOptions().toString());
    }

    private void showPluginEditor() {
        PluginConfigurationDialogFragment fragment = new PluginConfigurationDialogFragment();
        fragment.setArg(Key.pluginConfigure, pluginConfiguration.getSelected());
        fragment.setTargetFragment(this, 0);
        fragment.show(getParentFragmentManager(), Key.pluginConfigure);
    }

    private void saveAndExit() {
        Profile profile = ProfileManager.getProfile(profileId) != null ? ProfileManager.getProfile(profileId) : new Profile();
        profile.setId(profileId);
        profile.deserialize();
        ProfileManager.updateProfile(profile);
        ProfilesFragment.instance.profilesAdapter.deepRefreshId(profileId);
        if (profileId == Core.activeProfileIds.get(0) && DataStore.directBootAware) {
            DirectBoot.update();
        }
        requireActivity().finish();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        receiver = context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                lifecycleScope.launch(Dispatchers.Main, new Continuation<Unit, Exception>() {
                    @Override
                    public Unit invoke(Object o) {
                        initPlugins();
                        return null;
                    }

                    @Override
                    public Exception resumeWith(Object result) {
                        return null;
                    }
                });
            }
        }, new IntentFilter("android.intent.action.PACKAGE_ADDED"));
        ViewTreeLifecycleOwner.set(listView, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        isProxyApps.setChecked(DataStore.proxyApps);
        Preference fallbackProfile = DataStore.udpFallback != null ? findPreference(Key.udpFallback) : null;
        if (fallbackProfile != null) {
            Profile fallback = ProfileManager.getProfile(DataStore.udpFallback);
            if (fallback == null) {
                fallbackProfile.setSummary(R.string.plugin_disabled);
            } else {
                fallbackProfile.setSummary(fallback.formattedName);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            Plugin selected = pluginConfiguration.getSelected();
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions() +
                    Map.of(pluginConfiguration.getSelected().getId(), new PluginOptions(selected, (String) newValue)), selected);
            DataStore.plugin = pluginConfiguration.toString();
            DataStore.dirty = true;
            return true;
        } catch (RuntimeException exc) {
            Snackbar.make(requireView(), exc.getMessage(), Snackbar.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (!Key.proxyApps.equals(key) && findPreference(key) != null) {
            DataStore.dirty = true;
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey().equals(Key.plugin)) {
            PluginPreferenceDialogFragment fragment = new PluginPreferenceDialogFragment();
            fragment.setArg(Key.plugin);
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), Key.plugin);
        } else if (preference.getKey().equals(Key.pluginConfigure)) {
            Intent intent = PluginManager.buildIntent(plugin.getSelectedEntry().getId(), PluginContract.ACTION_CONFIGURE);
            if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
                showPluginEditor();
            } else {
                configurePlugin.launch(intent
                        .putExtra(PluginContract.EXTRA_OPTIONS, pluginConfiguration.getOptions().toString()));
            }
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private final ActivityResultLauncher<Intent> configurePlugin = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == Activity.RESULT_OK) {
                String options = result.getData().getStringExtra(PluginContract.EXTRA_OPTIONS);
                pluginConfigure.setText(options);
                onPreferenceChange(pluginConfigure, options);
            } else if (result.getResultCode() == PluginContract.RESULT_FALLBACK) {
                showPluginEditor();
            }
        }
    });

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete) {
            DeleteConfirmationDialogFragment fragment = new DeleteConfirmationDialogFragment();
            fragment.arg(new ProfileIdArg(profileId));
            fragment.key();
            fragment.show(getParentFragmentManager(), null);
            return true;
        } else if (item.getItemId() == R.id.action_apply) {
            saveAndExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDetach() {
        requireContext().unregisterReceiver(receiver);
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        DataStore.privateStore.unregisterChangeListener(this);
        super.onDestroy();
    }
}