package io.plaidapp.core.designernews.login.ui;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.login.data.LoginRepository;
import io.plaidapp.core.designernews.util.LiveDataTestUtil;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.Mockito.mock;
import java.io.IOException;

public class LoginViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private String username = "Plaid";
    private String pass = "design";

    private LoginRepository loginRepo = mock(LoginRepository.class);
    private LoginViewModel viewModel =
            new LoginViewModel(loginRepo, provideFakeCoroutinesContextProvider());

    @Test
    public void successEmitted_whenUserLoggedInSuccessfully() throws InterruptedException {
        User user = new User(
                3,
                "Plaida",
                "Plaidich",
                "Plaida Plaidich",
                "www"
        );
        LoginUiModel uiModel = new LoginUiModel("plaida plaidich", "www");
        Mockito.when(loginRepo.login(username, pass)).thenReturn(Result.Success(user));

        viewModel.login(username, pass);

        Result<LoginUiModel> event = LiveDataTestUtil.getValue(viewModel.getUiState());
        Assert.assertEquals(Result.Success(uiModel), event);
    }

    @Test
    public void errorEmitted_whenUserLogInFailed() throws InterruptedException {
        Mockito.when(loginRepo.login(username, pass)).thenReturn(Result.Error(new IOException("Login error")));

        viewModel.login(username, pass);

        Result<LoginUiModel> event = LiveDataTestUtil.getValue(viewModel.getUiState());
        Assert.assertTrue(event instanceof Result.Error);
    }

    @Test
    public void loadingIgnored_whenLoadingEmitted() throws InterruptedException {
        Mockito.when(loginRepo.login(username, pass)).thenReturn(Result.Loading);

        viewModel.login(username, pass);

        Result<LoginUiModel> event = LiveDataTestUtil.getValue(viewModel.getUiState());
        Assert.assertEquals(Result.Loading, event);
    }
}