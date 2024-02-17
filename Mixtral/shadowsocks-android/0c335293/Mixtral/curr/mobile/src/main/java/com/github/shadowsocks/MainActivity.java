

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
import android.support.v7.preference.PreferenceDataStoreCompat;
import android.support.v7.preference.PreferenceDataStoreChangeListener;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.github.shadowsocks.App;
import com.github.shadowsocks.App.Companion;
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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ShadowsocksConnection.Interface, Drawer.OnDrawerItemClickListener,
        OnPreferenceDataStoreChangeListener {
    private static final String TAG = "ShadowsocksMainActivity";
    private static final int REQUEST_CONNECT = 1;

    private static final long DRAWER_PROFILES = 0L;
    private static final long DRAWER_GLOBAL_SETTINGS = 1L;
    private static final long DRAWER_ABOUT = 3L;
    private static final long DRAWER_FAQ = 4L;
    private static final long DRAWER_CUSTOM_RULES = 5L;

    public static OnShadowsocksStateChangeListener stateListener;

    private ServiceButton fab;
    private Crossfader<CrossFadeSlidingPaneLayout> crossfader;
    private Drawer drawer;
    private long previousSelectedDrawer;

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
        } catch (ActivityNotFoundException e) {
            // ignore
        }
    }

    public void launchUrl(String uri) {
        this.launchUrl(Uri.parse(uri));
    }
    
    private int state = BaseService.IDLE;
    private IShadowsocksServiceCallback serviceCallback = new IShadowsocksServiceCallback.Stub() {
        @Override
        public void stateChanged(int state, String profileName, String msg) {
            App.getApp().handler.post(() -> changeState(state, msg, true));
        }

        @Override
        public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
            App.getApp().handler.post(() -> updateTraffic(profileId, txRate, rxRate, txTotal, rxTotal));
        }

        @Override
        public void trafficPersisted(int profileId) {
            App.getApp().handler.post(() -> ProfilesFragment.instance.onTrafficPersisted(profileId));
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
                        Snackbar.make(findViewById(R.id.snackbar),
                                getString(R.string.vpn_error).format(Locale.ENGLISH, msg), Snackbar.LENGTH_LONG).show();
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
            Fragment ProfilesFragmentInstance = ProfilesFragment.instance;
            if (ProfilesFragmentInstance != null) {
                ProfilesFragmentInstance.profilesAdapter.notifyDataSetChanged();  // refresh button enabled state
            }
            if (stateListener != null) {
                stateListener.invoke(state);
            }
        }

    
    public void updateTraffic(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
        txText.setText(TrafficMonitor.formatTraffic(txTotal));
        rxText.setText(TrafficMonitor.formatTraffic(rxTotal));
        txRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(txRate)));
        rxRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(rxRate)));
        Fragment child = supportFragmentManager.findFragmentById(R.id.fragment_holder);
        if (state != BaseService.STOPPING && child != null && child instanceof ToolbarFragment) {
            ToolbarFragment toolbarFragment = (ToolbarFragment) child;
            toolbarFragment.onTrafficUpdated(profileId, txRate, rxRate, txTotal, rxTotal);
        }
    }

        private void testConnection(int id) {
        String currentProfileRoute = "www.qualcomm.cn"; // replace with actual value of app.currentProfile!!.route
        String urlString = "https://" + currentProfileRoute + "/generate_204";
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            // handle exception
            return;
        }

        URLConnection conn;
        if (BaseService.usingVpnMode) {
            conn = url.openConnection();
        } else {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", DataStore.portProxy));
            conn = url.openConnection(proxy);
        }

        HttpURLConnection httpConn = (HttpURLConnection) conn;
        httpConn.setInstanceFollowRedirects(false);
        httpConn.setConnectTimeout(TIMEOUT);
        httpConn.setReadTimeout(TIMEOUT);
        httpConn.setUseCaches(false);

        boolean success = false;
        String result = "";
        long elapsed = 0;
        try {
            long start = System.currentTimeMillis();
            int code = httpConn.getResponseCode();
            elapsed = System.currentTimeMillis() - start;
            if (code == 204 || (code == 200 && httpConn.getContentLengthLong() == 0)) {
                success = true;
                result = getString(R.string.connection_test_available, elapsed);
            } else {
                throw new Exception(getString(R.string.connection_test_error_status_code, code));
            }
        } catch (Exception e) {
            result = getString(R.string.connection_test_error, e.getMessage());
        } finally {
            httpConn.disconnect();
        }

        if (testCount == id) {
            // post to handler
            if (success) {
                statusText.setText(result);
            } else {
                statusText.setText(R.string.connection_test_fail);
                Snackbar.make(findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show();
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
    Handler appHandler = app.getHandler();
    if (appHandler != null) {
        appHandler.post(new Runnable() {
            @Override
            public void run() {
                connection.disconnect();
                Executable.killAll();
                connection.connect();
            }
        });
    }
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
        app.startService();
    } else {
        Snackbar.make(findViewById(R.id.snackbar), R.string.vpn_permission_denied, Snackbar.LENGTH_LONG).show();
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

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            if (state == BaseService.CONNECTED) {
                App.getApp().stopService();
            } else {
                thread(() -> {
                    if (BaseService.usingVpnMode) {
                        Intent intent = VpnService.prepare(this);
                        if (intent != null) {
                            startActivityForResult(intent, REQUEST_CONNECT);
                        } else {
                            onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null);
                        }
                    } else {
                        App.getApp().startService();
                    }
                });
            }
        });

        statusText = findViewById(R.id.status);
        txText = findViewById(R.id.tx);
        txRateText = findViewById(R.id.txRate);
        rxText = findViewById(R.id.rx);
        rxRateText = findViewById(R.id.rxRate);

        changeState(BaseService.IDLE);
        App.getApp().handler.post(() -> connection.connect());
        DataStore.publicStore.registerChangeListener(this);

        Intent intent = this.intent;
        if (intent != null) {
            handleShareIntent(intent);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        String sharedStr = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            sharedStr = intent.getData().toString();
        } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            NdefMessage[] rawMsgs = (NdefMessage[]) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null && rawMsgs.length > 0) {
                sharedStr = new String(rawMsgs[0].getRecords()[0].getPayload());
            }
        }
        if (sharedStr == null) {
            return;
        }
        List<Profile> profiles = Arrays.asList(Profile.findAll(sharedStr));
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
        builder.setMessage(String.join("\n", profiles));
        builder.create().show();
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (Key.serviceMode.equals(key)) {
            App.getApp().handler.post(() -> {
                connection.disconnect();
                connection.connect();
            });
        }
    }

    private void displayFragment(ToolbarFragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_holder, fragment).commitAllowingStateLoss();
        drawer.closeDrawer();
    }

    @Override
    public boolean onItemClick(View view, int position, IDrawerItem<Object, Object> drawerItem) {
        long id = drawerItem.getIdentifier();
        if (id == previousSelectedDrawer) {
            drawer.closeDrawer();
        } else {
            previousSelectedDrawer = id;
            switch ((int) id) {
                case (int) DRAWER_PROFILES:
                    displayFragment(new ProfilesFragment());
                    break;
                case (int) DRAWER_GLOBAL_SETTINGS:
                    displayFragment(new GlobalSettingsFragment());
                    break;
                case (int) DRAWER_ABOUT:
                    App.getApp().track(TAG, "about");
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
    public void onResume() {
        super.onResume();
        App.getApp().remoteConfig.fetch();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connection.listeningForBandwidth = true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            ToolbarFragment currentFragment = (ToolbarFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
            if (!currentFragment.onBackPressed()) {
                if (currentFragment instanceof ProfilesFragment) {
                    super.onBackPressed();
                } else {
                    drawer.setSelection(DRAWER_PROFILES);
                }
            }
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
        if (crossfader != null) {
            crossfader.saveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DataStore.publicStore.unregisterChangeListener(this);
        connection.disconnect();
        BackupManager backupManager = new BackupManager(this);
        backupManager.dataChanged();
        App.getApp().handler.removeCallbacksAndMessages(null);
    }

    // Other methods are the same as the Kotlin code
}