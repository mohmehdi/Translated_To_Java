

package com.google.samples.apps.iosched.ui.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.net.Uri;
import android.support.test.rule.SyncTaskExecutorRule;
import com.google.firebase.auth.UserInfo;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.fakes.FakeAuthenticatedUser;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogin;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogout;
import com.nhaarman.mockito_kotlin.mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class DefaultLoginViewModelPluginTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    private DefaultLoginViewModelPlugin subject;

    @Before
    public void setup() {
        subject = new DefaultLoginViewModelPlugin(new FakeAuthenticatedUser(null));
    }

    @Test
    public void testLoggedOut() {
        assertNull(LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser()));
        assertNull(LiveDataTestUtil.getValue(subject.getCurrentUserImageUri()));
        assertFalse(subject.isLoggedIn());
    }

    @Test
    public void testLoggedIn() throws ExecutionException, InterruptedException {
        subject = new DefaultLoginViewModelPlugin(new FakeAuthenticatedUser(new FakeAuthenticatedUserInfo()));
        Result result = LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser());
        assertEquals(FakeAuthenticatedUserInfo.class, result.getData().getClass());
        assertEquals(FakeAuthenticatedUserInfo.getPhotoUrl(), LiveDataTestUtil.getValue(subject.getCurrentUserImageUri()));
        assertTrue(subject.isLoggedIn());
    }

    @Test
    public void testPostLogin() {
        subject.emitLoginRequest();
        assertEquals(RequestLogin.class, LiveDataTestUtil.getValue(subject.getPerformLoginEvent()).peekContent().getClass());
    }

    @Test
    public void testPostLogout() {
        subject.emitLogoutRequest();
        assertEquals(RequestLogout.class, LiveDataTestUtil.getValue(subject.getPerformLoginEvent()).peekContent().getClass());
    }
}

class FakeAuthenticatedUserInfo implements AuthenticatedUserInfo {

    private static final Uri photo = mock(Uri.class);
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
    public List<UserInfo> getProviderData() {
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
    public List<String> getProviders() {
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

    static Uri getPhotoUrl() {
        return photo;
    }
}