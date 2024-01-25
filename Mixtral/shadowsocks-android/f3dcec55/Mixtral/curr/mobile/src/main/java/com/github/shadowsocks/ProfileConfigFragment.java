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
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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
import com.github.shadowsocks.utils.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.IntentUtils;
import com.github.shadowsocks.widget.ListListener;
import com.google.android.material.snackbar.Snackbar;
import java.util.Objects;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

public class ProfileConfigFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, PreferenceDataStore.OnPreferenceDataStoreChangeListener,
        Preference.SummaryProvider<EditTextPreference> {

    public static final PasswordSummaryProvider PASSWORD_SUMMARY_PROVIDER = new PasswordSummaryProvider();

    @Parcelize
    public static class ProfileIdArg implements Parcelable {
        public long profileId;

        public ProfileIdArg(long profileId) {
            this.profileId = profileId;
        }
    }

    public static class DeleteConfirmationDialogFragment extends AlertDialogFragment<ProfileIdArg, Empty> {
        @Override
        protected void prepare(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            builder.setTitle(R.string.delete_confirm_prompt);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> {
                ProfileManager.delProfile(arg.profileId);
                requireActivity().finish();
            });
            builder.setNegativeButton(R.string.no, null);
        }
    }

    private long profileId = -1;
    private SwitchPreference isProxyApps;
    private PluginPreference plugin;
    private EditTextPreference pluginConfigure;
    private PluginConfiguration pluginConfiguration;
    private BroadcastReceiver receiver;
    private Preference udpFallback;

    private void makeDirt() {
        DataStore.setDirty(true);
        if (activity instanceof ProfileConfigActivity) {
            ProfileConfigActivity profileConfigActivity = (ProfileConfigActivity) activity;
            profileConfigActivity.getUnsavedChangesHandler().setEnabled(true);
        }
    }
    
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(DataStore.privateStore);
        Activity activity = requireActivity();
        profileId = activity.getIntent().getLongExtra(Action.EXTRA_PROFILE_ID, -1);
        if (profileId != -1 && profileId != DataStore.editingId) {
            activity.finish();
            return;
        }
        addPreferencesFromResource(R.xml.pref_profile);
        EditTextPreference remotePort = findPreference(Key.remotePort);
        if (remotePort != null) {
            remotePort.setOnBindEditTextListener(new Function1<EditText, Unit>() {
                @Override
                public Unit invoke(EditText editText) {
                    EditTextPreferenceModifiers.Port.onBindEditText(editText);
                    return null;
                }
            });
        }
        EditTextPreference password = findPreference(Key.password);
        if (password != null) {
            password.setSummaryProvider(this);
        }
        int serviceMode = DataStore.serviceMode;
        Preference ipv6 = findPreference(Key.ipv6);
        if (ipv6 != null) {
            ipv6.setEnabled(serviceMode == Key.modeVpn);
        }
        isProxyApps = findPreference(Key.proxyApps);
        if (isProxyApps != null) {
            isProxyApps.setEnabled(serviceMode == Key.modeVpn);
            isProxyApps.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    startActivity(new Intent(activity, AppManager.class));
                    if (newValue instanceof Boolean && (boolean) newValue) {
                        makeDirt();
                    }
                    return true;
                }
            });
        }
        Preference metered = findPreference(Key.metered);
        if (metered != null) {
            if (Build.VERSION.SDK_INT >= 28) {
                metered.setEnabled(serviceMode == Key.modeVpn);
            } else {
                metered.setVisible(false);
            }
        }
        plugin = findPreference(Key.plugin);
        pluginConfigure = findPreference(Key.pluginConfigure);
        if (pluginConfigure != null) {
            pluginConfigure.setOnBindEditTextListener(new Function1<EditText, Unit>() {
                @Override
                public Unit invoke(EditText editText) {
                    PluginPreferenceModifiers.Monospace.onBindEditText(editText);
                    return null;
                }
            });
        }
        pluginConfigure.setOnPreferenceChangeListener(this);
        pluginConfiguration = new PluginConfiguration(DataStore.plugin);
        initPlugins();
        udpFallback = findPreference(Key.udpFallback);
        DataStore.privateStore.addOnPreferenceDataStoreChangedListener(this);

        Profile profile = ProfileManager.getProfile(profileId);
        if (profile != null) {
            if (profile.subscription == Profile.SubscriptionStatus.Active) {
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
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(listView, new ListListener());
        setFragmentResultListener(PluginPreferenceDialogFragment.class.getName(), new Function2<String, Bundle, Unit>() {
            @Override
            public Unit invoke(String requestKey, Bundle result) {
                Plugin pluginSelected = plugin.getPlugins().lookup.getValue(
                        result.getString(PluginPreferenceDialogFragment.KEY_SELECTED_ID));
                String override = pluginConfiguration.getPluginsOptions().keys().stream().filter(id ->
                        plugin.getPlugins().lookup.get(id) == pluginSelected
                ).findFirst().orElse(null);
                pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions(),
                        override != null ? override : pluginSelected.getId());
                DataStore.plugin = pluginConfiguration.toString();
                makeDirt();
                plugin.setValue(pluginConfiguration.getSelected());
                pluginConfigure.setEnabled(pluginSelected != NoPlugin.INSTANCE);
                pluginConfigure.setText(pluginConfiguration.getOptions().toString());
                if (!pluginSelected.isTrusted()) {
                    Snackbar.make(requireView(), R.string.plugin_untrusted, Snackbar.LENGTH_LONG).show();
                }
                return null;
            }
        });
        AlertDialogFragment.setResultListener(this, new Function2<Integer, Empty, Unit>() {
            @Override
            public Unit invoke(Integer which, Empty empty) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        saveAndExit();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        requireActivity().finish();
                        break;
                }
                return null;
            }
        });
    }

    private void initPlugins() {
        plugin.setValue(pluginConfiguration.getSelected());
        plugin.init();
        pluginConfigure.setEnabled(plugin.getSelectedEntry() != NoPlugin.INSTANCE);
        pluginConfigure.setText(pluginConfiguration.getOptions().toString());
    }

    private void showPluginEditor() {
        PluginConfigurationDialogFragment fragment = new PluginConfigurationDialogFragment();
        fragment.setArg(Key.pluginConfigure, pluginConfiguration.getSelected());
        fragment.setTargetFragment(this, 0);
        fragment.showAllowingStateLoss(Objects.requireNonNull(getParentFragmentManager()), Key.pluginConfigure);
    }

    private void saveAndExit() {
        Profile profile = ProfileManager.getProfile(profileId);
        if (profile == null) {
            profile = new Profile();
        }
        profile.setId(profileId);
        profile.deserialize();
        ProfileManager.updateProfile(profile);
        ProfilesFragment.instance.profilesAdapter.deepRefreshId(profileId);
        if (profileId in Core.activeProfileIds && DataStore.directBootAware) {
            DirectBoot.update();
        }
        requireActivity().finish();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(ProfileConfigFragment.this);
                lifecycleRegistry.markState(Lifecycle.State.CREATED);
                lifecycleScope.launch(Dispatchers.Main) {   // wait until changes were flushed
                    initPlugins();
                }
            }
        };
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, new IntentFilter(IntentUtils.ACTION_PACKAGE_CHANGED));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isProxyApps != null) {
            isProxyApps.setChecked(DataStore.proxyApps); // fetch proxyApps updated by AppManager
        }
        Profile fallbackProfile = DataStore.udpFallback != null ? ProfileManager.getProfile(DataStore.udpFallback) : null;
        if (udpFallback != null) {
            if (fallbackProfile == null) {
                udpFallback.setSummary(R.string.plugin_disabled);
            } else {
                udpFallback.setSummary(fallbackProfile.getFormattedName());
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            Plugin selected = pluginConfiguration.getSelected();
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.getPluginsOptions() +
                    Map.of(pluginConfiguration.getSelected(), new PluginOptions(selected, (String) newValue)), selected);
            DataStore.plugin = pluginConfiguration.toString();
            makeDirt();
            return true;
        } catch (RuntimeException exc) {
            Snackbar.make(requireView(), exc.getMessage(), Snackbar.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (!Objects.equals(key, Key.proxyApps) && findPreference(key) != null) {
            makeDirt();
        }
    }

    @Override
    public CharSequence provideSummary(EditTextPreference preference) {
        return "\u2022".repeat(preference.getText() == null ? 0 : preference.getText().length());
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey().equals(Key.plugin)) {
            PluginPreferenceDialogFragment fragment = new PluginPreferenceDialogFragment();
            fragment.setArg(Key.plugin);
            fragment.setTargetFragment(this, 0);
            fragment.showAllowingStateLoss(Objects.requireNonNull(getParentFragmentManager()), Key.plugin);
        } else if (preference.getKey().equals(Key.pluginConfigure)) {
            Intent intent = PluginManager.buildIntent(plugin.getSelectedEntry().getId(), PluginContract.ACTION_CONFIGURE);
            if (intent.resolveActivity(Objects.requireNonNull(requireContext().getPackageManager())) == null) {
                showPluginEditor();
            } else {
                ActivityResultLauncher<Intent> configurePlugin = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
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
                configurePlugin.launch(intent.putExtra(PluginContract.EXTRA_OPTIONS, pluginConfiguration.getOptions().toString()));
            }
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private ActivityResultLauncher<Intent> configurePlugin;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete) {
            DeleteConfirmationDialogFragment dialogFragment = new DeleteConfirmationDialogFragment();
            dialogFragment.arg(new ProfileIdArg(profileId));
            dialogFragment.key();
            dialogFragment.show(Objects.requireNonNull(getParentFragmentManager()), null);
            return true;
        } else if (item.getItemId() == R.id.action_apply) {
            saveAndExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDetach() {
        Objects.requireNonNull(requireContext()).unregisterReceiver(receiver);
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        DataStore.privateStore.removeOnPreferenceDataStoreChangedListener(this);
        super.onDestroy();
    }
}