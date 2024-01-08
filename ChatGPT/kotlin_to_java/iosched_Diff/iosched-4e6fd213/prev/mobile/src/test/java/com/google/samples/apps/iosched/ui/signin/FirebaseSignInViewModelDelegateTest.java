package com.google.samples.apps.iosched.ui.signin;

import android.net.Uri;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.androidtest.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.domain.prefs.NotificationsPrefIsShownUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.fakes.FakePreferenceStorage;
import com.google.samples.apps.iosched.ui.schedule.FakeObserveUserAuthStateUseCase;
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class FirebaseSignInViewModelDelegateTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testSignedOut() {
        FirebaseSignInViewModelDelegate subject = new FirebaseSignInViewModelDelegate(
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(null),
                        new Result.Success<>(false)
                ),
                createNotificationsPrefIsShownUseCase()
        );

        Result.Success<AuthenticatedUserInfo> currentFirebaseUser = (Result.Success<AuthenticatedUserInfo>) LiveDataTestUtil.getValue(
                subject.getCurrentFirebaseUser()
        );
        TestCase.assertEquals(
                null,
                currentFirebaseUser.getData().getUid()
        );
        TestCase.assertEquals(
                null,
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        Assert.assertFalse(subject.isSignedIn());
    }

    @Test
    public void testSignedInRegistered() {

        AuthenticatedUserInfoBasic user = mock(AuthenticatedUserInfoBasic.class);
        doReturn("123").when(user).getUid();
        doReturn(mock(Uri.class)).when(user).getPhotoUrl();
        doReturn(true).when(user).isSignedIn();

        FirebaseSignInViewModelDelegate subject = new FirebaseSignInViewModelDelegate(
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(user),
                        new Result.Success<>(true)
                ),
                createNotificationsPrefIsShownUseCase()
        );

        TestCase.assertEquals(
                user.getUid(),
                ((Result.Success<AuthenticatedUserInfo>) LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser())).getData().getUid()
        );
        TestCase.assertEquals(
                user.getPhotoUrl(),
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        Assert.assertTrue(subject.isSignedIn());
        Assert.assertTrue(subject.isRegistered());
    }

    @Test
    public void testSignedInNotRegistered() {

        AuthenticatedUserInfoBasic user = mock(AuthenticatedUserInfoBasic.class);
        doReturn("123").when(user).getUid();
        doReturn(mock(Uri.class)).when(user).getPhotoUrl();
        doReturn(true).when(user).isSignedIn();

        FirebaseSignInViewModelDelegate subject = new FirebaseSignInViewModelDelegate(
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(user),
                        new Result.Success<>(false)
                ),
                createNotificationsPrefIsShownUseCase()
        );

        TestCase.assertEquals(
                user.getUid(),
                ((Result.Success<AuthenticatedUserInfo>) LiveDataTestUtil.getValue(subject.getCurrentFirebaseUser())).getData().getUid()
        );
        TestCase.assertEquals(
                user.getPhotoUrl(),
                LiveDataTestUtil.getValue(subject.getCurrentUserImageUri())
        );
        Assert.assertTrue(subject.isSignedIn());
        Assert.assertFalse(subject.isRegistered());
    }

    @Test
    public void testPostSignIn() {
        FirebaseSignInViewModelDelegate subject = new FirebaseSignInViewModelDelegate(
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(null),
                        new Result.Success<>(false)
                ),
                createNotificationsPrefIsShownUseCase()
        );

        subject.emitSignInRequest();

        TestCase.assertEquals(
                LiveDataTestUtil.getValue(subject.getPerformSignInEvent()).peekContent(),
                SignInEvent.RequestSignIn
        );
    }

    @Test
    public void testPostSignOut() {
        FirebaseSignInViewModelDelegate subject = new FirebaseSignInViewModelDelegate(
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(null),
                        new Result.Success<>(false)
                ),
                createNotificationsPrefIsShownUseCase()
        );

        subject.emitSignOutRequest();

        TestCase.assertEquals(
                LiveDataTestUtil.getValue(subject.getPerformSignInEvent()).peekContent(),
                SignInEvent.RequestSignOut
        );
    }

    private NotificationsPrefIsShownUseCase createNotificationsPrefIsShownUseCase() {
        return new NotificationsPrefIsShownUseCase(new FakePreferenceStorage());
    }
}