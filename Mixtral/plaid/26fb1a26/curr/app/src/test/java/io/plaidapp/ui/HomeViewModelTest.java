




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
import org.mockito.quality.Strictness;

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
import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem;
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
                new DesignerNewsSearchSourceItem("query", true)
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

        LiveData<SourcesUiModel> sources = homeViewModel.getSources();
        SourcesHighlightUiModel sourcesHighlightUiModel = sources.getValue().getHighlightSources().getValue();

        Assert.assertEquals(2, sources.getValue().getSourceUiModels().size());
        Assert.assertEquals(0, sourcesHighlightUiModel.getHighlightPositions().get(0));
        Assert.assertEquals(1, sourcesHighlightUiModel.getScrollToPosition());
    }

    @Test
    public void filtersUpdated_oneNewSource() throws ExecutionException, InterruptedException {

        List<SourceItem> sources = new ArrayList<>();
        sources.add(designerNewsSource);

        HomeViewModel homeViewModel = createViewModel(sources);
        verify(sourcesRepository).registerFilterChangedCallback(
                filtersChangedCallback.capture()
        );

        sources.add(dribbbleSource);
        filtersChangedCallback.getValue().onFiltersUpdated(sources);

        LiveData<SourcesUiModel> sourcesUiModel = homeViewModel.getSources();

        SourcesHighlightUiModel sourcesHighlightUiModel = sourcesUiModel.getValue().getHighlightSources().getValue();

        Assert.assertEquals(1, sourcesUiModel.getValue().getSourceUiModels().get(1).getSource().isSwipeDismissable() ? 1 : 0);
        Assert.assertEquals(1, sourcesHighlightUiModel.getHighlightPositions().get(0));
        Assert.assertEquals(1, sourcesHighlightUiModel.getScrollToPosition());
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
        SourcesUiModel.SourceUiModel uiSource = sources.getValue().getSourceUiModels().get(0);

        homeViewModel.onSourceClicked(uiSource);

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
        SourcesUiModel.SourceUiModel uiSource = sources.getValue().getSourceUiModels().get(0);

        uiSource.onSourceDismissed(designerNewsSourceUiModel.copy().toBuilder().setIsSwipeDismissable(true).build());

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
        SourcesUiModel.SourceUiModel uiSource = sources.getValue().getSourceUiModels().get(0);

        uiSource.onSourceDismissed(designerNewsSourceUiModel.copy().toBuilder().setIsSwipeDismissable(false).build());

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

        Assert.assertEquals(2, feedItems.size());
        Assert.assertEquals(post, feedItems.get(0));
        Assert.assertEquals(story, feedItems.get(1));
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

        Assert.assertEquals(initialFeed.getValue().getItems(), feed.getValue().getItems());
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

        Assert.assertEquals(2, feed.getValue().getItems().size());
        Assert.assertEquals(post, feed.getValue().getItems().get(0));
        Assert.assertEquals(story, feed.getValue().getItems().get(1));
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

        Assert.assertEquals(3, feedItems.size());
        Assert.assertEquals(post, feedItems.get(0));
        Assert.assertEquals(shot, feedItems.get(1));
        Assert.assertEquals(story, feedItems.get(2));
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