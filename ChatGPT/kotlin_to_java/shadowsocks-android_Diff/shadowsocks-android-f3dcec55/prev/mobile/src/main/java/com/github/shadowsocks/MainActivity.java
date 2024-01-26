package com.github.shadowsocks;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.net.UriCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.updateLayoutParams;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceDataStore;
import com.github.shadowsocks.acl.CustomRulesFragment;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.ShadowsocksConnection;
import com.github.shadowsocks.aidl.TrafficStats;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener;
import com.github.shadowsocks.subscription.SubscriptionFragment;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.StartService;
import com.github.shadowsocks.widget.ListHolderListener;
import com.github.shadowsocks.widget.ServiceButton;
import com.github.shadowsocks.widget.StatsBar;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.ktx.Firebase;

public class MainActivity extends AppCompatActivity implements ShadowsocksConnection.Callback, OnPreferenceDataStoreChangeListener,
        NavigationView.OnNavigationItemSelectedListener {
    public static ((BaseService.State) -> Unit) stateListener;

    private ServiceButton fab;
    private StatsBar stats;
    private DrawerLayout drawer;
    private NavigationView navigation;

    private CoordinatorLayout snackbar;
    public Snackbar snackbar(CharSequence text) {
        return Snackbar.make(snackbar, text, Snackbar.LENGTH_LONG).setAnchorView(fab);
    }

    private CustomTabsIntent customTabsIntent;
    public void launchUrl(String uri) {
        try {
            customTabsIntent.launchUrl(this, UriCompat.parseUri(uri, UriCompat.URI_ALLOW_UNSAFE));
        } catch (ActivityNotFoundException e) {
            snackbar(uri).show();
        }
    }

    private BaseService.State state = BaseService.State.Idle;
    public void stateChanged(BaseService.State state, String profileName, String msg) {
        changeState(state, msg);
    }
    public void trafficUpdated(long profileId, TrafficStats stats) {
        if (profileId == 0L) this.stats.updateTraffic(
                stats.getTxRate(), stats.getRxRate(), stats.getTxTotal(), stats.getRxTotal());
        if (state != BaseService.State.Stopping) {
            ProfilesFragment profilesFragment = (ProfilesFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
            if (profilesFragment != null) {
                profilesFragment.onTrafficUpdated(profileId, stats);
            }
        }
    }
    public void trafficPersisted(long profileId) {
        ProfilesFragment profilesFragment = ProfilesFragment.instance;
        if (profilesFragment != null) {
            profilesFragment.onTrafficPersisted(profileId);
        }
    }

    private void changeState(BaseService.State state, String msg, boolean animate) {
        fab.changeState(state, this.state, animate);
        stats.changeState(state, animate);
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show();
        this.state = state;
        ProfilesFragment profilesFragment = ProfilesFragment.instance;
        if (profilesFragment != null) {
            profilesFragment.profilesAdapter.notifyDataSetChanged();
        }
        if (stateListener != null) {
            stateListener.invoke(state);
        }
    }

    private void toggle() {
        if (state.canStop()) {
            Core.stopService();
        } else {
            connect.launch(null);
        }
    }

    private ShadowsocksConnection connection = new ShadowsocksConnection(true);
    public void onServiceConnected(IShadowsocksService service) {
        changeState(BaseService.State.values()[service.getState()], null, null);
    }
    public void onServiceDisconnected() {
        changeState(BaseService.State.Idle, null, null);
    }
    public void onBinderDied() {
        connection.disconnect(this);
        connection.connect(this, this);
    }

    private StartService connect = registerForActivityResult(new StartService(), result -> {
        if (result) {
            snackbar().setText(R.string.vpn_permission_denied).show();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.layout_main);
        snackbar = findViewById(R.id.snackbar);
        ViewCompat.setOnApplyWindowInsetsListener(snackbar, ListHolderListener.INSTANCE);
        stats = findViewById(R.id.stats);
        stats.setOnClickListener(v -> {
            if (state == BaseService.State.Connected) {
                stats.testConnection();
            }
        });
        drawer = findViewById(R.id.drawer);
        navigation = findViewById(R.id.navigation);
        navigation.setNavigationItemSelectedListener(this);
        if (savedInstanceState == null) {
            navigation.getMenu().findItem(R.id.profiles).setChecked(true);
            displayFragment(new ProfilesFragment());
        }

        fab = findViewById(R.id.fab);
        fab.initProgress(findViewById(R.id.fabProgress));
        fab.setOnClickListener(v -> toggle());
        ViewCompat.setOnApplyWindowInsetsListener(fab, (view, insets) -> {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            layoutParams.bottomMargin = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom +
                    getResources().getDimensionPixelOffset(R.dimen.mtrl_bottomappbar_fab_bottom_margin);
            return insets;
        });

        changeState(BaseService.State.Idle, false);
        connection.connect(this, this);
        DataStore.publicStore.registerChangeListener(this);
    }

    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        switch (key) {
            case Key.serviceMode:
                connection.disconnect(this);
                connection.connect(this, this);
                break;
        }
    }

    private void displayFragment(ToolbarFragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_holder, fragment).commitAllowingStateLoss();
        drawer.closeDrawers();
    }

    public boolean onNavigationItemSelected(MenuItem item) {
        if (item.isChecked()) {
            drawer.closeDrawers();
        } else {
            switch (item.getItemId()) {
                case R.id.profiles:
                    displayFragment(new ProfilesFragment());
                    connection.bandwidthTimeout = connection.bandwidthTimeout;
                    break;
                case R.id.globalSettings:
                    displayFragment(new GlobalSettingsFragment());
                    break;
                case R.id.about:
                    FirebaseAnalytics.getInstance(this).logEvent("about", null);
                    displayFragment(new AboutFragment());
                    break;
                case R.id.faq:
                    launchUrl(getString(R.string.faq_url));
                    return true;
                case R.id.customRules:
                    displayFragment(new CustomRulesFragment());
                    break;
                case R.id.subscriptions:
                    displayFragment(new SubscriptionFragment());
                    break;
                default:
                    return false;
            }
            item.setChecked(true);
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        connection.bandwidthTimeout = 500;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawers();
        } else {
            ToolbarFragment currentFragment = (ToolbarFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
            if (!currentFragment.onBackPressed()) {
                if (currentFragment instanceof ProfilesFragment) {
                    super.onBackPressed();
                } else {
                    navigation.getMenu().findItem(R.id.profiles).setChecked(true);
                    displayFragment(new ProfilesFragment());
                }
            }
        }
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_G && event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            toggle();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_T && event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            stats.testConnection();
            return true;
        } else {
            ToolbarFragment currentFragment = (ToolbarFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
            if (currentFragment != null) {
                currentFragment.toolbar.getMenu().setQwertyMode(KeyCharacterMap.load(event.getDeviceId()).getKeyboardType() != KeyCharacterMap.NUMERIC);
                currentFragment.toolbar.getMenu().performShortcut(keyCode, event, 0);
            }
            return false;
        }
    }

    @Override
    protected void onStop() {
        connection.bandwidthTimeout = 0;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DataStore.publicStore.unregisterChangeListener(this);
        connection.disconnect(this);
    }
}