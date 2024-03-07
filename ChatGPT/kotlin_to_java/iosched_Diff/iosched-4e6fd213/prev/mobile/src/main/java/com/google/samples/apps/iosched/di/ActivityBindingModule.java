package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.shared.di.ActivityScoped;
import com.google.samples.apps.iosched.ui.LaunchModule;
import com.google.samples.apps.iosched.ui.LauncherActivity;
import com.google.samples.apps.iosched.ui.MainActivity;
import com.google.samples.apps.iosched.ui.info.InfoModule;
import com.google.samples.apps.iosched.ui.map.MapActivity;
import com.google.samples.apps.iosched.ui.map.MapModule;
import com.google.samples.apps.iosched.ui.onboarding.OnboardingActivity;
import com.google.samples.apps.iosched.ui.onboarding.OnboardingModule;
import com.google.samples.apps.iosched.ui.prefs.PreferenceModule;
import com.google.samples.apps.iosched.ui.reservation.ReservationModule;
import com.google.samples.apps.iosched.ui.schedule.ScheduleModule;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActionsViewModelDelegateModule;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailModule;
import com.google.samples.apps.iosched.ui.signin.SignInDialogModule;
import com.google.samples.apps.iosched.ui.speaker.SpeakerActivity;
import com.google.samples.apps.iosched.ui.speaker.SpeakerModule;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class ActivityBindingModule {

    @ActivityScoped
    @ContributesAndroidInjector(modules = {LaunchModule.class})
    protected abstract LauncherActivity launcherActivity();

    @ActivityScoped
    @ContributesAndroidInjector(modules = {OnboardingModule.class})
    protected abstract OnboardingActivity onboardingActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    ScheduleModule.class,
                    MapModule.class,
                    InfoModule.class,
                    SignInDialogModule.class,
                    ReservationModule.class,
                    PreferenceModule.class
            }
    )
    protected abstract MainActivity mainActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    SessionDetailModule.class,
                    SignInDialogModule.class,
                    ReservationModule.class,
                    PreferenceModule.class
            }
    )
    protected abstract SessionDetailActivity sessionDetailActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    SpeakerModule.class,
                    SignInDialogModule.class,
                    EventActionsViewModelDelegateModule.class,
                    PreferenceModule.class
            }
    )
    protected abstract SpeakerActivity speakerActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    MapModule.class,
                    PreferenceModule.class
            }
    )
    protected abstract MapActivity mapActivity();
}