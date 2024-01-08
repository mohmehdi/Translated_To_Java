package com.google.samples.apps.iosched.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.material.navigation.NavigationView;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.NavigationHeaderBinding;
import com.google.samples.apps.iosched.shared.result.EventObserver;
import com.google.samples.apps.iosched.shared.util.inTransaction;
import com.google.samples.apps.iosched.shared.util.viewModelProvider;
import com.google.samples.apps.iosched.ui.info.InfoFragment;
import com.google.samples.apps.iosched.ui.map.MapFragment;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.ScheduleFragment;
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignOutDialogFragment;
import com.google.samples.apps.iosched.util.signin.FirebaseAuthErrorCodeConverter;
import com.google.samples.apps.iosched.util.updateForTheme;
import dagger.android.support.DaggerAppCompatActivity;
import timber.log.Timber;
import java.util.UUID;
import javax.inject.Inject;

public class MainActivity extends DaggerAppCompatActivity implements DrawerListener {

    private static final String EXTRA_NAVIGATION_ID = "extra.NAVIGATION_ID";

    private static final int FRAGMENT_ID = R.id.fragment_container;
    private static final int NAV_ID_NONE = -1;

    private static final String DIALOG_SIGN_IN = "dialog_sign_in";
    private static final String DIALOG_SIGN_OUT = "dialog_sign_out";

    @Inject
    SnackbarMessageManager snackbarMessageManager;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private MainNavigationFragment currentFragment;

    private MainActivityViewModel viewModel;

    private NavigationHeaderBinding navHeaderBinding;

    private DrawerLayout drawer;
    private NavigationView navigation;

    private int currentNavId = NAV_ID_NONE;
    private int pendingNavId = NAV_ID_NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = viewModelProvider(viewModelFactory);

        updateForTheme(viewModel.getCurrentTheme());

        setContentView(R.layout.activity_main);
        drawer = findViewById(R.id.drawer);
        navigation = findViewById(R.id.navigation);

        drawer.addDrawerListener(this);
        navigation.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                closeDrawer();
                navigateWhenDrawerClosed(item.getItemId());
                return true;
            }
        });

        navHeaderBinding = NavigationHeaderBinding.inflate(getLayoutInflater());
        navHeaderBinding.setViewModel(viewModel);
        navHeaderBinding.setLifecycleOwner(this);
        navigation.addHeaderView(navHeaderBinding.getRoot());

        if (savedInstanceState == null) {

            int initialNavId = getIntent().getIntExtra(EXTRA_NAVIGATION_ID, R.id.navigation_schedule);
            navigation.setCheckedItem(initialNavId);
            navigateTo(initialNavId);
        } else {

            currentFragment =
                    (MainNavigationFragment) getSupportFragmentManager().findFragmentById(FRAGMENT_ID);
            if (currentFragment == null) {
                throw new IllegalStateException("Activity recreated, but no fragment found!");
            }
        }

        viewModel.getTheme().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer theme) {
                updateForTheme(theme);
            }
        });

        viewModel.getNavigateToSignInDialogAction().observe(this, new EventObserver<Void>() {
            @Override
            public void onEvent(Void v) {
                openSignInDialog();
            }
        });

        viewModel.getNavigateToSignOutDialogAction().observe(this, new EventObserver<Void>() {
            @Override
            public void onEvent(Void v) {
                openSignOutDialog();
            }
        });
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentNavId = navigation.getCheckedItem() != null ? navigation.getCheckedItem().getItemId() : NAV_ID_NONE;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_CANCELED) {
            Timber.d("An activity returned RESULT_CANCELED");
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (response != null && response.getError() != null) {
                snackbarMessageManager.addMessage(
                        new SnackbarMessage(
                                FirebaseAuthErrorCodeConverter.convert(response.getError().getErrorCode()),
                                UUID.randomUUID().toString()
                        )
                );
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(navigation)) {
            closeDrawer();
        } else if (!currentFragment.onBackPressed()) {
            super.onBackPressed();
        }
    }

    private void closeDrawer() {
        drawer.closeDrawer(GravityCompat.START);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        currentFragment.onUserInteraction();
    }

    private void navigateWhenDrawerClosed(int navId) {
        if (drawer.isDrawerVisible(navigation)) {


            pendingNavId = navId;
        } else {
            navigateTo(navId);
        }
    }

    private void navigateTo(int navId) {
        if (navId == currentNavId) {
            return;
        }
        switch (navId) {
            case R.id.navigation_schedule:
                replaceFragment(new ScheduleFragment());
                break;
            case R.id.navigation_map:
                replaceFragment(new MapFragment());
                break;
            case R.id.navigation_info:
                replaceFragment(new InfoFragment());
                break;
            default:
                return;
        }
        currentNavId = navId;
    }

    private <F extends Fragment & MainNavigationFragment> void replaceFragment(F fragment) {
        getSupportFragmentManager().inTransaction(() -> {
            currentFragment = fragment;
            getSupportFragmentManager().beginTransaction().replace(FRAGMENT_ID, fragment).commit();
        });
    }

    private void openSignInDialog() {
        new SignInDialogFragment().show(getSupportFragmentManager(), DIALOG_SIGN_IN);
    }

    private void openSignOutDialog() {
        new SignOutDialogFragment().show(getSupportFragmentManager(), DIALOG_SIGN_OUT);
    }



    @Override
    public void onDrawerClosed(View drawerView) {
        if (drawerView == navigation && pendingNavId != NAV_ID_NONE) {
            navigateTo(pendingNavId);
            pendingNavId = NAV_ID_NONE;
        }
    }

    @Override
    public void onDrawerOpened(View drawerView) {}

    @Override
    public void onDrawerStateChanged(int newState) {}

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {}
}