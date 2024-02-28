

package com.google.samples.apps.iosched.tests.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.Espresso.onView;
import androidx.test.espresso.Espresso.pressBack;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.data.FakeConferenceDataSource;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDays;
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

@RunWith(AndroidJUnit4.class)
public class ScheduleTest {

    @Rule
    public MainActivityTestRule<MainActivity> activityRule = new MainActivityTestRule<>(R.id.navigation_schedule);

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Rule
    public FixedTimeRule timeProviderRule = new FixedTimeRule();

    @Rule
    public SetPreferencesRule preferencesRule = new SetPreferencesRule();

    private final Context resources = ApplicationProvider.getApplicationContext();

    @Before
    public void disableBottomSheetAnimations() {
        BottomSheetBehavior behavior = BottomSheetBehavior.from(activityRule.getActivity().findViewById(R.id.filter_sheet));
        behavior.setAnimationDisabled(true);
    }

    @Test
    public void clickOnAgenda_showsAgenda() {
        onView(ViewMatchers.withText(R.string.agenda)).perform(ViewActions.click());
        onView(ViewMatchers.withText("Breakfast")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void allDays_areClicked_showsSessions() {
        for (int i = 0; i < ConferenceDays.values().length; i++) {
            ConferenceDays conferenceDay = ConferenceDays.values()[i];
            String dayTitle = conferenceDay.formatMonthDay();
            onView(ViewMatchers.withText(dayTitle)).perform(ViewActions.click());
            onView(ViewMatchers.withText(String.format("First session day %d", i + 1)))
                    .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        }
    }

    @Test
    public void clickOnFirstItem_detailsShow() {
        onView(AllOf.allOf(ViewMatchers.withId(R.id.recyclerview), ViewMatchers.hasFocus()))
                .perform(RecyclerViewActions.actionOnItemAtPosition<SessionViewHolder>(0, ViewActions.click()));

        onView(
                AllOf.allOf(
                        ViewMatchers.withId(R.id.session_detail_with_video_title),
                        ViewMatchers.withText("First session day 1")
                )
        )
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void clickFilters_showFilters() {
        checkAnimationsDisabled();

        onView(ViewMatchers.withId(R.id.filter_fab)).perform(ViewActions.click());

        String uncheckedFilterContentDesc =
                resources.getString(R.string.a11y_filter_not_applied, FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);
        String checkedFilterContentDesc =
                resources.getString(R.string.a11y_filter_applied, FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);

        onView(AllOf.allOf(ViewMatchers.withId(R.id.recyclerview), ViewMatchers.withParent(ViewMatchers.withId(R.id.filter_sheet))))
                .perform(
                        RecyclerViewActions.scrollTo<ScheduleFilterAdapter.FilterViewHolder>(
                                hasDescendant(ViewMatchers.withContentDescription(uncheckedFilterContentDesc))
                        )
                );

        onView(ViewMatchers.withContentDescription(uncheckedFilterContentDesc))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .perform(ViewActions.click());

        onView(
                AllOf.allOf(
                        ViewMatchers.withId(R.id.filter_label),
                        ViewMatchers.withContentDescription(checkedFilterContentDesc),
                        not(ViewMatchers.withParent(ViewMatchers.withId(R.id.filter_description_tags)))
                )
        )
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .perform(ViewActions.click());
    }

    @Test
    public void filters_applyAFilter() {
        checkAnimationsDisabled();
        String sessionName = FakeConferenceDataSource.FAKE_SESSION_NAME;

        applyFilter(FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);

        onView(ViewMatchers.withText(sessionName))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void filters_clearFilters() {
        String filter = FakeConferenceDataSource.FAKE_SESSION_TAG_NAME;
        applyFilter(filter);

        onView(ViewMatchers.withId(R.id.clear_filters_shortcut)).perform(ViewActions.click());

        onView(
                AllOf.allOf(
                        ViewMatchers.withId(R.id.filter_label),
                        ViewMatchers.withContentDescription(getActiveFilterContDesc(filter)),
                        ViewMatchers.withParent(ViewMatchers.withId(R.id.filter_description_tags))
                )
        ).check(ViewAssertions.matches(CoreMatchers.not(ViewMatchers.isCompletelyDisplayed())));
    }

    private void applyFilter(String filter) {
        onView(ViewMatchers.withId(R.id.filter_fab)).perform(ViewActions.click());

        String uncheckedFilterContentDesc =
                resources.getString(R.string.a11y_filter_not_applied, filter);

        onView(AllOf.allOf(ViewMatchers.withId(R.id.recyclerview), ViewMatchers.withParent(ViewMatchers.withId(R.id.filter_sheet))))
                .check(ViewMatchers.matches(ViewMatchers.isDisplayed()));

        onView(AllOf.allOf(ViewMatchers.withId(R.id.recyclerview), ViewMatchers.withParent(ViewMatchers.withId(R.id.filter_sheet))))
                .perform(
                        RecyclerViewActions.scrollTo<ScheduleFilterAdapter.FilterViewHolder>(
                                hasDescendant(ViewMatchers.withContentDescription(uncheckedFilterContentDesc))
                        )
                );

        onView(ViewMatchers.withContentDescription(uncheckedFilterContentDesc))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .perform(ViewActions.click());

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
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
        );

        if (scale > 0) {
            throw new RuntimeException(
                    "Device must have animations disabled. " +
                            "Developer options -> Animator duration scale"
            );
        }
    }
}