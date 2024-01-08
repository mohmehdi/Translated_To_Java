package com.google.samples.apps.iosched.ui.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.net.Uri;
import com.google.firebase.auth.UserInfo;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.fakes.FakeAuthenticatedUser;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogin;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogout;
import com.nhaarman.mockito_kotlin.mock;
import junit.framework.TestCase;
import org.junit.Rule;
import org.junit.Test;

public class DefaultLoginViewModelPluginTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testLoggedOut() {
        DefaultLoginViewModelPlugin subject = new DefaultLoginViewModelPlugin(new FakeAuthenticatedUser(null));

        TestCase.assertEquals(
                null,
                LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser())
        );
        TestCase.assertEquals(
                null,
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        TestCase.assertFalse(subject.isLoggedIn());
    }

    @Test
    public void testLoggedIn() {

        DefaultLoginViewModelPlugin subject =
                new DefaultLoginViewModelPlugin(new FakeAuthenticatedUser(FakeAuthenticatedUserInfo));
        TestCase.assertEquals(
                FakeAuthenticatedUserInfo,
                ((Result.Success) LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser())).getData()
        );
        TestCase.assertEquals(
                FakeAuthenticatedUserInfo.getPhotoUrl(),
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        TestCase.assertTrue(subject.isLoggedIn());
    }

    @Test
    public void testPostLogin() {
        DefaultLoginViewModelPlugin subject = new DefaultLoginViewModelPlugin(new FakeAuthenticatedUser(null));

        subject.emitLoginRequest();

        TestCase.assertEquals(
                LiveDataTestUtil.getValue(subject.getPerformLoginEvent()).peekContent(), RequestLogin
        );
    }

    @Test
    public void testPostLogout() {
        DefaultLoginViewModelPlugin subject = new DefaultLoginViewModelPlugin(new FakeAuthenticatedUser(null));

        subject.emitLogoutRequest();

        TestCase.assertEquals(
                LiveDataTestUtil.getValue(subject.getPerformLoginEvent()).peekContent(), RequestLogout
        );
    }
}

class FakeAuthenticatedUserInfo implements AuthenticatedUserInfo {

    private Uri photo = mock(Uri.class);
    private static final String testUid = "testuid";

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public String getEmail() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public MutableList<? extends UserInfo> getProviderData() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Boolean isAnonymous() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getPhoneNumber() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getUid() {
        return testUid;
    }

    @Override
    public Boolean isEmailVerified() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getDisplayName() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Uri getPhotoUrl() {
        return photo;
    }

    @Override
    public MutableList<String> getProviders() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getProviderId() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Long getLastSignInTimestamp() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Long getCreationTimestamp() {
        throw new UnsupportedOperationException("not implemented");
    }
}