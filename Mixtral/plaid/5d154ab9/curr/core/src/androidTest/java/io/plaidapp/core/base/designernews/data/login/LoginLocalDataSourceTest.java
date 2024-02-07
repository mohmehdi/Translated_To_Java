package io.plaidapp.core.base.designernews.data.login;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource;
import io.plaidapp.core.designernews.data.users.model.User;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class LoginLocalDataSourceTest {

  private Context context = InstrumentationRegistry
    .getInstrumentation()
    .getTargetContext();
  private android.content.SharedPreferences sharedPreferences = context.getSharedPreferences(
    "test",
    Context.MODE_PRIVATE
  );
  private LoginLocalDataSource dataSource = new LoginLocalDataSource(
    sharedPreferences
  );

  @After
  public void tearDown() {
    sharedPreferences.edit().clear().commit();
  }

  @Test
  public void user_default() {
    Assert.assertNull(dataSource.user);
  }

  @Test
  public void user_set() {
    User user = new User(
      3,
      "Pladinium",
      "Plaidescu",
      "Plaidinium Plaidescu",
      "www"
    );

    dataSource.user = user;

    Assert.assertEquals(user, dataSource.user);
  }

  @Test
  public void logout() {
    User user = new User(
      3,
      "Plaidy",
      "Plaidinkski",
      "Plaidy Plaidinski",
      "www"
    );
    dataSource.user = user;

    dataSource.logout();

    Assert.assertNull(dataSource.user);
  }
}
