
package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.android.architecture.blueprints.todoapp.R;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AddEditTaskScreenTest {

    @Test
    @Ignore("hangs in robolectric, see issue #4724")
    public void emptyTask_isNotSaved() {

        AddEditTaskFragmentArgs bundle = new AddEditTaskFragmentArgs().toBundle();
        FragmentScenario.launchInContainer(AddEditTaskFragment.class, bundle, R.style.AppTheme);

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(ViewActions.clearText());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(ViewActions.clearText());

        Espresso.onView(ViewMatchers.withId(R.id.fab_edit_task_done)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}
