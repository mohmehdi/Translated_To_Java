package com.github.shadowsocks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.lifecycleScope;
import androidx.lifecycle.whenCreated;
import androidx.preference.*;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.*;
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment;
import com.github.shadowsocks.plugin.fragment.Empty;
import com.github.shadowsocks.plugin.fragment.showAllowingStateLoss;
import com.github.shadowsocks.preference.*;
import com.github.shadowsocks.utils.*;
import com.github.shadowsocks.widget.ListListener;
import com.google.android.material.snackbar.Snackbar;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;
import kotlinx.parcelize.Parcelize;

public class ProfileConfigFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, OnPreferenceDataStoreChangeListener {

    public static class PasswordSummaryProvider implements Preference.SummaryProvider<EditTextPreference> {
        @Override
        public CharSequence provideSummary(EditTextPreference preference) {
            return "\u2022".repeat(preference.getText().length());
        }
    }

    @Parcelize
    public static class ProfileIdArg implements Parcelable {
        public final long profileId;

        public ProfileIdArg(long profileId) {
            this.profileId = profileId;
        }
    }

    public static class DeleteConfirmationDialogFragment extends AlertDialogFragment<ProfileIdArg, Empty> {
        @Override
        protected void prepare(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
            builder.setTitle(R.string.delete_confirm_prompt)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        ProfileManager.delProfile(getArg().profileId);
                        requireActivity().finish();
                    })
                    .setNegativeButton(R.string.no, null);
        }
    }

    private long profileId = -1L;
    private SwitchPreference isProxyApps;
    private PluginPreference plugin;
    private EditTextPreference pluginConfigure;
    private PluginConfiguration pluginConfiguration;
    private BroadcastReceiver receiver;
    private Preference udpFallback;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setPreferenceDataStore(DataStore.privateStore);
        Activity activity = requireActivity();
        profileId = activity.getIntent().getLongExtra(Action.EXTRA_PROFILE_ID, -1L);
        if (profileId != -1L && profileId != DataStore.editingId) {
            activity.finish();
            return;
        }
        addPreferencesFromResource(R.xml.pref_profile);
        findPreference(Key.remotePort).setOnBindEditTextListener(EditTextPreferenceModifiers.Port);
        findPreference(Key.password).setSummaryProvider(new PasswordSummaryProvider());
        int serviceMode = DataStore.serviceMode;
        findPreference(Key.ipv6).setEnabled(serviceMode == Key.modeVpn);
        isProxyApps = findPreference(Key.proxyApps);
        isProxyApps.setEnabled(serviceMode == Key.modeVpn);
        isProxyApps.setOnPreferenceChangeListener((preference, newValue) -> {
            startActivity(new Intent(activity, AppManager.class));
            if ((Boolean) newValue) DataStore.dirty = true;
            return newValue;
        });
        findPreference(Key.metered).apply {
            if (Build.VERSION.SDK_INT >= 28) setEnabled(serviceMode == Key.modeVpn);
            else remove();
        }
        plugin = findPreference(Key.plugin);
        pluginConfigure = findPreference(Key.pluginConfigure);
        pluginConfigure.setOnBindEditTextListener(EditTextPreferenceModifiers.Monospace);
        pluginConfigure.setOnPreferenceChangeListener(this);
        pluginConfiguration = new PluginConfiguration(DataStore.plugin);
        initPlugins();
        udpFallback = findPreference(Key.udpFallback);
        DataStore.privateStore.registerChangeListener(this);

        Profile profile = ProfileManager.getProfile(profileId);
        if (profile == null) {
            profile = new Profile();
        }
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

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(getListView(), ListListener);
        setFragmentResultListener(PluginPreferenceDialogFragment.class.getName(), (requestKey, bundle) -> {
            String selectedId = bundle.getString(PluginPreferenceDialogFragment.KEY_SELECTED_ID);
            PluginEntry selected = plugin.plugins.lookup.get(selectedId);
            String override = pluginConfiguration.pluginsOptions.keySet().stream()
                    .filter(key -> plugin.plugins.lookup.get(key) == selected)
                    .findFirst()
                    .orElse(null);
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.pluginsOptions, override != null ? override : selected.id);
            DataStore.plugin = pluginConfiguration.toString();
            DataStore.dirty = true;
            plugin.setValue(pluginConfiguration.selected);
            pluginConfigure.setEnabled(!(selected instanceof NoPlugin));
            pluginConfigure.setText(pluginConfiguration.getOptions().toString());
            if (!(selected.trusted)) {
                Snackbar.make(requireView(), R.string.plugin_untrusted, Snackbar.LENGTH_LONG).show();
            }
        });
        AlertDialogFragment.setResultListener(ProfileConfigActivity.UnsavedChangesDialogFragment.class, (which, empty) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    saveAndExit();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    requireActivity().finish();
                    break;
            }
        });
    }

    private void initPlugins() {
        plugin.setValue(pluginConfiguration.selected);
        plugin.init();
        pluginConfigure.setEnabled(!(plugin.selectedEntry instanceof NoPlugin));
        pluginConfigure.setText(pluginConfiguration.getOptions().toString());
    }

    private void showPluginEditor() {
        PluginConfigurationDialogFragment fragment = new PluginConfigurationDialogFragment();
        fragment.setArg(Key.pluginConfigure, pluginConfiguration.selected);
        fragment.setTargetFragment(this, 0);
        fragment.showAllowingStateLoss(getParentFragmentManager(), Key.pluginConfigure);
    }

    private void saveAndExit() {
        Profile profile = ProfileManager.getProfile(profileId);
        if (profile == null) {
            profile = new Profile();
        }
        profile.id = profileId;
        profile.deserialize();
        ProfileManager.updateProfile(profile);
        ProfilesFragment.instance.profilesAdapter.deepRefreshId(profileId);
        if (profileId == Core.activeProfileIds && DataStore.directBootAware) {
            DirectBoot.update();
        }
        requireActivity().finish();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        receiver = context.listenForPackageChanges(false, () -> {
            lifecycleScope.launch(Dispatchers.Main) {
                whenCreated(() -> initPlugins());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        isProxyApps.setChecked(DataStore.proxyApps);
        Profile fallbackProfile = DataStore.udpFallback != null ? ProfileManager.getProfile(DataStore.udpFallback) : null;
        if (fallbackProfile == null) {
            udpFallback.setSummary(R.string.plugin_disabled);
        } else {
            udpFallback.setSummary(fallbackProfile.formattedName);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            String selected = pluginConfiguration.selected;
            pluginConfiguration = new PluginConfiguration(pluginConfiguration.pluginsOptions +
                    Map.of(selected, new PluginOptions(selected, (String) newValue)), selected);
            DataStore.plugin = pluginConfiguration.toString();
            DataStore.dirty = true;
            return true;
        } catch (RuntimeException exc) {
            Snackbar.make(requireView(), exc.getReadableMessage(), Snackbar.LENGTH_LONG).show();
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
        switch (preference.getKey()) {
            case Key.plugin:
                PluginPreferenceDialogFragment pluginDialogFragment = new PluginPreferenceDialogFragment();
                pluginDialogFragment.setArg(Key.plugin);
                pluginDialogFragment.setTargetFragment(this, 0);
                pluginDialogFragment.showAllowingStateLoss(getParentFragmentManager(), Key.plugin);
                break;
            case Key.pluginConfigure:
                Intent intent = PluginManager.buildIntent(plugin.selectedEntry.id, PluginContract.ACTION_CONFIGURE);
                if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
                    showPluginEditor();
                } else {
                    configurePlugin.launch(intent.putExtra(PluginContract.EXTRA_OPTIONS, pluginConfiguration.getOptions().toString()));
                }
                break;
            default:
                super.onDisplayPreferenceDialog(preference);
        }
    }

    private final ActivityResultLauncher<Intent> configurePlugin = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                int resultCode = result.getResultCode();
                Intent data = result.getData();
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        String options = data != null ? data.getStringExtra(PluginContract.EXTRA_OPTIONS) : null;
                        pluginConfigure.setText(options);
                        onPreferenceChange(pluginConfigure, options);
                        break;
                    case PluginContract.RESULT_FALLBACK:
                        showPluginEditor();
                        break;
                }
            });

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                DeleteConfirmationDialogFragment deleteDialogFragment = new DeleteConfirmationDialogFragment();
                deleteDialogFragment.arg(new ProfileIdArg(profileId));
                deleteDialogFragment.key();
                deleteDialogFragment.show(getParentFragmentManager(), null);
                return true;
            case R.id.action_apply:
                saveAndExit();
                return true;
            default:
                return false;
        }
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