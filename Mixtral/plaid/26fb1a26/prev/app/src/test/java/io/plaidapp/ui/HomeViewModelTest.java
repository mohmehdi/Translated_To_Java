




package io.plaidapp.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.nhaarman.mockitokotlin2.ArgumentCaptor;
import com.nhaarman.mockitokotlin2.Captor;
import com.nhaarman.mockitokotlin2.MockitoAnnotations;
import com.nhaarman.mockitokotlin2.Timeout;
import com.nhaarman.mockitokotlin2.verify;
import com.nhaarman.mockitokotlin2.whenever;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import io.plaidapp.core.data.DataLoadingSubject;
import io.plaidapp.core.data.DataManager;
import io.plaidapp.core.data.OnDataLoadedCallback;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.data.prefs.SourcesRepository;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.feed.FeedProgressUiModel;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import io.plaidapp.core.ui.filter.SourcesHighlightUiModel;
import io.plaidapp.designerNewsSource;
import io.plaidapp.designerNewsSourceUiModel;
import io.plaidapp.dribbbleSource;
import io.plaidapp.post;
import io.plaidapp.shot;
import io.plaidapp.story;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

public class HomeViewModelTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final int COLUMNS = 2;
    @Mock
    private DataManager dataManager;
    @Mock
    private LoginRepository loginRepository;
    @Mock
    private SourcesRepository sourcesRepository;

    @Captor
    private ArgumentCaptor<FiltersChangedCallback> filtersChangedCallback;

    @Captor
    private ArgumentCaptor<DataLoadingSubject.DataLoadingCallbacks> dataLoadingCallback;

    @Captor
    private ArgumentCaptor<OnDataLoadedCallback<List<PlaidItem>>> dataLoadedCallback;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void logoutFromDesignerNews() {

        HomeViewModel homeViewModel = createViewModel();

        homeViewModel.logoutFromDesignerNews();

        verify(loginRepository).logout();
    }

    @Test
    public void isDesignerNewsLoggedIn() {

        HomeViewModel homeViewModel = createViewModel();

        boolean loginStatus = false;
        whenever(loginRepository.isLoggedIn).thenReturn(loginStatus);

        boolean isLoggedIn = homeViewModel.isDesignerNewsUserLoggedIn();

        Assert.assertEquals(loginStatus, isLoggedIn);
    }

    @Test
    public void addSources_blankQuery() throws ExecutionException, InterruptedException {

        HomeViewModel homeViewModel = createViewModel();

        homeViewModel.addSources("", true, false);

        verify(sourcesRepository, never()).addOrMarkActiveSources(anyList());
    }

    @Test
    public void addSources_Dribbble() throws ExecutionException, InterruptedException {

        HomeViewModel homeViewModel = createViewModel();

        homeViewModel.addSources(dribbbleSource.query, true, false);

        List<SourceItem> expected = Arrays.asList(dribbbleSource);
        verify(sourcesRepository).addOrMarkActiveSources(expected);
    }

    @Test
    public void addSources_DesignerNews() throws ExecutionException, InterruptedException {

        HomeViewModel homeViewModel = createViewModel();

        homeViewModel.addSources(
                designerNewsSource.query,
                false,
                true
        );

        List<SourceItem> expected = Arrays.asList(designerNewsSource);
        verify(sourcesRepository).addOrMarkActiveSources(expected);
    }

    @Test
    public void addSources_DribbbleDesignerNews() throws ExecutionException, InterruptedException {

        HomeViewModel homeViewModel = createViewModel();

        homeViewModel.addSources("query", true, true);

        List<SourceItem> expected = Arrays.asList(
                new DribbbleSourceItem("query", true),
                new DesignerNewsSearchSource("query", true)
        );
        verify(sourcesRepository).addOrMarkActiveSources(expected);
    }

    @Test
    public void filtersUpdated_newSources() throws ExecutionException, InterruptedException {

        HomeViewModel homeViewModel = createViewModel();
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );

        filtersChangedCallback.getValue().onFiltersUpdated(Arrays.asList(designerNewsSource, dribbbleSource));

        LiveData<SourcesHighlightUiModel> sources = homeViewModel.getSources();
        SourcesHighlightUiModel sourcesHighlightUiModel = sources.getValue();

        SourcesHighlightUiModel expectedSourcesHighlightUiModel = new SourcesHighlightUiModel(
                Arrays.asList(0, 1),
                1
        );
        Assert.assertEquals(expectedSourcesHighlightUiModel, sourcesHighlightUiModel);

        Assert.assertEquals(2, sources.getValue().getSourceUiModels().size());
    }

    @Test
    public void filtersUpdated_oneNewSource() throws ExecutionException, InterruptedException {

        List<SourceItem> sources = new MutableList<SourceItem>() {{
            add(designerNewsSource);
        }};
        HomeViewModel homeViewModel = createViewModel(sources);
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );

        sources.add(dribbbleSource);
        filtersChangedCallback.getValue().onFiltersUpdated(sources);

        LiveData<SourcesUiModel> sourcesUiModel = homeViewModel.getSources();

        SourcesHighlightUiModel expectedSourcesHighlightUiModel = new SourcesHighlightUiModel(
                Arrays.asList(1),
                1
        );
        Assert.assertEquals(expectedSourcesHighlightUiModel, sourcesUiModel.getValue().getHighlightSources());

        Assert.assertEquals(2, sourcesUiModel.getValue().getSourceUiModels().size());
    }

    @Test
    public void sourceClicked_changesSourceActiveState() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );
        filtersChangedCallback.getValue().onFiltersUpdated(Arrays.asList(designerNewsSource));

        LiveData<SourcesUiModel> sources = homeViewModel.getSources();
        SourcesUiModel uiSource = sources.getValue();

        uiSource.getSourceUiModels().get(0).onSourceClicked(designerNewsSourceUiModel);

        verify(sourcesRepository).changeSourceActiveState(designerNewsSource.getKey());
    }

    @Test
    public void sourceRemoved_swipeDismissable() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );
        filtersChangedCallback.getValue().onFiltersUpdated(Arrays.asList(designerNewsSource));

        LiveData<SourcesUiModel> sources = homeViewModel.getSources();
        SourcesUiModel uiSource = sources.getValue();

        uiSource.getSourceUiModels().get(0).onSourceDismissed(designerNewsSourceUiModel.copy(isSwipeDismissable
                = true));

        verify(sourcesRepository).removeSource(designerNewsSource.getKey());
    }

    @Test
    public void sourceRemoved_notSwipeDismissable() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );
        filtersChangedCallback.getValue().onFiltersUpdated(Arrays.asList(designerNewsSource));

        LiveData<SourcesUiModel> sources = homeViewModel.getSources();
        SourcesUiModel uiSource = sources.getValue();

        uiSource.getSourceUiModels().get(0).onSourceDismissed(designerNewsSourceUiModel.copy(isSwipeDismissable
                = false));

        verify(sourcesRepository, never()).removeSource(designerNewsSource.getKey());
    }

    @Test
    public void filtersRemoved() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModelWithFeedData(Arrays.asList(post, shot, story));
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );

        filtersChangedCallback.getValue().onFilterRemoved(dribbbleSource.getKey());

        LiveData<FeedUiModel> feed = homeViewModel.getFeed(COLUMNS);
        List<PlaidItem> feedItems = feed.getValue().getItems();

        Assert.assertEquals(Arrays.asList(post, story), feedItems);
    }

    @Test
    public void filtersChanged_activeSource() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModelWithFeedData(Arrays.asList(post, shot, story));
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );
        LiveData<FeedUiModel> initialFeed = homeViewModel.getFeed(COLUMNS);

        DribbbleSourceItem activeSource = new DribbbleSourceItem("dribbble", true);
        filtersChangedCallback.getValue().onFiltersChanged(activeSource);

        LiveData<FeedUiModel> feed = homeViewModel.getFeed(COLUMNS);
        Assert.assertEquals(initialFeed, feed);
    }

    @Test
    public void filtersChanged_inactiveSource() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModelWithFeedData(Arrays.asList(post, shot, story));
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );

        DribbbleSourceItem inactiveSource = new DribbbleSourceItem("dribbble", false);
        filtersChangedCallback.getValue().onFiltersChanged(inactiveSource);

        LiveData<FeedUiModel> feed = homeViewModel.getFeed(COLUMNS);
        Assert.assertEquals(Arrays.asList(post, story), feed.getValue().getItems());
    }

    @Test
    public void dataLoading() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(dataManager).registerCallback(dataLoadingCallback.capture());

        dataLoadingCallback.getValue().dataStartedLoading();

        LiveData<FeedProgressUiModel> progress = homeViewModel.getFeedProgress();
        Assert.assertEquals(new FeedProgressUiModel(true), progress.getValue());
    }

    @Test
    public void dataFinishedLoading() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(dataManager).registerCallback(dataLoadingCallback.capture());

        dataLoadingCallback.getValue().dataFinishedLoading();

        LiveData<FeedProgressUiModel> progress = homeViewModel.getFeedProgress();
        Assert.assertEquals(new FeedProgressUiModel(false), progress.getValue());
    }

    @Test
    public void dataLoading_atInit() throws ExecutionException, InterruptedException {
        createViewModel();

        verify(dataManager, timeout(100)).loadMore();
    }

    @Test
    public void feed_emitsWhenDataLoaded() throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(dataManager).setOnDataLoadedCallback(dataLoadedCallback.capture());

        dataLoadedCallback.getValue().onDataLoaded(Arrays.asList(post, shot, story));

        LiveData<FeedUiModel> feed = homeViewModel.getFeed(2);
        List<PlaidItem> feedItems = feed.getValue().getItems();

        Assert.assertEquals(Arrays.asList(post, story, shot), feedItems);
    }

    private HomeViewModel createViewModelWithFeedData(List<PlaidItem> feedData) throws ExecutionException, InterruptedException {
        HomeViewModel homeViewModel = createViewModel();
        verify(dataManager).setOnDataLoadedCallback(dataLoadedCallback.capture());

        dataLoadedCallback.getValue().onDataLoaded(feedData);

        return homeViewModel;
    }

    private HomeViewModel createViewModel(List<SourceItem> list) throws ExecutionException, InterruptedException {
        whenever(sourcesRepository.getSources()).thenReturn(list);
        return new HomeViewModel(
                dataManager,
                loginRepository,
                sourcesRepository,
                provideFakeCoroutinesDispatcherProvider()
        );
    }

}