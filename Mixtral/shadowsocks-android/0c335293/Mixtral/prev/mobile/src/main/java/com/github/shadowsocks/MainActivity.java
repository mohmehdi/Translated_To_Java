

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
import android.support.v7.preference.DataStore;
import android.support.v7.preference.PreferenceDataStore;
import android.support.v7.preference.PreferenceDataStore.OnPreferenceDataStoreChangeListener;
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

    private static long DRAWER_PROFILES = 0L;
    private static long DRAWER_GLOBAL_SETTINGS = 1L;
    private static long DRAWER_ABOUT = 3L;
    private static long DRAWER_FAQ = 4L;
    private static long DRAWER_CUSTOM_RULES = 5L;

    private FloatingActionButton fab;
    private FABProgressCircle fabProgressCircle;
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

    private int greyTint;
    private int greenTint;

    private void hideCircle() {
        try {
            fabProgressCircle.hide();
        } catch (NullPointerException e) {
            // ignore
        }
    }
    private int state = BaseService.IDLE;
    private IShadowsocksServiceCallback serviceCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        customTabsIntent = new CustomTabsIntent.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.material_primary_500))
                .build();

        greyTint = ContextCompat.getColorStateList(this, R.color.material_primary_500).getDefaultColor();
        greenTint = ContextCompat.getColorStateList(this, R.color.material_green_700).getDefaultColor();

        fab = findViewById(R.id.fab);
        fabProgressCircle = findViewById(R.id.fabProgressCircle);

        serviceCallback = new IShadowsocksServiceCallback.Stub() {
            @Override
            public void stateChanged(int state, String profileName, String msg) {
                changeState(state, msg);
            }

            @Override
            public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
                updateTraffic(profileId, txRate, rxRate, txTotal, rxTotal);
            }

            @Override
            public void trafficPersisted(int profileId) {
                ProfilesFragment.instance.onTrafficPersisted(profileId);
            }
        };

        changeState(BaseService.IDLE);

        statusText = findViewById(R.id.status);
        txText = findViewById(R.id.tx);
        txRateText = findViewById(R.id.txRate);
        rxText = findViewById(R.id.rx);
        rxRateText = findViewById(R.id.rxRate);

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
                .withOnDrawerItemClickListener(MainActivity.this)
                .withActionBarDrawerToggle(true)
                .withSavedInstance(savedInstanceState);

        int miniDrawerWidth = (int) getResources().getDimension(R.dimen.material_mini_drawer_item);
        if (getResources().getDisplayMetrics().widthPixels >=
                getResources().getDimension(R.dimen.profile_item_max_width) + miniDrawerWidth) {
            drawer = drawerBuilder.withGenerateMiniDrawer(true).buildView();
            Crossfader<CrossFadeSlidingPaneLayout> crossfader = new Crossfader<>();
            crossfader.withContent(findViewById(android.R.id.content))
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

        findViewById<View>(R.id.stat).setOnClickListener(new View.OnClickListener() {

        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state == BaseService.CONNECTED) {
                    app.stopService();
                } else {
                    thread.run(new Runnable() {
                        @Override
                        public void run() {
                            if (BaseService.usingVpnMode) {
                                Intent intent = VpnService.prepare(MainActivity.this);
                                if (intent != null) {
                                    startActivityForResult(intent, REQUEST_CONNECT);
                                } else {
                                    onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null);
                                }
                            } else {
                                app.startService();
                            }
                        }
                    });
                }
            }
        });

        changeState(BaseService.IDLE);
        app.handler.post(new Runnable() {
            @Override
            public void run() {
                connection.connect();
            }
        });
        DataStore.registerChangeListener(this);

        Intent intent = getIntent();
        if (intent != null) handleShareIntent(intent);
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            app.startService();
        } else {
            Snackbar.make(findViewById(R.id.snackbar), R.string.vpn_permission_denied, Snackbar.LENGTH_LONG).show();
            Log.e(TAG, "Failed to start VpnService: " + data);
        }
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        if (key.equals(Key.serviceMode)) {
            app.handler.post(new Runnable() {
                @Override
                public void run() {
                    connection.disconnect();
                    connection.connect();
                }
            });
        }
    }

  

    @Override
    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
        Object id = drawerItem.getIdentifier();
        if (id == previousSelectedDrawer) {
            drawer.closeDrawer();
        } else {
            previousSelectedDrawer = id;
            switch ((String) id) {
                case DRAWER_PROFILES:
                    displayFragment(new ProfilesFragment());
                    break;
                case DRAWER_GLOBAL_SETTINGS:
                    displayFragment(new GlobalSettingsFragment());
                    break;
                case DRAWER_ABOUT:
                    app.track(TAG, "about");
                    displayFragment(new AboutFragment());
                    break;
                case DRAWER_FAQ:
                    launchUrl(getString(R.string.faq_url));
                    break;
                case DRAWER_CUSTOM_RULES:
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
        app.remoteConfig.fetch();
        if (state != BaseService.STOPPING && state != BaseService.CONNECTING) {
            hideCircle();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        connection.listeningForBandwidth = true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            ToolbarFragment currentFragment = (ToolbarFragment) supportFragmentManager.findFragmentById(R.id.fragment_holder);
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
    public void onStop() {
        super.onStop();
        connection.listeningForBandwidth = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        drawer.saveInstanceState(outState);
        if (crossfader != null) {
            crossfader.saveInstanceState(outState);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DataStore.unregisterChangeListener(this);
        connection.disconnect();
        BackupManager backupManager = new BackupManager(this);
        backupManager.dataChanged();
        app.handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }
    private void handleShareIntent(Intent intent) {
        String sharedStr = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            sharedStr = intent.getDataString();
        } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            NdefMessage[] rawMsgs = (NdefMessage[]) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null && rawMsgs.length > 0) {
                sharedStr = new String(rawMsgs[0].getRecords()[0].getPayload());
            }
        }
        if (TextUtils.isEmpty(sharedStr)) {
            return;
        }
        List<Profile> profiles = Arrays.asList(Profile.findAll(sharedStr));
        if (profiles.isEmpty()) {
            Snackbar.make(findViewById(R.id.snackbar), R.string.profile_invalid_input, Snackbar.LENGTH_LONG).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_profile_dialog)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Profile profile : profiles) {
                            ProfileManager.createProfile(profile);
                        }
                    }
                })
                .setNegativeButton(R.string.no, null)
                .setMessage(TextUtils.join("\n", profiles));
        builder.create().show();
    }

    private void displayFragment(ToolbarFragment fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, fragment).commitAllowingStateLoss();
        drawer.closeDrawer();
    }

    private void changeState(int state, String msg) {
        this.state = state;
        switch (state) {
            case BaseService.CONNECTING:
                fab.setImageResource(R.drawable.ic_start_busy);
                fabProgressCircle.show();
                statusText.setText(R.string.connecting);
                break;
            case BaseService.CONNECTED:
                if (this.state == BaseService.CONNECTING) {
                    fabProgressCircle.beginFinalAnimation();
                } else {
                    fabProgressCircle.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hideCircle();
                        }
                    }, 1000);
                }
                fab.setImageResource(R.drawable.ic_start_connected);
                statusText.setText(R.string.vpn_connected);
                fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.material_green_700)));
                TooltipCompat.setTooltipText(fab, getString(R.string.stop));
                break;
            case BaseService.STOPPING:
                fab.setImageResource(R.drawable.ic_start_busy);
                if (this.state == BaseService.CONNECTED) {
                    fabProgressCircle.show();
                }
                statusText.setText(R.string.stopping);
                break;
            case BaseService.IDLE:
            case BaseService.ERROR:
            case BaseService.DISCONNECTED:
                fab.setImageResource(R.drawable.ic_start_idle);
                fabProgressCircle.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        hideCircle();
                    }
                }, 1000);
                if (msg != null) {
                    Snackbar.make(findViewById(R.id.snackbar),
                            getString(R.string.vpn_error).format(Locale.ENGLISH, msg), Snackbar.LENGTH_LONG).show();
                    Log.e(TAG, "Error to start VPN service: " + msg);
                }
                statusText.setText(R.string.not_connected);
                fab.setBackgroundTintList(ColorStateList.valueOf(greyTint));
                TooltipCompat.setTooltipText(fab, getString(R.string.connect));
                break;
        }
        ProfilesFragment.instance.profilesAdapter.notifyDataSetChanged();
        if (state == BaseService.CONNECTED || state == BaseService.STOPPED) {
            fab.setEnabled(true);
        } else {
            fab.setEnabled(false);
        }
    }

    private void updateTraffic(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
        txText.setText(TrafficMonitor.formatTraffic(txTotal));
        rxText.setText(TrafficMonitor.formatTraffic(rxTotal));
        txRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(txRate)));
        rxRateText.setText(getString(R.string.speed, TrafficMonitor.formatTraffic(rxRate)));
        ToolbarFragment child = (ToolbarFragment) supportFragmentManager.findFragmentById(R.id.fragment_holder);
        if (state != BaseService.STOPPING) {
            child.onTrafficUpdated(profileId, txRate, rxRate, txTotal, rxTotal);
        }
    }

    private void testConnection(int id) {
        String urlStr = "https://";
        String host = null;
        if (app.currentProfile.route == Acl.CHINALIST) {
            host = "www.qualcomm.cn";
        } else {
            host = "www.google.com";
        }
        urlStr += host + "/generate_204";
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        HttpURLConnection conn = null;
        if (BaseService.usingVpnMode) {
            try {
                conn = (HttpURLConnection) url.openConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", DataStore.portProxy));
            try {
                conn = (HttpURLConnection) url.openConnection(proxy);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        try {
            conn.connect();
            int code = conn.getResponseCode();
            if (code == 204 || (code == 200 && conn.getResponseLength() == 0)) {
                String result = getString(R.string.connection_test_available, SystemClock.elapsedRealtime() - SystemClock.elapsedRealtime());
                if (testCount == id) {
                    app.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(result);
                        }
                    });
                }
            } else {
                String result = getString(R.string.connection_test_error_status_code, code);
                if (testCount == id) {
                    app.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(result);
                        }
                    });
                }
            }
        } catch (IOException e) {
            String result = getString(R.string.connection_test_error, e.getMessage());
            if (testCount == id) {
                app.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(result);
                    }
                });
            }
        } finally {
            conn.disconnect();
        }
    }

    private void launchUrl(String uri) {
        try {
            customTabsIntent.launchUrl(this, Uri.parse(uri));
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void launchUrl(Uri uri) {
        try {
            customTabsIntent.launchUrl(this, uri);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }
}