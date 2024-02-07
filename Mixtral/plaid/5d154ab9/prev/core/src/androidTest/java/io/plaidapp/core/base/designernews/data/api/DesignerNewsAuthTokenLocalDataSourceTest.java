package io.io.plaidapp.core.designernews.data.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.internal.runner.junit3.AndroidAnnotatedBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class DesignerNewsAuthTokenLocalDataSourceTest {

  private SharedPreferences sharedPreferences;
  private DesignerNewsAuthTokenLocalDataSource dataSource;

  @After
  public void tearDown() {
    sharedPreferences.edit().clear().commit();
  }

  @Test
  public void authToken_default() {
    assertNull(dataSource.authToken);
  }

  @Test
  public void authToken_set() {
    dataSource.authToken = "my token";
    assertEquals("my token", dataSource.authToken);
  }

  @Test
  public void clearData() {
    dataSource.authToken = "token";
    dataSource.clearData();
    assertNull(dataSource.authToken);
  }
}
