
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
public class PlantDetailBenchmarks {
    @Rule
    public MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    @Test
    public void plantDetailCompilationNone() {
        benchmarkPlantDetail(new CompilationMode.None());
    }

    @Test
    public void plantDetailCompilationPartial() {
        benchmarkPlantDetail(new CompilationMode.Partial());
    }

    @Test
    public void plantDetailCompilationFull() {
        benchmarkPlantDetail(new CompilationMode.Full());
    }

    private void benchmarkPlantDetail(CompilationMode compilationMode) {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                listOf(new FrameTimingMetric()),
                compilationMode,
                10,
                StartupMode.COLD,
                () -> {
                    startActivityAndWait();
                    goToPlantListTab();
                },
                () -> {
                    goToPlantDetail();
                }
        );
    }
}

public void goToPlantDetail(Integer index) {
    By plantListSelector = By.res(packageName, "plant_list");
    UiObject2 recycler = device.findObject(plantListSelector);

    int currentChildIndex = index != null ? index : ((iteration != null ? iteration : 0) % recycler.getChildCount());

    UiObject2 child = recycler.getChildren().get(currentChildIndex);
    child.click();

    device.wait(Until.gone(plantListSelector), 5_000);
}
