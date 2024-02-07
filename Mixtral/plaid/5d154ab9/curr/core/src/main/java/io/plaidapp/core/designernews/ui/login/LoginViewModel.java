public class LoginViewModel extends ViewModel {

  private final LoginRepository loginRepository;
  private final CoroutinesContextProvider contextProvider;

  private Job currentJob;

  private final MutableLiveData<Result<LoginUiModel>> _uiState = new MutableLiveData<>();
  public final LiveData<Result<LoginUiModel>> uiState = _uiState;

  public LoginViewModel(
    LoginRepository loginRepository,
    CoroutinesContextProvider contextProvider
  ) {
    this.loginRepository = loginRepository;
    this.contextProvider = contextProvider;
  }

  public void login(String username, String password) {
    if (currentJob != null && currentJob.isActive()) {
      return;
    }
    currentJob = launchLogin(username, password);
  }

  private Continuation<? super Unit> launchLogin(
    String username,
    String password
  ) {
    return new Continuation<Unit>() {
      @Override
      public Object resumeWith(Object result) throws Throwable {
        return launch(contextProvider.io).resumeWith(result);
      }

      @Override
      public void resume(Object result) {
        _uiState.postValue(Result.Loading);
        Object resultObj = result;
        if (resultObj instanceof Result) {
          Result<?> resultResp = (Result<?>) resultObj;
          switch (resultResp.get()) {
            case SUCCESS:
              Result<?> successResp = (Result.Success<?>) resultResp;
              LoginUiModel uiModel = new LoginUiModel(
                ((User) successResp.getData()).getDisplayName().toLowerCase(),
                ((User) successResp.getData()).getPortraitUrl()
              );
              _uiState.postValue(new Result.Success<>(uiModel));
              break;
            case ERROR:
              _uiState.postValue((Result<?>) resultResp);
              break;
            case LOADING:
              // Handle loading case if needed
              break;
          }
        }
      }
    };
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    if (currentJob != null) {
      currentJob.cancel();
    }
  }
}

public class LoginUiModel {

  private final String displayName;
  private final String portraitUrl;

  public LoginUiModel(String displayName, String portraitUrl) {
    this.displayName = displayName;
    this.portraitUrl = portraitUrl;
  }

}
