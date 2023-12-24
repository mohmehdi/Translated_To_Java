
package com.google.samples.apps.sunflower.data;

import android.arch.persistence.room.Room;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.matcher.ViewMatchers;
import com.google.samples.apps.sunflower.utilities.getValue;
import com.google.samples.apps.sunflower.utilities.testCalendar;
import com.google.samples.apps.sunflower.utilities.testPlant;
import com.google.samples.apps.sunflower.utilities.testPlants;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GardenPlantingDaoTest {
    private AppDatabase database;
    private GardenPlantingDao gardenPlantingDao;
    private GardenPlanting gardenPlanting = new GardenPlanting("1", testPlant.getPlantId(), testCalendar, testCalendar);

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        gardenPlantingDao = database.gardenPlantingDao();

        database.plantDao().insertAll(testPlants);
        gardenPlantingDao.insertGardenPlanting(gardenPlanting);
    }

    @After
    public void closeDb() {
        database.close();
    }

    @Test
    public void testGetGardenPlantings() {
        GardenPlanting gardenPlanting2 = new GardenPlanting("2", testPlants.get(1).getPlantId(), testCalendar, testCalendar);
        gardenPlantingDao.insertGardenPlanting(gardenPlanting2);
        assertThat(getValue(gardenPlantingDao.getGardenPlantings()).size(), CoreMatchers.equalTo(2));
    }

    @Test
    public void testGetGardenPlanting() {
        assertThat(getValue(gardenPlantingDao.getGardenPlanting(gardenPlanting.getGardenPlantingId())),
                CoreMatchers.equalTo(gardenPlanting));
    }
}
