
package com.example.android.architecture.blueprints.todoapp;

import androidx.lifecycle.LiveData;
import org.junit.Assert;

public class Utils {

    public static void assertLiveDataEventTriggered(
            LiveData<Event<String>> liveData,
            String taskId
    ) {
        Event<String> value = liveData.awaitNextValue();
        Assert.assertEquals(value.getContentIfNotHandled(), taskId);
    }

    public static void assertSnackbarMessage(
            LiveData<Event<Integer>> snackbarLiveData,
            int messageId
    ) {
        Event<Integer> value = snackbarLiveData.awaitNextValue();
        Assert.assertEquals(value.getContentIfNotHandled(), messageId);
    }
}
