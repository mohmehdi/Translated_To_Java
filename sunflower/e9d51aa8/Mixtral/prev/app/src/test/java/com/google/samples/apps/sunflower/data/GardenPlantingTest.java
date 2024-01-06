package com.google.samples.apps.sunflower.data;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

import java.util.Calendar;
import java.util.Calendar;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public class GardenPlantingTest {

  public class GardenPlanting {

    private String plantId;
    private String gardenId;
    private Calendar plantDate;
    private Calendar lastWateringDate;

    public GardenPlanting(String plantId, String gardenId) {
      this.plantId = plantId;
      this.gardenId = gardenId;
      this.plantDate = Calendar.getInstance();
      this.lastWateringDate = Calendar.getInstance();
    }

    @Test
    public void test_default_values() {
      GardenPlanting gardenPlanting = new GardenPlanting("1", "1");
      Calendar cal = Calendar.getInstance();
      assertYMD(cal, gardenPlanting.plantDate);
      assertYMD(cal, gardenPlanting.lastWateringDate);
    }

    private static void assertYMD(Calendar expectedCal, Calendar actualCal) {
      Assert.assertThat(
        actualCal.get(YEAR),
        CoreMatchers.equalTo(expectedCal.get(YEAR))
      );
      Assert.assertThat(
        actualCal.get(MONTH),
        CoreMatchers.equalTo(expectedCal.get(MONTH))
      );
      Assert.assertThat(
        actualCal.get(DAY_OF_MONTH),
        CoreMatchers.equalTo(expectedCal.get(DAY_OF_MONTH))
      );
    }
  }
}
