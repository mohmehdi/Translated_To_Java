package io.plaidapp.core.designernews.login.ui;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.support.annotation.NonNull;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.login.data.LoginRepository;
import io.plaidapp.core.designernews.login.ui.LoginUiModel;
import io.plaidapp.core.designernews.util.LiveDataTestUtil;
import io.plaidapp.test.shared.CoroutinesContextProvider;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class LoginViewModelTest {

  @Rule
  public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  private String username = "Plaid";
  private String pass = "design";

  private LoginRepository loginRepo;
  private LoginViewModel viewModel;

  @Test
  public void successEmitted_whenUserLoggedInSuccessfully() throws Exception {
    User user = new User(3, "Plaida", "Plaidich", "Plaida Plaidich", "www");
    LoginUiModel uiModel = new LoginUiModel("plaida plaidich", "www");
    Mockito
      .when(loginRepo.login(username, pass))
      .thenReturn(Result.Success(user));

    viewModel.login(username, pass);

    Object event = LiveDataTestUtil.getValue(viewModel.uiState);
    Assert.assertEquals(Result.Success(uiModel), event);
  }

  @Test
  public void errorEmitted_whenUserLogInFailed() throws Exception {
    Mockito
      .when(loginRepo.login(username, pass))
      .thenReturn(Result.Error(new IOException("Login error")));

    viewModel.login(username, pass);

    Object event = LiveDataTestUtil.getValue(viewModel.uiState);
    Assert.assertTrue(event instanceof Result.Error);
  }

  @Test
  public void loadingIgnored_whenLoadingEmitted() throws Exception {
    Mockito.when(loginRepo.login(username, pass)).thenReturn(Result.Loading);

    viewModel.login(username, pass);

    Object event = LiveDataTestUtil.getValue(viewModel.uiState);
    Assert.assertEquals(Result.Loading, event);
  }
}
