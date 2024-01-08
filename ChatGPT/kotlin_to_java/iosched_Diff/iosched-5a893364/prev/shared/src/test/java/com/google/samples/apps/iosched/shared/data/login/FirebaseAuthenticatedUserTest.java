package com.google.samples.apps.iosched.shared.data.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth.AuthStateListener;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.nhaarman.mockito_kotlin.argumentCaptor;
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.verify;
import org.hamcrest.MatcherAssert.assertThat;
import org.hamcrest.Matchers;
import org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

public class FirebaseAuthenticatedUserTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private String expected = "a user token";

    private GetTokenResult tokenResult = new GetTokenResult(expected);

    private Task<GetTokenResult> mockSuccessfulTask = mock(Task.class);
    private Task<GetTokenResult> mockUnsuccessfulTask = mock(Task.class);

    private FirebaseUser mockNonAnonymousUser = mock(FirebaseUser.class);
    private FirebaseUser mockNonAnonymousUserUnsuccessfulTaskUser = mock(FirebaseUser.class);
    private FirebaseUser mockAnonymousUser = mock(FirebaseUser.class);

    private FirebaseAuth mockedNonAnonymousFirebaseAuth = mock(FirebaseAuth.class);
    private FirebaseAuth mockedNonAnonymousUnsuccessfulFirebaseAuth = mock(FirebaseAuth.class);
    private FirebaseAuth mockedAnonymousFirebaseAuth = mock(FirebaseAuth.class);

    @Test
    public void loggedInUser() {
        FirebaseAuthenticatedUser subject = new FirebaseAuthenticatedUser(mockedNonAnonymousFirebaseAuth);

        argumentCaptor<AuthStateListener> authStateListenerCaptor = argumentCaptor.forClass(AuthStateListener.class);
        verify(mockedNonAnonymousFirebaseAuth).addAuthStateListener(authStateListenerCaptor.capture());
        authStateListenerCaptor.getValue().onAuthStateChanged(mockedNonAnonymousFirebaseAuth);

        argumentCaptor<OnCompleteListener<GetTokenResult>> onCompleteListenerCaptor = argumentCaptor.forClass(OnCompleteListener.class);
        verify(mockSuccessfulTask).addOnCompleteListener(onCompleteListenerCaptor.capture());
        onCompleteListenerCaptor.getValue().onComplete(mockSuccessfulTask);

        Result<FirebaseUser> currentUser = LiveDataTestUtil.getValue(subject.getCurrentUser());
        assertThat(
                ((Result.Success<FirebaseUser>) currentUser).getData().isAnonymous(),
                Matchers.is(false));

        Result<String> token = LiveDataTestUtil.getValue(subject.getToken());
        assertThat(((Result.Success<String>) token).getData(), Matchers.is(expected));
    }

    @Test
    public void anonymousUser() {
        FirebaseAuthenticatedUser subject = new FirebaseAuthenticatedUser(mockedAnonymousFirebaseAuth);

        argumentCaptor<AuthStateListener> authStateListenerCaptor = argumentCaptor.forClass(AuthStateListener.class);
        verify(mockedAnonymousFirebaseAuth).addAuthStateListener(authStateListenerCaptor.capture());
        authStateListenerCaptor.getValue().onAuthStateChanged(mockedAnonymousFirebaseAuth);

        argumentCaptor<OnCompleteListener<GetTokenResult>> onCompleteListenerCaptor = argumentCaptor.forClass(OnCompleteListener.class);
        verify(mockSuccessfulTask).addOnCompleteListener(onCompleteListenerCaptor.capture());
        onCompleteListenerCaptor.getValue().onComplete(mockSuccessfulTask);

        Result<FirebaseUser> value = LiveDataTestUtil.getValue(subject.getCurrentUser());
        assertThat(
                ((Result.Success<FirebaseUser>) value).getData().isAnonymous(),
                Matchers.is(true));

        Result<String> token = LiveDataTestUtil.getValue(subject.getToken());
        assertThat(((Result.Success<String>) token).getData(), Matchers.is(expected));
    }

    @Test
    public void errorGettingIdToken() {
        FirebaseAuthenticatedUser subject = new FirebaseAuthenticatedUser(mockedNonAnonymousUnsuccessfulFirebaseAuth);

        argumentCaptor<AuthStateListener> authStateListenerCaptor = argumentCaptor.forClass(AuthStateListener.class);
        verify(mockedNonAnonymousUnsuccessfulFirebaseAuth).addAuthStateListener(authStateListenerCaptor.capture());
        authStateListenerCaptor.getValue().onAuthStateChanged(mockedNonAnonymousUnsuccessfulFirebaseAuth);

        argumentCaptor<OnCompleteListener<GetTokenResult>> onCompleteListenerCaptor = argumentCaptor.forClass(OnCompleteListener.class);
        verify(mockUnsuccessfulTask).addOnCompleteListener(onCompleteListenerCaptor.capture());
        onCompleteListenerCaptor.getValue().onComplete(mockUnsuccessfulTask);

        Result<FirebaseUser> value = LiveDataTestUtil.getValue(subject.getCurrentUser());
        assertThat(
                ((Result.Success<FirebaseUser>) value).getData().isAnonymous(),
                Matchers.is(false));

        assertTrue(LiveDataTestUtil.getValue(subject.getToken()) instanceof Result.Error);
    }
}