
package com.google.samples.apps.sunflower.data;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.samples.apps.sunflower.utilities.getValue;
import com.google.samples.apps.sunflower.utilities.testCalendar;
import com.google.samples.apps.sunflower.utilities.testGardenPlanting;
import com.google.samples.apps.sunflower.utilities.testPlant;
import com.google.samples.apps.sunflower.utilities.testPlants;
import kotlinx.coroutines.runBlocking;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GardenPlantingDaoTest {
    private AppDatabase database;
    private GardenPlantingDao gardenPlantingDao;
    private long testGardenPlantingId = 0;

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void createDb() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        gardenPlantingDao = database.gardenPlantingDao();

        database.plantDao().insertAll(testPlants);
        testGardenPlantingId = gardenPlantingDao.insertGardenPlanting(testGardenPlanting);
    }

    @After
    public void closeDb() {
        database.close();
    }

    @Test
    public void testGetGardenPlantings() throws InterruptedException {
        GardenPlanting gardenPlanting2 = new GardenPlanting(
            testPlants[1].getPlantId(),
            testCalendar,
            testCalendar
        );
        gardenPlanting2.setGardenPlantingId(2);
        gardenPlantingDao.insertGardenPlanting(gardenPlanting2);
        Assert.assertThat(getValue(gardenPlantingDao.getGardenPlantings()).size(), CoreMatchers.equalTo(2));
    }

    @Test
    public void testDeleteGardenPlanting() throws InterruptedException {
        GardenPlanting gardenPlanting2 = new GardenPlanting(
            testPlants[1].getPlantId(),
            testCalendar,
            testCalendar
        );
        gardenPlanting2.setGardenPlantingId(2);
        gardenPlantingDao.insertGardenPlanting(gardenPlanting2);
        Assert.assertThat(getValue(gardenPlantingDao.getGardenPlantings()).size(), CoreMatchers.equalTo(2));
        gardenPlantingDao.deleteGardenPlanting(gardenPlanting2);
        Assert.assertThat(getValue(gardenPlantingDao.getGardenPlantings()).size(), CoreMatchers.equalTo(1));
    }

    @Test
    public void testGetGardenPlantingForPlant() throws InterruptedException {
        Assert.assertTrue(getValue(gardenPlantingDao.isPlanted(testPlant.getPlantId())));
    }

    @Test
    public void testGetGardenPlantingForPlant_notFound() throws InterruptedException {
        Assert.assertFalse(getValue(gardenPlantingDao.isPlanted(testPlants[2].getPlantId())));
    }

    @Test
    public void testGetPlantAndGardenPlantings() throws InterruptedException {
        List<PlantAndGardenPlantings> plantAndGardenPlantings = getValue(gardenPlantingDao.getPlantedGardens());
        Assert.assertThat(plantAndGardenPlantings.size(), CoreMatchers.equalTo(1));

        Assert.assertThat(plantAndGardenPlantings.get(0).getPlant(), CoreMatchers.equalTo(testPlant));
        Assert.assertThat(plantAndGardenPlantings.get(0).getGardenPlantings().size(), CoreMatchers.equalTo(1));
        Assert.assertThat(plantAndGardenPlantings.get(0).getGardenPlantings().get(0), CoreMatchers.equalTo(testGardenPlanting));
    }
}
