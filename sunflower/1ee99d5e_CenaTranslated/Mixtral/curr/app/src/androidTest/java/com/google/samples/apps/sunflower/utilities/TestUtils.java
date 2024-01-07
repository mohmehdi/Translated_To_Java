package com.google.samples.apps.sunflower.utilities;

import android.app.Activity;
import android.content.Intent;
import androidx.appcompat.widget.Toolbar;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import com.google.samples.apps.sunflower.data.GardenPlanting;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

public class TestUtilities {

    List testPlants = new ArrayList < > ();
    Plant testPlant;
    Calendar testCalendar;
    GardenPlanting testGardenPlanting;
    PlantAndGardenPlantings testPlantAndGardenPlanting;

    public TestUtilities() {
        testPlants.add(new Plant("1", "Apple", "A red fruit", 1));
        testPlants.add(new Plant("2", "B", "Description B", 1));
        testPlants.add(new Plant("3", "C", "Description C", 2));
        testPlant = testPlants.get(0);

        testCalendar = Calendar.getInstance();
        testCalendar.set(1998, Calendar.SEPTEMBER, 4);

        testGardenPlanting = new GardenPlanting(testPlant.getPlantId(), testCalendar, testCalendar);

        testPlantAndGardenPlanting = new PlantAndGardenPlantings(testPlant, List.of(testGardenPlanting));
    }

    public String getToolbarNavigationContentDescription(Activity activity, int toolbarId) {
        Toolbar toolbar = activity.findViewById(toolbarId);
        return (String) toolbar.getNavigationContentDescription();
    }

    public Matcher chooser(Matcher matcher) {
        return allOf(IntentMatchers.hasAction(Intent.ACTION_CHOOSER), IntentMatchers.hasExtra(is(Intent.EXTRA_INTENT), matcher));
    }
}