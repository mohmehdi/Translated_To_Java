
package com.google.samples.apps.sunflower.macrobenchmark;

import androidx.benchmark.macro.BaselineProfileMode;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StartupBenchmarks {
    @Rule
    public MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    @Test
    public void startupCompilationNone() {
        startup(new CompilationMode.None());
    }

    @Test
    public void startupCompilationPartial() {
        startup(new CompilationMode.Partial());
    }

    @Test
    public void startupCompilationWarmup() {
        startup(new CompilationMode.Partial(BaselineProfileMode.Disable, 2));
    }

    private void startup(CompilationMode compilationMode) {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                listOf(new StartupTimingMetric()),
                5,
                compilationMode,
                StartupMode.COLD,
                () -> {
                    pressHome();
                },
                () -> {
                    startActivityAndWait();
                    By recyclerHasChild = By.hasChild(By.res(PACKAGE_NAME, "garden_list"));
                    device.wait(Until.hasObject(recyclerHasChild), 5_000);
                }
        );
    }
}
