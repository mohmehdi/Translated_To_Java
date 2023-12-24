
package com.google.samples.apps.sunflower.macrobenchmark;

import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlantListBenchmarks {
    @Rule
    public MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    @Test
    public void openPlantList() {
        openPlantList(CompilationMode.None());
    }

    @Test
    public void plantListCompilationPartial() {
        openPlantList(CompilationMode.Partial());
    }

    @Test
    public void plantListCompilationFull() {
        openPlantList(CompilationMode.Full());
    }

    private void openPlantList(CompilationMode compilationMode) {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                listOf(new FrameTimingMetric()),
                compilationMode,
                5,
                StartupMode.COLD,
                () -> {
                    pressHome();
                    startActivityAndWait();
                },
                () -> {
                    goToPlantListTab();
                }
        );
    }

    public void goToPlantListTab() {
        By plantListTab = By.descContains("Plant list");
        device.findObject(plantListTab).click();

        By recyclerHasChild = By.hasChild(By.res(packageName, "plant_list"));
        device.wait(Until.hasObject(recyclerHasChild), 5000);

        device.waitForIdle();
    }
}
