package io.plaidapp.core.base.designernews.data.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import io.plaidapp.core.designernews.data.login.DesignerNewsAuthTokenLocalDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class DesignerNewsAuthTokenLocalDataSourceTest {

  private Context context = InstrumentationRegistry
    .getInstrumentation()
    .getTargetContext();
  private SharedPreferences sharedPreferences = context.getSharedPreferences(
    "test",
    Context.MODE_PRIVATE
  );
  private DesignerNewsAuthTokenLocalDataSource dataSource = new DesignerNewsAuthTokenLocalDataSource(
    sharedPreferences
  );

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
