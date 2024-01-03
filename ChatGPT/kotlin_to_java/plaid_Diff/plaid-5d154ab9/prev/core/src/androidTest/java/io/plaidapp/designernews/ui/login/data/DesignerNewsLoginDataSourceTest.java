package io.plaidapp.designernews.ui.login.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.login.data.LoginLocalDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class DesignerNewsLoginDataSourceTest {

    private SharedPreferences sharedPreferences = InstrumentationRegistry.getInstrumentation().getContext()
            .getSharedPreferences("test", Context.MODE_PRIVATE);

    private LoginLocalDataSource dataSource = new LoginLocalDataSource(sharedPreferences);

    @After
    public void tearDown() {
        sharedPreferences.edit().clear().commit();
    }

    @Test
    public void user_default() {
        Assert.assertNull(dataSource.getUser());
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

        dataSource.setUser(user);

        Assert.assertEquals(user, dataSource.getUser());
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
        dataSource.setUser(user);

        dataSource.logout();

        Assert.assertNull(dataSource.getUser());
    }
}