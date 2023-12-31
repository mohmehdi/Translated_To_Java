package com.example.android.architecture.blueprints.todoapp;

import androidx.lifecycle.LiveData;
import org.junit.Assert;

public class TestUtil {
    public static void assertLiveDataEventTriggered(
        LiveData<Event<String>> liveData,
        String taskId
    ) {
        Event<String> value = LiveDataTestUtil.getValue(liveData);
        Assert.assertEquals(value.getContentIfNotHandled(), taskId);
    }

    public static void assertSnackbarMessage(LiveData<Event<Integer>> snackbarLiveData, int messageId) {
        Event<Integer> value = LiveDataTestUtil.getValue(snackbarLiveData);
        Assert.assertEquals(value.getContentIfNotHandled(), messageId);
    }
}