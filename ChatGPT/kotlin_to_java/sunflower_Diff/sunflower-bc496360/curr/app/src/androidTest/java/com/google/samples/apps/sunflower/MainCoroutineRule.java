
package com.google.samples.apps.sunflower;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.test.TestCoroutineDispatcher;
import kotlinx.coroutines.test.TestCoroutineScope;
import kotlinx.coroutines.test.resetMain;
import kotlinx.coroutines.test.setMain;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class MainCoroutineRule extends TestWatcher {

    private TestCoroutineDispatcher testDispatcher = new TestCoroutineDispatcher();

    @Override
    protected void starting(Description description) {
        super.starting(description);
        Dispatchers.setMain(testDispatcher);
    }

    @Override
    protected void finished(Description description) {
        super.finished(description);
        Dispatchers.resetMain();
        testDispatcher.cleanupTestCoroutines();
    }

    public TestCoroutineScope runBlockingTest(suspend () -> Unit block) {
        return testDispatcher.runBlockingTest(block);
    }
}
