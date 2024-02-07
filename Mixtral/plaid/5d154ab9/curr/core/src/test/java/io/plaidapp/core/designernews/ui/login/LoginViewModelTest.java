package io.plaidapp.core.designernews.ui.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.ui.login.LoginViewModel.LoginUiModel;
import io.plaidapp.core.designernews.util.LiveDataTestUtil;
import io.plaidapp.test.shared.CoroutinesContextProvider;
import io.plaidapp.test.shared.FakeCoroutinesContextProvider;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LoginViewModelTest {

  @Rule
  public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

  private String username = "Plaid";
  private String pass = "design";

  @Mock
  private LoginRepository loginRepo;

  private LoginViewModel viewModel;

  private CoroutinesContextProvider coroutinesContextProvider = new FakeCoroutinesContextProvider();

  @Test
  public void successEmitted_whenUserLoggedInSuccessfully() throws Exception {
    User user = new User(3, "Plaida", "Plaidich", "Plaida Plaidich", "www");
    LoginUiModel uiModel = new LoginUiModel("plaida plaidich", "www");
    Mockito
      .when(loginRepo.login(username, pass))
      .thenReturn(Result.Success(user));

    viewModel.login(username, pass);

    Result event = LiveDataTestUtil.getValue(viewModel.uiState);
    Assert.assertEquals(Result.Success(uiModel), event);
  }

  @Test
  public void errorEmitted_whenUserLogInFailed() throws Exception {
    Mockito
      .when(loginRepo.login(username, pass))
      .thenReturn(Result.Error(new IOException("Login error")));

    viewModel.login(username, pass);

    Result event = LiveDataTestUtil.getValue(viewModel.uiState);
    Assert.assertTrue(event instanceof Result.Error);
  }

  @Test
  public void loadingIgnored_whenLoadingEmitted() throws Exception {
    Mockito.when(loginRepo.login(username, pass)).thenReturn(Result.Loading);

    viewModel.login(username, pass);

    Result event = LiveDataTestUtil.getValue(viewModel.uiState);
    Assert.assertEquals(Result.Loading, event);
  }
}
