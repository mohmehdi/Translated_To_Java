
package com.google.samples.apps.iosched.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.androidtest.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.fakes.FakeSignInViewModelDelegate;
import com.google.samples.apps.iosched.test.util.fakes.FakeThemedActivityDelegate;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class MainActivityViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    private MainActivityViewModel createMainActivityViewModel(SignInViewModelDelegate signInViewModelDelegate, ThemedActivityDelegate themedActivityDelegate) {
        return new MainActivityViewModel(signInViewModelDelegate, themedActivityDelegate);
    }

    @Test
    public void notLoggedIn_profileClicked_showsSignInDialog() {
        FakeSignInViewModelDelegate signInViewModelDelegate = new FakeSignInViewModelDelegate();
        signInViewModelDelegate.injectIsSignedIn = false;

        MainActivityViewModel viewModel = createMainActivityViewModel(signInViewModelDelegate, new FakeThemedActivityDelegate());

        viewModel.onProfileClicked();

        Matcher<Object> notNullValueMatcher = Matchers.notNullValue();
        Assert.assertThat(LiveDataTestUtil.getValue(viewModel.navigateToSignInDialogAction), is(notNullValueMatcher));
    }

    @Test
    public void loggedIn_profileClicked_showsSignOutDialog() {
        FakeSignInViewModelDelegate signInViewModelDelegate = new FakeSignInViewModelDelegate();
        signInViewModelDelegate.injectIsSignedIn = true;

        MainActivityViewModel viewModel = createMainActivityViewModel(signInViewModelDelegate, new FakeThemedActivityDelegate());

        viewModel.onProfileClicked();

        Matcher<Object> notNullValueMatcher = Matchers.notNullValue();
        Assert.assertThat(LiveDataTestUtil.getValue(viewModel.navigateToSignOutDialogAction), is(notNullValueMatcher));
    }
}