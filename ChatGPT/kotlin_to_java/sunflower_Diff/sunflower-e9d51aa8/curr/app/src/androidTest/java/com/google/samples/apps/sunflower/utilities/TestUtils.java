
package com.google.samples.apps.sunflower.utilities;

import android.app.Activity;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.v7.widget.Toolbar;
import android.view.View;
import com.google.samples.apps.sunflower.data.GardenPlanting;
import com.google.samples.apps.sunflower.data.Plant;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import java.util.ArrayList;
import java.util.Calendar;

public class TestUtils {
    public static ArrayList<Plant> testPlants = new ArrayList<Plant>() {{
        add(new Plant("1", "Apple", "A red fruit", 1));
        add(new Plant("2", "B", "Description B", 1));
        add(new Plant("3", "C", "Description C", 2));
    }};
    public static Plant testPlant = testPlants.get(0);

    public static Calendar testCalendar = Calendar.getInstance();
    static {
        testCalendar.set(Calendar.YEAR, 1998);
        testCalendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
        testCalendar.set(Calendar.DAY_OF_MONTH, 4);
    }

    public static GardenPlanting testGardenPlanting = new GardenPlanting(
            "1", testPlant.getPlantId(), testCalendar, testCalendar);

    public static Matcher<View> withCollapsingToolbarTitle(final String string) {
        return new BoundedMatcher<View, CollapsingToolbarLayout>(CollapsingToolbarLayout.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("with toolbar title: " + string);
            }

            @Override
            protected boolean matchesSafely(CollapsingToolbarLayout toolbar) {
                return string.equals(toolbar.getTitle());
            }
        };
    }

    public static String getToolbarNavigationContentDescription(Activity activity, int toolbarId) {
        return ((Toolbar) activity.findViewById(toolbarId)).getNavigationContentDescription().toString();
    }
}
