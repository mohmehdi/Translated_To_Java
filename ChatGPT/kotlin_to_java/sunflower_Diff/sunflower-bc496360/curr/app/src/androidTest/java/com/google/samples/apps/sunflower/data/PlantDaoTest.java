
package com.google.samples.apps.sunflower.data;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlinx.coroutines.flow.first;
import kotlinx.coroutines.runBlocking;

@RunWith(AndroidJUnit4.class)
public class PlantDaoTest {
    private AppDatabase database;
    private PlantDao plantDao;
    private Plant plantA = new Plant("1", "A", "", 1, 1, "");
    private Plant plantB = new Plant("2", "B", "", 1, 1, "");
    private Plant plantC = new Plant("3", "C", "", 2, 2, "");

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void createDb() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        plantDao = database.plantDao();

        runBlocking(() -> {
            plantDao.insertAll(Arrays.asList(plantB, plantC, plantA));
        });
    }

    @After
    public void closeDb() {
        database.close();
    }

    @Test
    public void testGetPlants() throws InterruptedException {
        runBlocking(() -> {
            List<Plant> plantList = plantDao.getPlants().first();
            Assert.assertThat(plantList.size(), Matchers.equalTo(3));

            Assert.assertThat(plantList.get(0), Matchers.equalTo(plantA));
            Assert.assertThat(plantList.get(1), Matchers.equalTo(plantB));
            Assert.assertThat(plantList.get(2), Matchers.equalTo(plantC));
        });
    }

    @Test
    public void testGetPlantsWithGrowZoneNumber() throws InterruptedException {
        runBlocking(() -> {
            List<Plant> plantList = plantDao.getPlantsWithGrowZoneNumber(1).first();
            Assert.assertThat(plantList.size(), Matchers.equalTo(2));
            Assert.assertThat(plantDao.getPlantsWithGrowZoneNumber(2).first().size(), Matchers.equalTo(1));
            Assert.assertThat(plantDao.getPlantsWithGrowZoneNumber(3).first().size(), Matchers.equalTo(0));

            Assert.assertThat(plantList.get(0), Matchers.equalTo(plantA));
            Assert.assertThat(plantList.get(1), Matchers.equalTo(plantB));
        });
    }

    @Test
    public void testGetPlant() throws InterruptedException {
        runBlocking(() -> {
            Assert.assertThat(plantDao.getPlant(plantA.getPlantId()).first(), Matchers.equalTo(plantA));
        });
    }
}
