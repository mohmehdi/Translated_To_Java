

package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.shared.di.ActivityScoped;
import com.google.samples.apps.iosched.ui.LaunchModule;
import com.google.samples.apps.iosched.ui.LauncherActivity;
import com.google.samples.apps.iosched.ui.MainActivity;
import com.google.samples.apps.iosched.ui.MapActivity;
import com.google.samples.apps.iosched.ui.MapModule;
import com.google.samples.apps.iosched.ui.OnboardingActivity;
import com.google.samples.apps.iosched.ui.OnboardingModule;
import com.google.samples.apps.iosched.ui.InfoModule;
import com.google.samples.apps.iosched.ui.SignInDialogModule;
import com.google.samples.apps.iosched.ui.ReservationModule;
import com.google.samples.apps.iosched.ui.schedule.ScheduleModule;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActionsViewModelDelegateModule;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailModule;
import com.google.samples.apps.iosched.ui.speaker.SpeakerActivity;
import com.google.samples.apps.iosched.ui.speaker.SpeakerModule;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class ActivityBindingModule {

    @ActivityScoped
    @ContributesAndroidInjector(modules = {LaunchModule.class})
    public abstract LauncherActivity launcherActivity();

    @ActivityScoped
    @ContributesAndroidInjector(modules = {OnboardingModule.class})
    public abstract OnboardingActivity onboardingActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    ScheduleModule.class,
                    MapModule.class,
                    InfoModule.class,
                    SignInDialogModule.class,
                    ReservationModule.class,
                    PreferenceModule.class
            })
    public abstract MainActivity mainActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    SessionDetailModule.class,
                    SignInDialogModule.class,
                    ReservationModule.class,
                    PreferenceModule.class
            })
    public abstract SessionDetailActivity sessionDetailActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    SpeakerModule.class,
                    SignInDialogModule.class,
                    EventActionsViewModelDelegateModule.class,
                    PreferenceModule.class
            })
    public abstract SpeakerActivity speakerActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    MapModule.class,
                    PreferenceModule.class
            })
    public abstract MapActivity mapActivity();
}