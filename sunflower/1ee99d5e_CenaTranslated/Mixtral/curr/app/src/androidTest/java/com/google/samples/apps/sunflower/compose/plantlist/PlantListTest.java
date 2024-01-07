package com.google.samples.apps.sunflower.compose.plantlist;

import androidx.annotation.NonNull;
import androidx.compose.ui.test.AssertIsDisplayed;
import androidx.compose.ui.test.junit4.AndroidComposeTestRule;
import androidx.compose.ui.test.OnNodeWithText;
import androidx.compose.ui.test.SemidefiniteMatchersKt;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.samples.apps.sunflower.compose.plantdetail.plantForTesting;
import com.google.samples.apps.sunflower.data.Plant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.compose.ui.test.SemidefiniteMatchersKt.assertIsDisplayed;

@RunWith(AndroidJUnit4.class)
public class PlantListTest {
    @Rule
    public AndroidComposeTestRule < MainActivity, TestScene > composeTestRule =
        new AndroidComposeTestRule < > (MainActivity.class, true, false);


    @Before
    public void setUp() {
        composeTestRule.setContent {
            PlantListScreen(
                plants = listOf(plantForTesting()),
                onPlantClick = plant - > {}
            );
        }
    }

    @Test
    public void plantList\ _itemShown() {
        OnNodeWithText appleNode = composeTestRule.onNodeWithText("Apple");
        assertIsDisplayed(appleNode);
    }
}