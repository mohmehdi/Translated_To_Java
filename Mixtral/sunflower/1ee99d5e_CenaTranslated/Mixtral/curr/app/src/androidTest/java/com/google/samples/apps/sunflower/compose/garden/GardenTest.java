package com.google.samples.apps.sunflower.compose.garden;

import androidx.annotation.NonNull;
import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.CreateComposeRule;
import androidx.compose.ui.test.onNodeWithText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import com.google.samples.apps.sunflower.utilities.testPlantAndGardenPlanting;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class GardenTest {

    @Rule
    public CreateComposeRule composeTestRule = new CreateComposeRule();

    @Test
    public void garden_emptyGarden() {
        startGarden(Collections.emptyList());
        composeTestRule.onNodeWithText("Add plant").assertIsDisplayed();
    }

    @Test
    public void garden_notEmptyGarden() {
        PlantAndGardenPlantings plantAndGardenPlanting = testPlantAndGardenPlanting;
        startGarden(Collections.singletonList(plantAndGardenPlanting));
        composeTestRule.onNodeWithText("Add plant").assertDoesNotExist();
        composeTestRule.onNodeWithText(plantAndGardenPlanting.getPlant().getName()).assertIsDisplayed();
    }

    private void startGarden(List < PlantAndGardenPlantings > gardenPlantings) {
        composeTestRule.setContent(() - > {
            GardenScreen gardenScreen = new GardenScreen();
            gardenScreen.setGardenPlants(gardenPlantings);
            return gardenScreen;
        });
    }
}