package com.google.samples.apps.sunflower.compose.plantdetail;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.RawRes;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.platform.LocalContext;
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

import static androidx.compose.ui.test.assertIsDisplayed;
import static androidx.compose.ui.test.assertDoesNotExist;

@RunWith(AndroidJUnit4.class)
class PlantDetailComposeTest {

    @Rule
    public final createComposeRule composeTestRule = new createComposeRule();

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

    @Test
    public void plantDetails_checkGalleryNotShown() {
        startPlantDetails(true, false);
        composeTestRule.onNodeWithContentDescription("Gallery Icon").assertDoesNotExist();
    }

    @Test
    public void plantDetails_checkGalleryIsShown() {
        startPlantDetails(true, true);
        composeTestRule.onNodeWithContentDescription("Gallery Icon").assertIsDisplayed();
    }

    private void startPlantDetails(boolean isPlanted, boolean hasUnsplashKey) {
        composeTestRule.setContent(
                () -> PlantDetails(
                        plantForTesting(),
                        isPlanted,
                        new PlantDetailsCallbacks() {
                            @Override
                            public void onAddPlantClicked() {

                            }

                            @Override
                            public void onInfoCardClicked() {

                            }

                            @Override
                            public void onGalleryClicked() {

                            }

                            @Override
                            public void onDismiss() {

                            }
                        },
                        hasUnsplashKey
                )
        );
    }

    @Composable
    public static Plant plantForTesting() {
        return new Plant(
                "malus-pumila",
                "Apple",
                "",
                3,
                30,
                rawUri(R.raw.apple).toString()
        );
    }

    @Composable
    private static Uri rawUri(@RawRes int id) {
        return Uri.parse(
                ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + LocalContext.current.getPackageName() + "/" + id
        );
    }
}