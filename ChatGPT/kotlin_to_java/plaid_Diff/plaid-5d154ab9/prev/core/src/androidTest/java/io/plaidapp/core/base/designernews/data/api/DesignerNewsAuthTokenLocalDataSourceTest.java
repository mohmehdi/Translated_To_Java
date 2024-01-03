package io.plaidapp.core.designernews.data.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class DesignerNewsAuthTokenLocalDataSourceTest {
    private SharedPreferences sharedPreferences = InstrumentationRegistry.getInstrumentation().getContext()
            .getSharedPreferences("test", Context.MODE_PRIVATE);

    private DesignerNewsAuthTokenLocalDataSource dataSource = new DesignerNewsAuthTokenLocalDataSource(sharedPreferences);

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