
package com.google.samples.apps.sunflower.utilities;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import dagger.hilt.android.testing.HiltTestApplication;

public class MainTestRunner extends AndroidJUnitRunner {

    @Override
    public Application newApplication(ClassLoader cl, String name, Context context) throws IllegalAccessException, ClassNotFoundException, InstantiationException {
        return super.newApplication(cl, HiltTestApplication.class.getName(), context);
    }
}
