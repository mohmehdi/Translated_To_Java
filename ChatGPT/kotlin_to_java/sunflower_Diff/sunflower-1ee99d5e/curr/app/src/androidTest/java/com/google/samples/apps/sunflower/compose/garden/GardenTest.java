
package com.google.samples.apps.sunflower.compose.garden;

import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.createComposeRule;
import androidx.compose.ui.test.onNodeWithText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import com.google.samples.apps.sunflower.utilities.testPlantAndGardenPlanting;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GardenTest {

    @Rule
    public final createComposeRule composeTestRule = createComposeRule();

    @Test
    public void garden_emptyGarden() {
        startGarden(Collections.emptyList());
        composeTestRule.onNodeWithText("Add plant").assertIsDisplayed();
    }

    @Test
    public void garden_notEmptyGarden() {
        startGarden(Collections.singletonList(testPlantAndGardenPlanting));
        composeTestRule.onNodeWithText("Add plant").assertDoesNotExist();
        composeTestRule.onNodeWithText(testPlantAndGardenPlanting.getPlant().getName()).assertIsDisplayed();
    }

    private void startGarden(List<PlantAndGardenPlantings> gardenPlantings) {
        composeTestRule.setContent(() -> {
            GardenScreen gardenScreen = new GardenScreen(gardenPlantings);
            return gardenScreen;
        });
    }
}
