package io.plaidapp.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
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
import io.plaidapp.core.feed.FeedUiModel;
import io.plaidapp.core.ui.expandPopularItems;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import io.plaidapp.core.ui.filter.SourceUiModel;
import io.plaidapp.core.ui.filter.SourcesHighlightUiModel;
import io.plaidapp.core.ui.filter.SourcesUiModel;
import io.plaidapp.core.ui.getPlaidItemsForDisplay;
import io.plaidapp.core.util.event.Event;
import kotlinx.coroutines.launch;
import java.util.Collections;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private MutableLiveData<SourcesUiModel> _sources = new MutableLiveData<>();
    public LiveData<SourcesUiModel> sources = _sources;

    private MutableLiveData<FeedProgressUiModel> _feedProgress = new MutableLiveData<>();
    public LiveData<FeedProgressUiModel> feedProgress = _feedProgress;

    private MutableLiveData<List<PlaidItem>> feedData = new MutableLiveData<>();

    private OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback = new OnDataLoadedCallback<List<PlaidItem>>() {
        @Override
        public void onDataLoaded(List<PlaidItem> data) {
            List<PlaidItem> oldItems = feedData.getValue().orEmpty();
            updateFeedData(oldItems, data);
        }
    };

    private FiltersChangedCallback filtersChangedCallbacks = new FiltersChangedCallback() {
        @Override
        public void onFiltersChanged(SourceItem changedFilter) {
            if (!changedFilter.getActive()) {
                handleDataSourceRemoved(changedFilter.getKey(), feedData.getValue().orEmpty());
            }
        }

        @Override
        public void onFilterRemoved(String sourceKey) {
            handleDataSourceRemoved(sourceKey, feedData.getValue().orEmpty());
        }

        @Override
        public void onFiltersUpdated(List<SourceItem> sources) {
            updateSourcesUiModel(sources);
        }
    };

    private DataLoadingSubject.DataLoadingCallbacks dataLoadingCallbacks = new DataLoadingSubject.DataLoadingCallbacks() {
        @Override
        public void dataStartedLoading() {
            _feedProgress.postValue(new FeedProgressUiModel(true));
        }

        @Override
        public void dataFinishedLoading() {
            _feedProgress.postValue(new FeedProgressUiModel(false));
        }
    };

    private DataManager dataManager;
    private LoginRepository designerNewsLoginRepository;
    private SourcesRepository sourcesRepository;
    private CoroutinesDispatcherProvider dispatcherProvider;

    public HomeViewModel(DataManager dataManager, LoginRepository designerNewsLoginRepository, SourcesRepository sourcesRepository, CoroutinesDispatcherProvider dispatcherProvider) {
        this.dataManager = dataManager;
        this.designerNewsLoginRepository = designerNewsLoginRepository;
        this.sourcesRepository = sourcesRepository;
        this.dispatcherProvider = dispatcherProvider;
        sourcesRepository.registerFilterChangedCallback(filtersChangedCallbacks);
        dataManager.setOnDataLoadedCallback(onDataLoadedCallback);
        dataManager.registerCallback(dataLoadingCallbacks);
        getSources();
        loadData();
    }

    public LiveData<FeedUiModel> getFeed(int columns) {
        return Transformations.switchMap(feedData, it -> {
            expandPopularItems(it, columns);
            return new MutableLiveData<>(new FeedUiModel(it));
        });
    }

    public boolean isDesignerNewsUserLoggedIn() {
        return designerNewsLoginRepository.isLoggedIn();
    }

    public void logoutFromDesignerNews() {
        designerNewsLoginRepository.logout();
    }

    public void loadData() {
        viewModelScope.launch {
            dataManager.loadMore();
        }
    }

    @Override
    protected void onCleared() {
        dataManager.cancelLoading();
        super.onCleared();
    }

    public void addSources(String query, boolean isDribbble, boolean isDesignerNews) {
        if (query.isBlank()) {
            return;
        }
        List<SourceItem> sources = new ArrayList<>();
        if (isDribbble) {
            sources.add(new DribbbleSourceItem(query, true));
        }
        if (isDesignerNews) {
            sources.add(new DesignerNewsSearchSource(query, true));
        }
        viewModelScope.launch(dispatcherProvider.getIO()) {
            sourcesRepository.addOrMarkActiveSources(sources);
        }
    }

    private void getSources() {
        viewModelScope.launch(dispatcherProvider.getIO()) {
            List<SourceItem> sources = sourcesRepository.getSources();
            updateSourcesUiModel(sources);
        }
    }

    private void updateSourcesUiModel(List<SourceItem> sources) {
        List<SourceUiModel> newSourcesUiModel = createNewSourceUiModels(sources);
        SourcesUiModel oldSourceUiModel = _sources.getValue();
        if (oldSourceUiModel == null) {
            _sources.postValue(new SourcesUiModel(newSourcesUiModel));
        } else {
            SourcesHighlightUiModel highlightUiModel = createSourcesHighlightUiModel(oldSourceUiModel.getSourceUiModels(), newSourcesUiModel);
            Event<SourcesHighlightUiModel> event = highlightUiModel != null ? new Event<>(highlightUiModel) : null;
            _sources.postValue(new SourcesUiModel(newSourcesUiModel, event));
        }
    }

    private SourcesHighlightUiModel createSourcesHighlightUiModel(List<SourceUiModel> oldSources, List<SourceUiModel> newSources) {
        if (oldSources.size() >= newSources.size()) {
            return null;
        }
        List<Integer> positions = new ArrayList<>();
        int itemsAdded = 0;
        for (int i = 0; i < oldSources.size(); i++) {
            SourceUiModel item = oldSources.get(i);
            if (!item.getKey().equals(newSources.get(i + itemsAdded).getKey())) {
                positions.add(i + itemsAdded);
                itemsAdded++;
            }
        }
        int lastItems = oldSources.size() + itemsAdded;
        for (int i = lastItems; i < newSources.size(); i++) {
            positions.add(i);
        }
        Integer scrollToPosition = Collections.max(positions);
        return scrollToPosition != null ? new SourcesHighlightUiModel(positions, scrollToPosition) : null;
    }

    private void updateFeedData(List<PlaidItem> oldItems, List<PlaidItem> newItems) {
        feedData.postValue(getPlaidItemsForDisplay(oldItems, newItems));
    }

    private void handleDataSourceRemoved(String dataSourceKey, List<PlaidItem> oldItems) {
        List<PlaidItem> items = new ArrayList<>(oldItems);
        items.removeIf(it -> dataSourceKey.equals(it.getDataSource()));
        feedData.postValue(items);
    }

    private List<SourceUiModel> createNewSourceUiModels(List<SourceItem> sources) {
        List<SourceItem> mutableSources = new ArrayList<>(sources);
        Collections.sort(mutableSources, new SourceItem.SourceComparator());
        List<SourceUiModel> sourceUiModels = new ArrayList<>();
        for (SourceItem it : mutableSources) {
            SourceUiModel sourceUiModel = new SourceUiModel(
                    it.getKey(),
                    it.getName(),
                    it.getActive(),
                    it.getIconRes(),
                    it.isSwipeDismissable(),
                    sourceUiModel -> sourcesRepository.changeSourceActiveState(sourceUiModel.getKey()),
                    sourceUiModel -> {
                        if (sourceUiModel.isSwipeDismissable()) {
                            sourcesRepository.removeSource(sourceUiModel.getKey());
                        }
                    }
            );
            sourceUiModels.add(sourceUiModel);
        }
        return sourceUiModels;
    }
}