
package com.google.samples.apps.sunflower.viewmodels;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;
import com.google.samples.apps.sunflower.utilities.getValue;
import com.google.samples.apps.sunflower.utilities.testPlant;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class PlantDetailViewModelTest {

    private AppDatabase appDatabase;
    private PlantDetailViewModel viewModel;

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() {
        androidx.test.core.app.ApplicationProvider.getApplicationContext();
        appDatabase = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class).build();

        PlantRepository plantRepo = PlantRepository.getInstance(appDatabase.plantDao());
        GardenPlantingRepository gardenPlantingRepo = GardenPlantingRepository.getInstance(
                appDatabase.gardenPlantingDao()
        );
        viewModel = new PlantDetailViewModel(plantRepo, gardenPlantingRepo, testPlant.getPlantId());
    }

    @After
    public void tearDown() {
        appDatabase.close();
    }

    @Test
    public void testDefaultValues() throws InterruptedException {
        assertFalse(getValue(viewModel.getIsPlanted()));
    }
}
