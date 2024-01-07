package com.google.samples.apps.sunflower;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.platform.LocalContext;
import androidx.compose.ui.test.junit4.CreateComposeRule;
import androidx.compose.ui.test.SemanticsMatcher;
import androidx.compose.ui.test.junit4.createComposeRule;
import androidx.compose.ui.test.SemanticsNodeInteraction;
import androidx.compose.ui.test.nodeWithContentDescription;
import androidx.compose.ui.test.nodeWithText;
import androidx.core.net.ToUri;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantDetails;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantDetailsCallbacks;
import com.google.samples.apps.sunflower.data.Plant;
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PlantDetailComposeTest {

    @Rule
    public CreateComposeRule composeTestRule = createComposeRule();

    private Plant plant;

    @Before
    public void setUp() {
        plant = new Plant(
            "malus-pumila",
            "Apple",
            "An apple is a sweet, edible fruit produced by an apple tree (Malus pumila). Apple trees are cultivated worldwide, and are the most widely grown species in the genus Malus. The tree originated in Central Asia, where its wild ancestor, Malus sieversii, is still found today. Apples have been grown for thousands of years in Asia and Europe, and were brought to North America by European colonists. Apples have religious and mythological significance in many cultures, including Norse, Greek and European Christian traditions.<br><br>Apple trees are large if grown from seed. Generally apple cultivars are propagated by grafting onto rootstocks, which control the size of the resulting tree. There are more than 7,500 known cultivars of apples, resulting in a range of desired characteristics. Different cultivars are bred for various tastes and uses, including cooking, eating raw and cider production. Trees and fruit are prone to a number of fungal, bacterial and pest problems, which can be controlled by a number of organic and non-organic means. In 2010, the fruit's genome was sequenced as part of research on disease control and selective breeding in apple production.<br><br>Worldwide production of apples in 2014 was 84.6 million tonnes, with China accounting for 48% of the total.<br><br>(From <a href=\"https://www.britannica.com/fruit/apple\">Britannica</a>)",
            3,
            30,
            rawUri(R.raw.apple).toString());
    }

    @Test
    public void plantDetails_checkIsNotPlanted() {
        startPlantDetails(false);
        SemanticsNodeInteraction node = composeTestRule.onNodeWithText("Apple");
        node.assertIsDisplayed();
        node = composeTestRule.onNodeWithContentDescription("Add plant");
        node.assertIsDisplayed();
    }

    @Test
    public void plantDetails_checkIsPlanted() {
        startPlantDetails(true);
        SemanticsNodeInteraction node = composeTestRule.onNodeWithText("Apple");
        node.assertIsDisplayed();
        composeTestRule.onNodeWithContentDescription(new SemanticsMatcher() {
            @NonNull
            @Override
            public String describeTo(ConditionDescription description) {
                description.appendText("Add plant");
                return null;
            }

            @Override
            public boolean matches(SemanticsNode node, MatcherResult matcherResult) {
                return Objects.equals(node.getContentDescription(), "Add plant");
            }
        }).assertDoesNotExist();
    }

    private void startPlantDetails(boolean isPlanted) {
        composeTestRule.setContent {
            PlantDetails(
                plant,
                isPlanted,
                new PlantDetailsCallbacks() {
                    @Override
                    public void onFavoriteClicked() {}

                    @Override
                    public void onInfoClicked() {}

                    @Override
                    public void onPlantClicked() {}
                });
        };
    }

    @Composable
    private Uri rawUri(@RawRes int id) {
        return Uri.parse("android.resource://" + LocalContext.current.getPackageName() + "/raw/" + id);
    }
}