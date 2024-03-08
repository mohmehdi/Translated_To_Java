package com.google.samples.apps.iosched.ui.login;

import android.net.Uri;
import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.ui.schedule.FakeObserveUserAuthStateUseCase;
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;
import junit.framework.TestCase;
import org.junit.Rule;
import org.junit.Test;

public class FirebaseLoginViewModelPluginTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testLoggedOut() {
        FirebaseLoginViewModelPlugin subject = new FirebaseLoginViewModelPlugin(new FakeObserveUserAuthStateUseCase(
                Result.Success(null),
                Result.Success(false)));

        Result.Success<AuthenticatedUserInfo> currentFirebaseUser = (Result.Success<AuthenticatedUserInfo>) LiveDataTestUtil.getValue(subject.currentFirebaseUser);
        TestCase.assertEquals(
                null,
                currentFirebaseUser.getData().getUid()
        );
        TestCase.assertEquals(
                null,
                LiveDataTestUtil.getValue(subject.currentUserImageUri)
        );
        TestCase.assertFalse(subject.isLoggedIn());
    }

    @Test
    public void testLoggedInRegistered() {

        AuthenticatedUserInfoBasic user = mock(AuthenticatedUserInfoBasic.class);
        doReturn("123").`when`(user).getUid();
        doReturn(mock(Uri.class)).`when`(user).getPhotoUrl();
        doReturn(true).`when`(user).isLoggedIn();
        FirebaseLoginViewModelPlugin subject = new FirebaseLoginViewModelPlugin(new FakeObserveUserAuthStateUseCase(
                Result.Success(user),
                Result.Success(true)));

        TestCase.assertEquals(
                user.getUid(),
                ((Result.Success) LiveDataTestUtil.getValue(subject.currentFirebaseUser)).getData().getUid()
        );
        TestCase.assertEquals(
                user.getPhotoUrl(),
                LiveDataTestUtil.getValue(subject.currentUserImageUri));
        TestCase.assertTrue(subject.isLoggedIn());
        TestCase.assertTrue(subject.isRegistered());
    }

    @Test
    public void testLoggedInNotRegistered() {

        AuthenticatedUserInfoBasic user = mock(AuthenticatedUserInfoBasic.class);
        doReturn("123").`when`(user).getUid();
        doReturn(mock(Uri.class)).`when`(user).getPhotoUrl();
        doReturn(true).`when`(user).isLoggedIn();
        FirebaseLoginViewModelPlugin subject = new FirebaseLoginViewModelPlugin(new FakeObserveUserAuthStateUseCase(
                Result.Success(user),
                Result.Success(false)));

        TestCase.assertEquals(
                user.getUid(),
                ((Result.Success) LiveDataTestUtil.getValue(subject.currentFirebaseUser)).getData().getUid()
        );
        TestCase.assertEquals(
                user.getPhotoUrl(),
                LiveDataTestUtil.getValue(subject.currentUserImageUri));
        TestCase.assertTrue(subject.isLoggedIn());
        TestCase.assertFalse(subject.isRegistered());
    }

    @Test
    public void testPostLogin() {
        FirebaseLoginViewModelPlugin subject = new FirebaseLoginViewModelPlugin(new FakeObserveUserAuthStateUseCase(
                Result.Success(null),
                Result.Success(false)));

        subject.emitLoginRequest();

        TestCase.assertEquals(
                LiveDataTestUtil.getValue(subject.performLoginEvent).peekContent(),
                LoginEvent.RequestLogin
        );
    }

    @Test
    public void testPostLogout() {
        FirebaseLoginViewModelPlugin subject = new FirebaseLoginViewModelPlugin(new FakeObserveUserAuthStateUseCase(
                Result.Success(null),
                Result.Success(false)));

        subject.emitLogoutRequest();

        TestCase.assertEquals(
                LiveDataTestUtil.getValue(subject.performLoginEvent).peekContent(),
                LoginEvent.RequestLogout
        );
    }
}