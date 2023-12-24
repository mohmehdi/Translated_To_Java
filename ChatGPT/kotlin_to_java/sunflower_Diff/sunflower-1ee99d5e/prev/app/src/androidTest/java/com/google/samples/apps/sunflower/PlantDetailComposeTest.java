
package com.google.samples.apps.sunflower;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.RawRes;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.platform.LocalContext;
import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.createComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.core.net.toUri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantDetails;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantDetailsCallbacks;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.test.R;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlantDetailComposeTest {

    @get:Rule
    public final createComposeRule composeTestRule = createComposeRule();

    @Test
    public void plantDetails_checkIsNotPlanted() {
        startPlantDetails(false);
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed();
        composeTestRule.onNodeWithContentDescription("Add plant").assertIsDisplayed();
    }

    @Test
    public void plantDetails_checkIsPlanted() {
        startPlantDetails(true);
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed();
        composeTestRule.onNodeWithContentDescription("Add plant").assertDoesNotExist();
    }

    private void startPlantDetails(boolean isPlanted) {
        composeTestRule.setContent(() -> {
            PlantDetails(
                plantForTesting(),
                isPlanted,
                new PlantDetailsCallbacks(() -> {}, () -> {}, () -> {})
            );
        });
    }

    @Composable
    private Plant plantForTesting() {
        return new Plant(
            "malus-pumila",
            "Apple",
            "An apple is a sweet, edible fruit produced by an apple tree (Malus pumila). Apple trees are cultivated worldwide, and are the most widely grown species in the genus Malus. The tree originated in Central Asia, where its wild ancestor, Malus sieversii, is still found today. Apples have been grown for thousands of years in Asia and Europe, and were brought to North America by European colonists. Apples have religious and mythological significance in many cultures, including Norse, Greek and European Christian traditions.<br><br>Apple trees are large if grown from seed. Generally apple cultivars are propagated by grafting onto rootstocks, which control the size of the resulting tree. There are more than 7,500 known cultivars of apples, resulting in a range of desired characteristics. Different cultivars are bred for various tastes and uses, including cooking, eating raw and cider production. Trees and fruit are prone to a number of fungal, bacterial and pest problems, which can be controlled by a number of organic and non-organic means. In 2010, the fruit's genome was sequenced as part of research on disease control and selective breeding in apple production.<br><br>Worldwide production of apples in 2014 was 84.6 million tonnes, with China accounting for 48% of the total.<br><br>(From <a href=\\\"https:",
            3,
            30,
            rawUri(R.raw.apple).toString()
        );
    }

    @Composable
    private Uri rawUri(@RawRes int id) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + LocalContext.current.getPackageName() + "/" + id);
    }
}
