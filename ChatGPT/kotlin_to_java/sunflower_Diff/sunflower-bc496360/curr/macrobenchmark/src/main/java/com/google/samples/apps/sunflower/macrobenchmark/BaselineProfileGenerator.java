
package com.google.samples.apps.sunflower.macrobenchmark;

import androidx.benchmark.macro.ExperimentalBaselineProfilesApi;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@OptIn(ExperimentalBaselineProfilesApi.class)
@RunWith(AndroidJUnit4.class)
public class BaselineProfileGenerator {

    @Rule
    public BaselineProfileRule rule = new BaselineProfileRule();

    @Test
    public void startPlantListPlantDetail() {
        rule.collectBaselineProfile(PACKAGE_NAME, () -> {
            
            pressHome();
            startActivityAndWait();

            
            UiObject2 plantListTab = device.findObject(By.descContains("Plant list"));
            plantListTab.click();
            device.waitForIdle();
            
            Thread.sleep(500);

            
            UiObject2 plantList = device.findObject(By.res(packageName, "plant_list"));
            UiObject2 listItem = plantList.getChildren().get(0);
            listItem.click();
            device.wait(Until.gone(By.res(packageName, "plant_list")), 5_000);
        });
    }
}
