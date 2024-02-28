

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
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.SyncTaskExecutorRule;
import androidx.test.rule.TestRule;
import androidx.test.rule.TimeRule;
import androidx.test.rule.TimerScheduleRule;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.data.FakeConferenceDataSource;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDays;
import com.google.samples.apps.iosched.tests.FixedTimeRule;
import com.google.samples.apps.iosched.tests.SetPreferencesRule;
import com.google.samples.apps.iosched.ui.schedule.day.SessionViewHolder;
import com.google.samples.apps.iosched.ui.schedule.filters.ScheduleFilterAdapter;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.AllOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.AllOf.allOf;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ScheduleTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new MainActivityTestRule<>(R.id.navigation_schedule);

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Rule
    public TimeRule timeProviderRule = new FixedTimeRule();

    @Rule
    public TestRule setPreferencesRule = new SetPreferencesRule();

    private Context context;

    @Before
    public void disableBottomSheetAnimations() {
        context = ApplicationProvider.getApplicationContext();
        BottomSheetBehavior behavior = BottomSheetBehavior.from(
                activityRule.getActivity().findViewById(R.id.filter_sheet)
        );
        behavior.setAnimationDisabled(true);
    }

    @Test
    public void allDays_areClicked_showsSessions() {
        for (int i = 0; i < ConferenceDays.values().length; i++) {
            ConferenceDays conferenceDay = ConferenceDays.values()[i];
            String dayTitle = conferenceDay.formatMonthDay();
            onView(withText(dayTitle)).perform(ViewActions.click());
            onView(withText("First session day " + (i + 1)))
                    .check(ViewAssertions.matches(isDisplayed()));
        }
    }

    @Test
    public void clickOnFirstItem_detailsShow() {
        onView(allOf(withId(R.id.recyclerview), hasFocus()))
                .perform(RecyclerViewActions.actionOnItemAtPosition<SessionViewHolder>(0, ViewActions.click()));

        onView(
                allOf(
                        withId(R.id.session_detail_with_video_title),
                        withText("First session day 1")
                )
        )
                .check(ViewAssertions.matches(isDisplayed()));
    }

    @Test
    public void clickFilters_showFilters() {
        checkAnimationsDisabled();

        onView(withId(R.id.filter_fab)).perform(ViewActions.click());

        String uncheckedFilterContentDesc =
                context.getString(R.string.a11y_filter_not_applied, FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);
        String checkedFilterContentDesc =
                context.getString(R.string.a11y_filter_applied, FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);

        onView(allOf(withId(R.id.recyclerview), withParent(withId(R.id.filter_sheet))))
                .perform(
                        RecyclerViewActions.scrollTo<ScheduleFilterAdapter.FilterViewHolder>(
                                hasDescendant(withContentDescription(uncheckedFilterContentDesc))
                        )
                );

        onView(withContentDescription(uncheckedFilterContentDesc))
                .check(ViewAssertions.matches(isDisplayed()))
                .perform(ViewActions.click());

        onView(
                allOf(
                        withId(R.id.filter_label),
                        withContentDescription(checkedFilterContentDesc),
                        not(withParent(withId(R.id.filter_description_tags)))
                )
        )
                .check(ViewAssertions.matches(isDisplayed()))
                .perform(ViewActions.click());
    }

    @Test
    public void filters_applyAFilter() {
        checkAnimationsDisabled();
        String sessionName = FakeConferenceDataSource.FAKE_SESSION_NAME;

        applyFilter(FakeConferenceDataSource.FAKE_SESSION_TAG_NAME);

        onView(withText(sessionName))
                .check(ViewAssertions.matches(isDisplayed()));
    }

    @Test
    public void filters_clearFilters() {

        String filter = FakeConferenceDataSource.FAKE_SESSION_TAG_NAME;
        applyFilter(filter);

        onView(withId(R.id.clear_filters_shortcut)).perform(ViewActions.click());

        onView(
                allOf(
                        withId(R.id.filter_label),
                        withContentDescription(getActiveFilterContDesc(filter)),
                        withParent(withId(R.id.filter_description_tags))
                )
        ).check(ViewAssertions.matches(not(ViewMatchers.isCompletelyDisplayed())));
    }

    private void applyFilter(String filter) {
        onView(withId(R.id.filter_fab)).perform(ViewActions.click());

        String uncheckedFilterContentDesc =
                context.getString(R.string.a11y_filter_not_applied, filter);

        onView(allOf(withId(R.id.recyclerview), withParent(withId(R.id.filter_sheet))))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        onView(allOf(withId(R.id.recyclerview), withParent(withId(R.id.filter_sheet))))
                .perform(
                        RecyclerViewActions.scrollTo<ScheduleFilterAdapter.FilterViewHolder>(
                                hasDescendant(withContentDescription(uncheckedFilterContentDesc))
                        )
                );

        onView(withContentDescription(uncheckedFilterContentDesc))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .perform(ViewActions.click());

        pressBack();
    }

    private String getDisabledFilterContDesc(String filter) {
        return context.getString(R.string.a11y_filter_not_applied, filter);
    }

    private String getActiveFilterContDesc(String filter) {
        return context.getString(R.string.a11y_filter_applied, filter);
    }

    private void checkAnimationsDisabled() {
        float scale = Settings.Global.getFloat(
                context.getContentResolver(),
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