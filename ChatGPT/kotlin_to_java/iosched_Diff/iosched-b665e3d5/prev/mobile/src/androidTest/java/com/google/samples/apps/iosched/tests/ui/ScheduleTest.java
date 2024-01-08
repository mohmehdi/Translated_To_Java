package com.google.samples.apps.iosched.tests.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.data.FakeConferenceDataSource;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.tests.FixedTimeRule;
import com.google.samples.apps.iosched.tests.SetPreferencesRule;
import com.google.samples.apps.iosched.tests.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.ui.schedule.day.SessionViewHolder;
import com.google.samples.apps.iosched.ui.schedule.filters.ScheduleFilterAdapter;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior;

import org.hamcrest.CoreMatchers;
import org.hamcrest.core.AllOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class ScheduleTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Rule
    public FixedTimeRule timeProviderRule = new FixedTimeRule();

    @Rule
    public SetPreferencesRule preferencesRule = new SetPreferencesRule();

    private Context context;
    private Resources resources;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        resources = context.getResources();
        disableBottomSheetAnimations();
    }

    @Test
    public void clickOnAgenda_showsAgenda() {
        onView(withText(R.string.agenda)).perform(click());
        onView(withText("Breakfast")).check(matches(isDisplayed()));
    }

    @Test
    public void allDays_areClicked_showsSessions() {
        for (TimeUtils.ConferenceDays conferenceDay : TimeUtils.ConferenceDays.values()) {
            String dayTitle = conferenceDay.formatMonthDay();
            onView(withText(dayTitle)).perform(click());
            onView(withText("First session day " + (conferenceDay.ordinal() + 1)))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void clickOnFirstItem_detailsShow() {
        onView(AllOf.allOf(withId(R.id.recyclerview), hasFocus()))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));

        onView(
                AllOf.allOf(
                        withId(R.id.session_detail_with_video_title),
                        withText("First session day 1")
                )
        )
                .check(matches(isDisplayed()));
    }

    @Test
    public void clickFilters_showFilters() {
        checkAnimationsDisabled();

        onView(withId(R.id.filter_fab)).perform(click());

        String uncheckedFilterContentDesc =
                getDisabledFilterContDesc(FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);
        String checkedFilterContentDesc =
                getActiveFilterContDesc(FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);

        onView(AllOf.allOf(withId(R.id.recyclerview), withParent(withId(R.id.filter_sheet))))
                .perform(
                        RecyclerViewActions.scrollToPosition(
                                ScheduleFilterAdapter.FilterViewHolder.class,
                                hasDescendant(withContentDescription(uncheckedFilterContentDesc))
                        )
                );

        onView(withContentDescription(uncheckedFilterContentDesc))
                .check(matches(isDisplayed()))
                .perform(click());

        onView(
                AllOf.allOf(
                        withId(R.id.filter_label),
                        withContentDescription(checkedFilterContentDesc),
                        CoreMatchers.not(withParent(withId(R.id.filter_description_tags)))
                )
        )
                .check(matches(isDisplayed()))
                .perform(click());
    }

    @Test
    public void filters_applyAFilter() {
        checkAnimationsDisabled();
        String sessionName = FakeConferenceDataSource.FAKE_SESSION_NAME;

        applyFilter(FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);

        onView(withText(sessionName))
                .check(matches(isDisplayed()));
    }

    @Test
    public void filters_clearFilters() {
        String filter = FakeConferenceDataSource.FAKE_SESSION_TAG_NAME;
        applyFilter(filter);

        onView(withId(R.id.clear_filters_shortcut)).perform(click());

        onView(
                AllOf.allOf(
                        withId(R.id.filter_label),
                        withContentDescription(getActiveFilterContDesc(filter)),
                        withParent(withId(R.id.filter_description_tags))
                )
        ).check(matches(CoreMatchers.not(isCompletelyDisplayed())));
    }

    private void applyFilter(String filter) {
        onView(withId(R.id.filter_fab)).perform(click());

        String uncheckedFilterContentDesc =
                resources.getString(R.string.a11y_filter_not_applied, filter);

        onView(AllOf.allOf(withId(R.id.recyclerview), withParent(withId(R.id.filter_sheet)))))
                .check(matches(isDisplayed()));

        onView(AllOf.allOf(withId(R.id.recyclerview), withParent(withId(R.id.filter_sheet)))))
                .perform(
                        RecyclerViewActions.scrollToPosition(
                                ScheduleFilterAdapter.FilterViewHolder.class,
                                hasDescendant(withContentDescription(uncheckedFilterContentDesc))
                        )
                );

        onView(withContentDescription(uncheckedFilterContentDesc))
                .check(matches(isDisplayed()))
                .perform(click());

        pressBack();
    }

    private String getDisabledFilterContDesc(String filter) {
        return resources.getString(R.string.a11y_filter_not_applied, filter);
    }

    private String getActiveFilterContDesc(String filter) {
        return resources.getString(R.string.a11y_filter_applied, filter);
    }

    private void checkAnimationsDisabled() {
        float scale = Settings.Global.getFloat(
                context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
        );

        if (scale > 0) {
            throw new Exception(
                    "Device must have animations disabled. " +
                            "Developer options -> Animator duration scale"
            );
        }
    }
}