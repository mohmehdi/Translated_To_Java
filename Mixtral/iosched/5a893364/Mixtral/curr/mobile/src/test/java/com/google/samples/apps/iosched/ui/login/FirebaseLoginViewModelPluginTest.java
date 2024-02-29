

package com.google.samples.apps.iosched.ui.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.ui.schedule.FakeObserveUserAuthStateUseCase;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FirebaseLoginViewModelPluginTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Mock
    private FakeObserveUserAuthStateUseCase fakeObserveUserAuthStateUseCase;

    private FirebaseLoginViewModelPlugin subject;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        subject = new FirebaseLoginViewModelPlugin(fakeObserveUserAuthStateUseCase);
    }

    @Test
    public void testLoggedOut() {
        Result<AuthenticatedUserInfo> userResult = Result.Success(null);
        Result<Boolean> isRegisteredResult = Result.Success(false);
        fakeObserveUserAuthStateUseCase.setUser(userResult);
        fakeObserveUserAuthStateUseCase.setIsRegistered(isRegisteredResult);

        try {
            AuthenticatedUserInfo currentFirebaseUser = LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser());
            assertEquals(
                    null,
                    currentFirebaseUser.getUid()
            );
            assertEquals(
                    null,
                    LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
            );
            assertFalse(subject.isLoggedIn());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testLoggedInRegistered() {
        AuthenticatedUserInfoBasic user = mock(AuthenticatedUserInfoBasic.class);
        Uri photoUrl = mock(Uri.class);
        when(user.getUid()).thenReturn("123");
        when(user.getPhotoUrl()).thenReturn(photoUrl);
        when(user.isLoggedIn()).thenReturn(true);

        Result<AuthenticatedUserInfo> userResult = Result.Success(user);
        Result<Boolean> isRegisteredResult = Result.Success(true);
        fakeObserveUserAuthStateUseCase.setUser(userResult);
        fakeObserveUserAuthStateUseCase.setIsRegistered(isRegisteredResult);

        subject.setFakeObserveUserAuthStateUseCase(fakeObserveUserAuthStateUseCase);

        assertEquals(
                user.getUid(),
                LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser()).getData().getUid()
        );
        assertEquals(
                photoUrl,
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        assertTrue(subject.isLoggedIn());
        assertTrue(subject.isRegistered());
    }

    @Test
    public void testLoggedInNotRegistered() {
        AuthenticatedUserInfoBasic user = mock(AuthenticatedUserInfoBasic.class);
        Uri photoUrl = mock(Uri.class);
        when(user.getUid()).thenReturn("123");
        when(user.getPhotoUrl()).thenReturn(photoUrl);
        when(user.isLoggedIn()).thenReturn(true);

        Result<AuthenticatedUserInfo> userResult = Result.Success(user);
        Result<Boolean> isRegisteredResult = Result.Success(false);
        fakeObserveUserAuthStateUseCase.setUser(userResult);
        fakeObserveUserAuthStateUseCase.setIsRegistered(isRegisteredResult);

        subject.setFakeObserveUserAuthStateUseCase(fakeObserveUserAuthStateUseCase);

        assertEquals(
                user.getUid(),
                LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser()).getData().getUid()
        );
        assertEquals(
                photoUrl,
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        assertTrue(subject.isLoggedIn());
        assertFalse(subject.isRegistered());
    }

    @Test
    public void testPostLogin() {
        subject.emitLoginRequest();

        assertEquals(
                LiveDataTestUtil.getValue(subject.getPerformLoginEvent()).peekContent(),
                LoginEvent.RequestLogin
        );
    }

    @Test
    public void testPostLogout() {
        subject.emitLogoutRequest();

        assertEquals(
                LiveDataTestUtil.getValue(subject.getPerformLoginEvent()).peekContent(),
                LoginEvent.RequestLogout
        );
    }
}