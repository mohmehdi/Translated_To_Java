package io.plaidapp.core.base.designernews.data.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DesignerNewsAuthTokenLocalDataSourceTest {

    private SharedPreferences sharedPreferences;
    private DesignerNewsAuthTokenLocalDataSource dataSource;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        sharedPreferences = context.getSharedPreferences("test", Context.MODE_PRIVATE);
        dataSource = new DesignerNewsAuthTokenLocalDataSource(sharedPreferences);
    }

    @After
    public void tearDown() {
        sharedPreferences.edit().clear().commit();
    }

    @Test
    public void authToken_default() {
        Assert.assertNull(dataSource.getAuthToken());
    }

    @Test
    public void authToken_set() {
        dataSource.setAuthToken("my token");
        Assert.assertEquals("my token", dataSource.getAuthToken());
    }

    @Test
    public void clearData() {
        dataSource.setAuthToken("token");
        dataSource.clearData();
        Assert.assertNull(dataSource.getAuthToken());
    }
}