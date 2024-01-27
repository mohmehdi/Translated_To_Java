package com.github.shadowsocks;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.preference.PreferenceDataStore;
import android.support.v7.preference.OnPreferenceDataStoreChangeListener;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.github.shadowsocks.App;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.CustomRulesFragment;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.bg.Executable;
import com.github.shadowsocks.bg.TrafficMonitor;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.responseLength;
import com.github.shadowsocks.utils.thread;
import com.github.shadowsocks.widget.ServiceButton;
import com.mikepenz.crossfader.Crossfader;
import com.mikepenz.crossfader.view.CrossFadeSlidingPaneLayout;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.interfaces.ICrossfader;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements ShadowsocksConnection.Interface, Drawer.OnDrawerItemClickListener,
OnPreferenceDataStoreChangeListener {
private static final String TAG = "ShadowsocksMainActivity";
private static final int REQUEST_CONNECT = 1;


private static final long DRAWER_PROFILES = 0L;
private static final long DRAWER_GLOBAL_SETTINGS = 1L;
private static final long DRAWER_ABOUT = 3L;
private static final long DRAWER_FAQ = 4L;
private static final long DRAWER_CUSTOM_RULES = 5L;

static OnPreferenceDataStoreChangeListener stateListener;

// UI
private ServiceButton fab;
private Crossfader<CrossFadeSlidingPaneLayout> crossfader;
private Drawer drawer;
private long previousSelectedDrawer;

private int testCount;
private TextView statusText;
private TextView txText;
private TextView rxText;
private TextView txRateText;
private TextView rxRateText;

private CustomTabsIntent customTabsIntent;
    private static final String TAG = "ShadowsocksMainActivity";
    private static final int REQUEST_CONNECT = 1;

    private static final long DRAWER_PROFILES = 0L;
    private static final long DRAWER_GLOBAL_SETTINGS = 1L;
    private static final long DRAWER_ABOUT = 3L;
    private static final long DRAWER_FAQ = 4L;
    private static final long DRAWER_CUSTOM_RULES = 5L;

    public static Consumer<Integer> stateListener;

    // UI
    private ServiceButton fab;
    private Crossfader<CrossFadeSlidingPaneLayout> crossfader;
    private Drawer drawer;
    private long previousSelectedDrawer = 0; // it's actually lateinit

    private int testCount = 0;
    private TextView statusText;
    private TextView txText;
    private TextView rxText;
    private TextView txRateText;
    private TextView rxRateText;

    private CustomTabsIntent customTabsIntent;

    {
        customTabsIntent = new CustomTabsIntent.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.material_primary_500))
                .build();
    }

    public void launchUrl(Uri uri) {
        try {
            customTabsIntent.launchUrl(this, uri);
        } catch (ActivityNotFoundException e) {
            // ignore
        }
    }

    public void launchUrl(String uri) {
        launchUrl(Uri.parse(uri));
    }
// service
int state = BaseService.IDLE;
private IShadowsocksServiceCallback.Stub serviceCallback = new IShadowsocksServiceCallback.Stub() {
    @Override
    public void stateChanged(int state, String profileName, String msg) throws RemoteException {
        App.app.handler.post(() -> changeState(state, msg, true));
    }

    @Override
    public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) throws RemoteException {
        App.app.handler.post(() -> updateTraffic(profileId, txRate, rxRate, txTotal, rxTotal));
    }

    @Override
    public void trafficPersisted(int profileId) throws RemoteException {
        App.app.handler.post(() -> ProfilesFragment.instance.onTrafficPersisted(profileId));
    }
};

public void changeState(int state, String msg, boolean animate) {
    fab.changeState(state, animate);
    switch (state) {
        case BaseService.CONNECTING:
            statusText.setText(R.string.connecting);
            break;
        case BaseService.CONNECTED:
            statusText.setText(R.string.vpn_connected);
            break;
        case BaseService.STOPPING:
            statusText.setText(R.string.stopping);
            break;
        default:
            if (msg != null) {
                Snackbar snackbar = Snackbar.make(findViewById(R.id.snackbar),
                        getString(R.string.vpn_error).format(Locale.ENGLISH, msg), Snackbar.LENGTH_LONG);
                snackbar.show();
                Log.e(TAG, "Error to start VPN service: " + msg);
            }
            statusText.setText(R.string.not_connected);
            break;
    }
    this.state = state;
    if (state != BaseService.CONNECTED) {
        updateTraffic(-1, 0, 0, 0, 0);
        testCount++;  // suppress previous test messages
    }
    ProfilesFragment.instance.profilesAdapter.notifyDataSetChanged();  // refresh button enabled state
    stateListener.invoke(state);
}

public void updateTraffic(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
    txText.setText(TrafficMonitor.formatTraffic(txTotal));
    rxText.setText(TrafficMonitor.formatTraffic(rxTotal));
    txRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(txRate)));
    rxRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(rxRate)));
    ToolbarFragment child = (ToolbarFragment) supportFragmentManager.findFragmentById(R.id.fragment_holder);
    if (state != BaseService.STOPPING)
        child.onTrafficUpdated(profileId, txRate, rxRate, txTotal, rxTotal);
}

private void testConnection(int id) {
    try {
        String protocol = "https";
        String host = "";
        switch (app.getCurrentProfile().getRoute()) {
            case Acl.CHINALIST:
                host = "www.qualcomm.cn";
                break;
            default:
                host = "www.google.com";
                break;
        }
        URL url = new URL(protocol, host, "/generate_204");
        HttpURLConnection conn;
        if (BaseService.usingVpnMode) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", DataStore.portProxy));
            conn = (HttpURLConnection) url.openConnection(proxy);
        }
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        conn.connect();
        long start = SystemClock.elapsedRealtime();
        int code = conn.getResponseCode();
        long elapsed = SystemClock.elapsedRealtime() - start;
        if (code == 204 || (code == 200 && conn.getResponseLength() == 0L)) {
            String result = getString(R.string.connection_test_available, elapsed);
            if (testCount == id) {
                app.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(result);
                    }
                });
            }
        } else {
            throw new Exception(getString(R.string.connection_test_error_status_code, code));
        }
    } catch (Exception e) {
        String result = getString(R.string.connection_test_error, e.getMessage());
        if (testCount == id) {
            app.handler.post(new Runnable() {
                @Override
                public void run() {
                    statusText.setText(R.string.connection_test_fail);
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG);
                    snackbar.show();
                }
            });
        }
    }
}

@Override
public boolean listenForDeath() {
    return true;
}

@Override
public void onServiceConnected(IShadowsocksService service) {
    changeState(service.getState());
}

@Override
public void onServiceDisconnected() {
    changeState(BaseService.IDLE);
}

@Override
public void binderDied() {
    app.handler.post(new Runnable() {
        @Override
        public void run() {
            connection.disconnect();
            Executable.killAll();
            connection.connect();
        }
    });
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
        app.startService();
    } else {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.snackbar), R.string.vpn_permission_denied, Snackbar.LENGTH_LONG);
        snackbar.show();
        Log.e(TAG, "Failed to start VpnService: " + data);
    }
}


@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.layout_main);

    customTabsIntent = new CustomTabsIntent.Builder()
            .setToolbarColor(ContextCompat.getColor(this, R.color.material_primary_500))
            .build();

    DrawerBuilder drawerBuilder = new DrawerBuilder()
            .withActivity(this)
            .withTranslucentStatusBar(true)
            .withHeader(R.layout.layout_header)
            .addDrawerItems(
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER_PROFILES)
                            .withName(R.string.profiles)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic_action_description))
                            .withIconTintingEnabled(true),
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER_CUSTOM_RULES)
                            .withName(R.string.custom_rules)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic_action_assignment))
                            .withIconTintingEnabled(true),
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER_GLOBAL_SETTINGS)
                            .withName(R.string.settings)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic_action_settings))
                            .withIconTintingEnabled(true)
            )
            .addStickyDrawerItems(
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER_FAQ)
                            .withName(R.string.faq)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic_action_help_outline))
                            .withIconTintingEnabled(true)
                            .withSelectable(false),
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER_ABOUT)
                            .withName(R.string.about)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic_action_copyright))
                            .withIconTintingEnabled(true)
            )
            .withOnDrawerItemClickListener(this)
            .withActionBarDrawerToggle(true)
            .withSavedInstance(savedInstanceState);

    int miniDrawerWidth = (int) getResources().getDimension(R.dimen.material_mini_drawer_item);
    if (getResources().getDisplayMetrics().widthPixels >=
            getResources().getDimension(R.dimen.profile_item_max_width) + miniDrawerWidth) {
        drawer = drawerBuilder.withGenerateMiniDrawer(true).buildView();
        Crossfader<CrossFadeSlidingPaneLayout> crossfader = new Crossfader<>();
        this.crossfader = crossfader;
        crossfader
                .withContent(findViewById(android.R.id.content))
                .withFirst(drawer.getSlider(), getResources().getDimensionPixelSize(R.dimen.material_drawer_width))
                .withSecond(drawer.getMiniDrawer().build(this), miniDrawerWidth)
                .withSavedInstance(savedInstanceState)
                .build();
        if (getResources().getConfiguration().layoutDirection == View.LAYOUT_DIRECTION_RTL)
            crossfader.crossFadeSlidingPaneLayout.setShadowDrawableRight(AppCompatResources.getDrawable(this, R.drawable.material_drawer_shadow_right));
        else crossfader.crossFadeSlidingPaneLayout.setShadowDrawableLeft(AppCompatResources.getDrawable(this, R.drawable.material_drawer_shadow_left));
        drawer.getMiniDrawer().withCrossFader(new ICrossfader() {
            @Override
            public boolean isCrossfaded() {
                return crossfader.isCrossFaded();
            }

            @Override
            public void crossfade() {
                crossfader.crossFade();
            }
        });
    } else drawer = drawerBuilder.build();

    if (savedInstanceState == null) displayFragment(new ProfilesFragment());
    previousSelectedDrawer = drawer.getCurrentSelection();
    statusText = findViewById(R.id.status);
    txText = findViewById(R.id.tx);
    txRateText = findViewById(R.id.txRate);
    rxText = findViewById(R.id.rx);
    rxRateText = findViewById(R.id.rxRate);
    findViewById(R.id.stat).setOnClickListener(view -> {
        if (state == BaseService.CONNECTED) {
            ++testCount;
            statusText.setText(R.string.connection_test_testing);
            int id = testCount;  // it would change by other code
            thread(() -> testConnection(id));
        }
    });

    fab = findViewById(R.id.fab);
    fab.setOnClickListener(view -> {
        if (state == BaseService.CONNECTED) App.app.stopService();
        else thread(() -> {
            if (BaseService.usingVpnMode) {
                Intent intent = VpnService.prepare(this);
                if (intent != null) startActivityForResult(intent, REQUEST_CONNECT);
                else App.app.handler.post(() -> onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null));
            } else App.app.startService();
        });
    });

    changeState(BaseService.IDLE);   // reset everything to init state
    App.app.handler.post(() -> connection.connect());
    DataStore.publicStore.registerChangeListener(this);

    Intent intent = this.getIntent();
    if (intent != null) handleShareIntent(intent);
}

@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleShareIntent(intent);
}

private void handleShareIntent(Intent intent) {
    String sharedStr = null;
    if (Intent.ACTION_VIEW.equals(intent.getAction())) {
        sharedStr = intent.getData().toString();
    } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
        Object[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null && rawMsgs.length > 0) {
            sharedStr = new String((((NdefMessage) rawMsgs[0]).getRecords()[0]).getPayload());
        }
    }
    if (sharedStr == null) return;
    List<Profile> profiles = Profile.findAll(sharedStr);
    if (profiles.isEmpty()) {
        Snackbar.make(findViewById(R.id.snackbar), R.string.profile_invalid_input, Snackbar.LENGTH_LONG).show();
        return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.add_profile_dialog);
    builder.setPositiveButton(R.string.yes, (dialog, which) -> {
        for (Profile profile : profiles) {
            ProfileManager.createProfile(profile);
        }
    });
    builder.setNegativeButton(R.string.no, null);
    builder.setMessage(profiles.toString());
    builder.create().show();
}

@Override
public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
    if (Key.serviceMode.equals(key)) App.app.handler.post(() -> {
        connection.disconnect();
        connection.connect();
    });
}

private void displayFragment(ToolbarFragment fragment) {
    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_holder, fragment).commitAllowingStateLoss();
    drawer.closeDrawer();
}

@Override
public boolean onItemClick(View view, int position, IDrawerItem<*, *> drawerItem) {
    long id = drawerItem.getIdentifier();
    if (id == previousSelectedDrawer) drawer.closeDrawer();
    else {
        previousSelectedDrawer = id;
        switch ((int) id) {
            case (int) DRAWER_PROFILES:
                displayFragment(new ProfilesFragment());
                break;
            case (int) DRAWER_GLOBAL_SETTINGS:
                displayFragment(new GlobalSettingsFragment());
                break;
            case (int) DRAWER_ABOUT:
                App.app.track(TAG, "about");
                displayFragment(new AboutFragment());
                break;
            case (int) DRAWER_FAQ:
                launchUrl(getString(R.string.faq_url));
                break;
            case (int) DRAWER_CUSTOM_RULES:
                displayFragment(new CustomRulesFragment());
                break;
            default:
                return false;
        }
    }
    return true;
}

@Override
protected void onResume() {
    super.onResume();
    App.app.remoteConfig.fetch();
}

@Override
protected void onStart() {
    super.onStart();
    connection.listeningForBandwidth = true;
}

@Override
public void onBackPressed() {
    if (drawer.isDrawerOpen()) drawer.closeDrawer();
    else {
        ToolbarFragment currentFragment = (ToolbarFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (!currentFragment.onBackPressed())
            if (currentFragment instanceof ProfilesFragment) super.onBackPressed();
            else drawer.setSelection(DRAWER_PROFILES);
    }
}

@Override
protected void onStop() {
    super.onStop();
    connection.listeningForBandwidth = false;
}

@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    drawer.saveInstanceState(outState);
    if (crossfader != null) crossfader.saveInstanceState(outState);
}

@Override
protected void onDestroy() {
    super.onDestroy();
    DataStore.publicStore.unregisterChangeListener(this);
    connection.disconnect();
    BackupManager backupManager = new BackupManager(this);
    backupManager.dataChanged();
    App.app.handler.removeCallbacksAndMessages(null);
}
}