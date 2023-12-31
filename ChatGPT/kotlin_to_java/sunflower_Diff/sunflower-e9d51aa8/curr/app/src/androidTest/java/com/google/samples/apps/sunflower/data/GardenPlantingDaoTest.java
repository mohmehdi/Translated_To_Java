package com.google.samples.apps.sunflower.data;

import android.arch.persistence.room.Room;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.matcher.ViewMatchers;
import com.google.samples.apps.sunflower.utilities.getValue;
import com.google.samples.apps.sunflower.utilities.testCalendar;
import com.google.samples.apps.sunflower.utilities.testGardenPlanting;
import com.google.samples.apps.sunflower.utilities.testPlant;
import com.google.samples.apps.sunflower.utilities.testPlants;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GardenPlantingDaoTest {
    private AppDatabase database;
    private GardenPlantingDao gardenPlantingDao;

    @Before
    public void createDb() {
        android.content.Context context = InstrumentationRegistry.getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        gardenPlantingDao = database.gardenPlantingDao();

        database.plantDao().insertAll(testPlants.INSTANCE);
        gardenPlantingDao.insertGardenPlanting(testGardenPlanting.INSTANCE);
    }

    @After
    public void closeDb() {
        database.close();
    }

    @Test
    public void testGetGardenPlantings() {
        GardenPlanting gardenPlanting2 = new GardenPlanting("2", testPlants.INSTANCE.get(1).getPlantId(), testCalendar.INSTANCE, testCalendar.INSTANCE);
        gardenPlantingDao.insertGardenPlanting(gardenPlanting2);
        Assert.assertThat(getValue(gardenPlantingDao.getGardenPlantings()).size(), CoreMatchers.equalTo(2));
    }

    @Test
    public void testGetGardenPlanting() {
        Assert.assertThat(getValue(gardenPlantingDao.getGardenPlanting(
                testGardenPlanting.INSTANCE.getGardenPlantingId())), CoreMatchers.equalTo(testGardenPlanting.INSTANCE));
    }

    @Test
    public void testGetGardenPlantingForPlant() {
        Assert.assertThat(getValue(gardenPlantingDao.getGardenPlantingForPlant(testPlant.INSTANCE.getPlantId())),
                CoreMatchers.equalTo(testGardenPlanting.INSTANCE));
    }

    @Test
    public void testGetGardenPlantingForPlant_notFound() {
        Assert.assertNull(getValue(gardenPlantingDao.getGardenPlantingForPlant(testPlants.INSTANCE.get(2).getPlantId())));
    }
}