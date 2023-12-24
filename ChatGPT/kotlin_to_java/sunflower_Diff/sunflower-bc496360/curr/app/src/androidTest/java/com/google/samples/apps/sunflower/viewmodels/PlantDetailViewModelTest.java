
package com.google.samples.apps.sunflower.viewmodels;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.samples.apps.sunflower.MainCoroutineRule;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;
import com.google.samples.apps.sunflower.utilities.getValue;
import com.google.samples.apps.sunflower.utilities.testPlant;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.inject.Inject;

import static org.junit.Assert.assertFalse;

@HiltAndroidTest
public class PlantDetailViewModelTest {

    private AppDatabase appDatabase;
    private PlantDetailViewModel viewModel;
    private final HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    private final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    private final MainCoroutineRule coroutineRule = new MainCoroutineRule();

    @Rule
    public RuleChain rule = RuleChain
            .outerRule(hiltRule)
            .around(instantTaskExecutorRule)
            .around(coroutineRule);

    @Inject
    PlantRepository plantRepository;

    @Inject
    GardenPlantingRepository gardenPlantRepository;

    @Before
    public void setUp() {
        hiltRule.inject();

        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();

        SavedStateHandle savedStateHandle = new SavedStateHandle();
        savedStateHandle.set("plantId", testPlant.getPlantId());
        viewModel = new PlantDetailViewModel(savedStateHandle, plantRepository, gardenPlantRepository);
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
