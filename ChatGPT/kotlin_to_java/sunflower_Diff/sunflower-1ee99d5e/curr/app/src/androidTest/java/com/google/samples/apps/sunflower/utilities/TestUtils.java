
package com.google.samples.apps.sunflower.utilities;

import android.app.Activity;
import android.content.Intent;
import androidx.appcompat.widget.Toolbar;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import com.google.samples.apps.sunflower.data.GardenPlanting;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Utilities {

    public static List<Plant> testPlants = new ArrayList<>();
    public static Plant testPlant;

    static {
        testPlants.add(new Plant("1", "Apple", "A red fruit", 1));
        testPlants.add(new Plant("2", "B", "Description B", 1));
        testPlants.add(new Plant("3", "C", "Description C", 2));
        testPlant = testPlants.get(0);
    }

    public static Calendar testCalendar = Calendar.getInstance();

    static {
        testCalendar.set(Calendar.YEAR, 1998);
        testCalendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
        testCalendar.set(Calendar.DAY_OF_MONTH, 4);
    }

    public static GardenPlanting testGardenPlanting = new GardenPlanting(testPlant.getPlantId(), testCalendar, testCalendar);

    public static PlantAndGardenPlantings testPlantAndGardenPlanting = new PlantAndGardenPlantings(testPlant, List.of(testGardenPlanting));

    public static String getToolbarNavigationContentDescription(Activity activity, int toolbarId) {
        return ((Toolbar) activity.findViewById(toolbarId)).getNavigationContentDescription().toString();
    }

    public static Matcher<Intent> chooser(Matcher<Intent> matcher) {
        return Matchers.allOf(
                IntentMatchers.hasAction(Intent.ACTION_CHOOSER),
                IntentMatchers.hasExtra(Matchers.is(Intent.EXTRA_INTENT), matcher)
        );
    }
}
