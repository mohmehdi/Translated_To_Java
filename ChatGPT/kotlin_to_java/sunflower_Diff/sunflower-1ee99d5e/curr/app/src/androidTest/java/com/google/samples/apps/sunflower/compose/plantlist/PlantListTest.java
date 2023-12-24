
package com.google.samples.apps.sunflower.compose.plantlist;

import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.createComposeRule;
import androidx.compose.ui.test.onNodeWithText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantForTesting;
import com.google.samples.apps.sunflower.data.Plant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlantListTest {
    @Rule
    public final createComposeRule composeTestRule = createComposeRule();

    @Test
    public void plantList_itemShown() {
        startPlantList();
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed();
    }

    private void startPlantList(OnPlantClick onPlantClick) {
        composeTestRule.setContent(() -> {
            PlantListScreen plantListScreen = new PlantListScreen();
            plantListScreen.setPlants(Arrays.asList(PlantForTesting.plantForTesting()));
            plantListScreen.setOnPlantClick(onPlantClick);
            return plantListScreen;
        });
    }
}
