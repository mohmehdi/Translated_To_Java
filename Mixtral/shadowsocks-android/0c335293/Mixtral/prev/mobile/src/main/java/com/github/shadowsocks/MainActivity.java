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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.preference.PreferenceDataStore;
import android.support.v7.widget.TooltipCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.github.jorgecastilloprz.FABProgressCircle;
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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
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

var stateListener: ((Int) -> Unit)? = null

// UI
private FloatingActionButton fab;
private FABProgressCircle fabProgressCircle;
private Crossfader<CrossFadeSlidingPaneLayout> crossfader;
private Drawer drawer;
private long previousSelectedDrawer; // it's actually lateinit

private int testCount = 0;
private TextView statusText;
private TextView txText;
private TextView rxText;
private TextView txRateText;
private TextView rxRateText;

private CustomTabsIntent customTabsIntent;
public void launchUrl(Uri uri) {
    try {
        customTabsIntent.launchUrl(this, uri);
    } catch (ActivityNotFoundException e) { }  // ignore
} 
public void launchUrl(String uri) {
    launchUrl(Uri.parse(uri));
}

private int greyTint;
private int greenTint;

private void hideCircle() {
    try {
        fabProgressCircle.hide();
    } catch (NullPointerException e) { }   // ignore
}

// service
private int state = BaseService.IDLE;
private IShadowsocksServiceCallback serviceCallback = new IShadowsocksServiceCallback.Stub() {
    @Override
    public void stateChanged(int state, String profileName, String msg) {
        app.handler.post(() -> changeState(state, msg));
    }
    @Override
    public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
        app.handler.post(() -> updateTraffic(profileId, txRate, rxRate, txTotal, rxTotal));
    }
    @Override
    public void trafficPersisted(int profileId) {
        app.handler.post(() -> ProfilesFragment.instance.onTrafficPersisted(profileId));
    }
};

public void changeState(int state, String msg) {
    switch (state) {
        case BaseService.CONNECTING:
            fab.setImageResource(R.drawable.ic_start_busy);
            fabProgressCircle.show();
            statusText.setText(R.string.connecting);
            break;
        case BaseService.CONNECTED:
            if (MainActivity.this.state == BaseService.CONNECTING) fabProgressCircle.beginFinalAnimation();
            else fabProgressCircle.postDelayed(MainActivity.this::hideCircle, 1000);
            fab.setImageResource(R.drawable.ic_start_connected);
            statusText.setText(R.string.vpn_connected);
            break;
        case BaseService.STOPPING:
            fab.setImageResource(R.drawable.ic_start_busy);
            if (MainActivity.this.state == BaseService.CONNECTED) fabProgressCircle.show();   // ignore for stopped
            statusText.setText(R.string.stopping);
            break;
        default:
            fab.setImageResource(R.drawable.ic_start_idle);
            fabProgressCircle.postDelayed(MainActivity.this::hideCircle, 1000);
            if (msg != null) {
                Snackbar.make(findViewById(R.id.snackbar),
                        getString(R.string.vpn_error).format(Locale.ENGLISH, msg), Snackbar.LENGTH_LONG).show();
                Log.e(TAG, "Error to start VPN service: " + msg);
            }
            statusText.setText(R.string.not_connected);
            break;
    }
    MainActivity.this.state = state;
    if (state == BaseService.CONNECTED) {
        fab.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.material_green_700));
        TooltipCompat.setTooltipText(fab, getString(R.string.stop));
    } else {
        fab.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.material_primary_500));
        TooltipCompat.setTooltipText(fab, getString(R.string.connect));
        updateTraffic(-1, 0, 0, 0, 0);
        testCount += 1;  // suppress previous test messages
    }
    ProfilesFragment.instance.profilesAdapter.notifyDataSetChanged();  // refresh button enabled state
    stateListener.invoke(state);
    fab.setEnabled(false);
    if (state == BaseService.CONNECTED || state == BaseService.STOPPED) MainActivity.this.handler.postDelayed(
            () -> fab.setEnabled(state == BaseService.CONNECTED || state == BaseService.STOPPED), 1000);
}
public void updateTraffic(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
    txText.setText(TrafficMonitor.formatTraffic(txTotal));
    rxText.setText(TrafficMonitor.formatTraffic(rxTotal));
    txRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(txRate)));
    rxRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(rxRate)));
    ToolbarFragment child = (ToolbarFragment) supportFragmentManager.findFragmentById(R.id.fragment\_holder);
    if (state != BaseService.STOPPING)
        child.onTrafficUpdated(profileId, txRate, rxRate, txTotal, rxTotal);
}

private void testConnection(int id) {
    URL url;
    HttpURLConnection conn;
    try {
        url = new URL("https", when (app.currentProfile.route) {
            Acl.CHINALIST -> "www.qualcomm.cn"
            else -> "www.google.com"
        }, "/generate\_204");
        conn = (BaseService.usingVpnMode ? url.openConnection() :
                url.openConnection(Proxy.Type.SOCKS,
                        new InetSocketAddress("127.0.0.1", DataStore.portProxy)))
                as HttpURLConnection;
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        int code = conn.getResponseCode();
        if (code == 204 || code == 200 && conn.getResponseLength() == 0L) {
            String elapsed = String.valueOf(SystemClock.elapsedRealtime() - start);
            app.handler.post(() -> {
                if (testCount == id) statusText.setText(getString(R.string.connection\_test\_available, elapsed));
            });
        } else {
            String msg = String.format(Locale.ENGLISH, getString(R.string.connection\_test\_error\_status\_code), code);
            app.handler.post(() -> {
                if (testCount == id) statusText.setText(msg);
            });
        }
    } catch (Exception e) {
        String msg = String.format(Locale.ENGLISH, getString(R.string.connection\_test\_error), e.getMessage());
        app.handler.post(() -> {
            if (testCount == id) statusText.setText(msg);
        });
    } finally {
        conn.disconnect();
    }
}

@Override
public void onServiceConnected(IShadowsocksService service) {
    changeState(service.state);
}
@Override
public void onServiceDisconnected() {
    changeState(BaseService.IDLE);
}
@Override
public void binderDied() {
    MainActivity.this.handler.post(() -> {
        connection.disconnect();
        Executable.killAll();
        connection.connect();
    });
}

@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT\_OK) app.startService();
    else {
        Snackbar.make(findViewById(R.id.snackbar), R.string.vpn\_permission\_denied, Snackbar.LENGTH\_LONG).show();
        Log.e(TAG, "Failed to start VpnService: " + data);
    }
}

@Override
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.layout\_main);
    DrawerBuilder drawerBuilder = new DrawerBuilder();
    drawerBuilder
            .withActivity(this)
            .withTranslucentStatusBar(true)
            .withHeader(R.layout.layout\_header)
            .addDrawerItems(
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER\_PROFILES)
                            .withName(R.string.profiles)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic\_action\_description))
                            .withIconTintingEnabled(true),
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER\_CUSTOM\_RULES)
                            .withName(R.string.custom\_rules)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic\_action\_assignment))
                            .withIconTintingEnabled(true),
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER\_GLOBAL\_SETTINGS)
                            .withName(R.string.settings)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic\_action\_settings))
                            .withIconTintingEnabled(true)
            )
            .addStickyDrawerItems(
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER\_FAQ)
                            .withName(R.string.faq)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic\_action\_help\_outline))
                            .withIconTintingEnabled(true)
                            .withSelectable(false),
                    new PrimaryDrawerItem()
                            .withIdentifier(DRAWER\_ABOUT)
                            .withName(R.string.about)
                            .withIcon(AppCompatResources.getDrawable(this, R.drawable.ic\_action\_copyright))
                            .withIconTintingEnabled(true)
            )
            .withOnDrawerItemClickListener(this)
            .withActionBarDrawerToggle(true)
            .withSavedInstance(savedInstanceState);
    int miniDrawerWidth = (int) getResources().getDimension(R.dimen.material\_mini\_drawer\_item);
    if (getResources().getDisplayMetrics().widthPixels >=
            getResources().getDimension(R.dimen.profile\_item\_max\_width) + miniDrawerWidth) {
        drawer = drawerBuilder.withGenerateMiniDrawer(true).buildView();
        Crossfader<CrossFadeSlidingPaneLayout> crossfader = new Crossfader<>();
        this.crossfader = crossfader;
        crossfader
                .withContent(findViewById(android.R.id.content))
                .withFirst(drawer.slider, getResources().getDimensionPixelSize(R.dimen.material\_drawer\_width))
                .withSecond(drawer.miniDrawer.build(this), miniDrawerWidth)
                .withSavedInstance(savedInstanceState)
                .build();
        if (getResources().getConfiguration().layoutDirection == View.LAYOUT\_DIRECTION\_RTL)
            crossfader.crossFadeSlidingPaneLayout.setShadowDrawableRight(
                    AppCompatResources.getDrawable(this, R.drawable.material\_drawer\_shadow\_right));
        else crossfader.crossFadeSlidingPaneLayout.setShadowDrawableLeft(
                AppCompatResources.getDrawable(this, R.drawable.material\_drawer\_shadow\_left));
        drawer.miniDrawer.withCrossFader(new ICrossfader() { // a wrapper is needed
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
            statusText.setText(R.string.connection\_test\_testing);
            int id = testCount;  // it would change by other code
            thread(() -> testConnection(id));
        }
    });

    fab = findViewById(R.id.fab);
    fabProgressCircle = findViewById(R.id.fabProgressCircle);
    fab.setOnClickListener(view -> {
        if (state == BaseService.CONNECTED) app.stopService();
        else thread(() -> {
            if (BaseService.usingVpnMode) {
                Intent intent = VpnService.prepare(this);
                if (intent != null) startActivityForResult(intent, REQUEST\_CONNECT);
                else app.handler.post(() -> onActivityResult(REQUEST\_CONNECT, Activity.RESULT\_OK, null));
            } else app.startService();
        });
    });

    changeState(BaseService.IDLE);   // reset everything to init state
    app.handler.post(() -> connection.connect());
    DataStore.registerChangeListener(this);

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
    String action = intent.getAction();
    if (Intent.ACTION\_VIEW.equals(action)) {
        sharedStr = intent.getData().toString();
    } else if (NfcAdapter.ACTION\_NDEF\_DISCOVERED.equals(action)) {
        NdefMessage[] rawMsgs = (NdefMessage[]) intent.getParcelableArrayExtra(NfcAdapter.EXTRA\_NDEF\_MESSAGES);
        if (rawMsgs != null && rawMsgs.length > 0) {
            sharedStr = new String(rawMsgs[0].getRecords()[0].getPayload());
        }
    }
    if (TextUtils.isEmpty(sharedStr)) return;
    List<Profile> profiles = Profile.findAll(sharedStr);
    if (profiles.isEmpty()) {
        Snackbar.make(findViewById(R.id.snackbar), R.string.profile\_invalid\_input, Snackbar.LENGTH\_LONG).show();
        return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder
            .setTitle(R.string.add\_profile\_dialog)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                for (Profile profile : profiles) {
                    ProfileManager.createProfile(profile);
                }
            })
            .setNegativeButton(R.string.no, null)
            .setMessage(profiles.toString());
    builder.create().show();
}

@Override
public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
    long id = drawerItem.getIdentifier();
    if (id == previousSelectedDrawer) drawer.closeDrawer();
    else {
        previousSelectedDrawer = id;
        switch ((int) id) {
            case (int) DRAWER\_PROFILES:
                displayFragment(new ProfilesFragment());
                break;
            case (int) DRAWER\_GLOBAL\_SETTINGS:
                displayFragment(new GlobalSettingsFragment());
                break;
            case (int) DRAWER\_ABOUT:
                app.track(TAG, "about");
                displayFragment(new AboutFragment());
                break;
            case (int) DRAWER\_FAQ:
                app.track(TAG, "faq");
                launchUrl(getString(R.string.faq\_url));
                break;
            case (int) DRAWER\_CUSTOM\_RULES:
                displayFragment(new CustomRulesFragment());
                break;
            default:
                return false;
        }
    }
    return true;
}

@Override
public void onPreferenceDataStoreChanged(PreferenceDataStore store, @Nullable String key) {
    if (Key.serviceMode.equals(key)) {
        Handler handler = app.getHandler();
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    connection.disconnect();
                    connection.connect();
                }
            });
        }
    }
}

private void displayFragment(ToolbarFragment fragment) {
    getSupportFragmentManager().beginTransaction().replace(R.id.fragment\_holder, fragment).commitAllowingStateLoss();
    drawer.closeDrawer();
}

@Override
public void onResume() {
    super.onResume();
    app.remoteConfig.fetch();
    if (state != BaseService.STOPPING && state != BaseService.CONNECTING) hideCircle();
}

@Override
public void onStart() {
    super.onStart();
    connection.listeningForBandwidth = true;
}

@Override
public void onBackPressed() {
    if (drawer.isDrawerOpen()) drawer.closeDrawer();
    else {
        ToolbarFragment currentFragment = (ToolbarFragment) supportFragmentManager.findFragmentById(R.id.fragment\_holder);
        if (!currentFragment.onBackPressed())
            if (currentFragment instanceof ProfilesFragment) super.onBackPressed();
            else drawer.setSelection(DRAWER\_PROFILES);
    }
}

@Override
public void onStop() {
    connection.listeningForBandwidth = false;
    super.onStop();
}

@Override
public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    drawer.saveInstanceState(outState);
    crossfader.saveInstanceState(outState);
}
KeyValuePair 
@Override
public void onDestroy() {
    super.onDestroy();
    DataStore.unregisterChangeListener(this);
    connection.disconnect();
    BackupManager backupManager = new BackupManager(this);
    backupManager.dataChanged();
    app.handler.removeCallbacksAndMessages(null);
}
}