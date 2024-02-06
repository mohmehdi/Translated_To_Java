




package io.plaidapp.ui;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

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

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<SourcesUiModel> _sources = new MutableLiveData<>();
    public LiveData<SourcesUiModel> sources = _sources;

    private final MutableLiveData<FeedProgressUiModel> _feedProgress = new MutableLiveData<>();
    public LiveData<FeedProgressUiModel> feedProgress = _feedProgress;

    private final MutableLiveData<List<PlaidItem>> feedData = new MutableLiveData<>();

    private final OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback = new OnDataLoadedCallback<List<PlaidItem>>() {
        @Override
        public void onDataLoaded(List<PlaidItem> data) {
            List<PlaidItem> oldItems = feedData.getValue() != null ? feedData.getValue() : Collections.emptyList();
            updateFeedData(oldItems, data);
        }
    };
    
    private final FiltersChangedCallback filtersChangedCallbacks = new FiltersChangedCallback() {
        @Override
        public void onFiltersChanged(SourceItem changedFilter) {
            if (!changedFilter.active) {
                handleDataSourceRemoved(changedFilter.key, feedData.getValue() != null ? feedData.getValue() : Collections.emptyList());
            }
        }

        @Override
        public void onFilterRemoved(String sourceKey) {
            handleDataSourceRemoved(sourceKey, feedData.getValue() != null ? feedData.getValue() : Collections.emptyList());
        }

        @Override
        public void onFiltersUpdated(List<SourceItem> sources) {
            updateSourcesUiModel(sources);
        }
    };

    private final DataLoadingSubject.DataLoadingCallbacks dataLoadingCallbacks = new DataLoadingSubject.DataLoadingCallbacks() {
        @Override
        public void dataStartedLoading() {
            _feedProgress.postValue(new FeedProgressUiModel(true));
        }

        @Override
        public void dataFinishedLoading() {
            _feedProgress.postValue(new FeedProgressUiModel(false));
        }
    };

    public HomeViewModel(
            @NonNull DataManager dataManager,
            @NonNull LoginRepository designerNewsLoginRepository,
            @NonNull SourcesRepository sourcesRepository,
            @NonNull CoroutinesDispatcherProvider dispatcherProvider) {
        sourcesRepository.registerFilterChangedCallback(filtersChangedCallbacks);
        dataManager.setOnDataLoadedCallback(onDataLoadedCallback);
        dataManager.registerCallback(dataLoadingCallbacks);
        getSources(dispatcherProvider.io());
        loadData(dispatcherProvider.io());
    }

    public LiveData<FeedUiModel> getFeed(int columns) {
        return Transformations.switchMap(feedData, input -> {
            List<PlaidItem> expandedItems = expandPopularItems(input, columns);
            return new MutableLiveData<>(new FeedUiModel(expandedItems));
        });
    }

    public boolean isDesignerNewsUserLoggedIn() {
        return designerNewsLoginRepository.isLoggedIn();
    }

    public void logoutFromDesignerNews() {
        designerNewsLoginRepository.logout();
    }

    public void loadData() {
        dataManager.loadMore();
    }

    @Override
    protected void onCleared() {
        dataManager.cancelLoading();
        super.onCleared();
    }

    public void addSources(String query, boolean isDribbble, boolean isDesignerNews) {
        if (query.isEmpty()) {
            return;
        }
        List<SourceItem> sources = new ArrayList<>();
        if (isDribbble) {
            sources.add(new DribbbleSourceItem(query, true));
        }
        if (isDesignerNews) {
            sources.add(new DesignerNewsSearchSource(query, true));
        }
        viewModelScope.launch(dispatcherProvider.io(), () -> {
            sourcesRepository.addOrMarkActiveSources(sources);
        });
    }

    private void getSources() {
        viewModelScope.launch(executor, () -> {
            List<SourceItem> sources = sourcesRepository.getSources();
            updateSourcesUiModel(sources);
        });
    }

    private void updateSourcesUiModel(List<SourceItem> sources) {
        List<SourceUiModel> newSourcesUiModel = createNewSourceUiModels(sources);
        SourcesUiModel oldSourceUiModel = _sources.getValue();
        if (oldSourceUiModel == null) {
            _sources.postValue(new SourcesUiModel(newSourcesUiModel));
        } else {
            SourcesHighlightUiModel event = createSourcesHighlightUiModel(
                    oldSourceUiModel.sourceUiModels,
                    newSourcesUiModel
            );
            _sources.postValue(new SourcesUiModel(newSourcesUiModel, event));
        }
    }

    private SourcesHighlightUiModel createSourcesHighlightUiModel(
            List<SourceUiModel> oldSources,
            List<SourceUiModel> newSources) {
        if (oldSources.size() >= newSources.size()) {
            return null;
        }

        List<Integer> positions = new ArrayList<>();
        int itemsAdded = 0;

        for (int i = 0; i < oldSources.size(); i++) {
            SourceUiModel item = oldSources.get(i);
            if (!item.key.equals(newSources.get(i + itemsAdded).key)) {
                positions.add(i + itemsAdded);
                itemsAdded++;
            }
        }
        int lastItems = oldSources.size() + itemsAdded;
        for (int i = lastItems; i < newSources.size(); i++) {
            positions.add(i);
        }

        Integer scrollToPosition = Collections.max(positions);
        return scrollToPosition == null ? null : new SourcesHighlightUiModel(positions, scrollToPosition);
    }

    private void updateFeedData(List<PlaidItem> oldItems, List<PlaidItem> newItems) {
        feedData.postValue(getPlaidItemsForDisplay(oldItems, newItems));
    }

    private void handleDataSourceRemoved(String dataSourceKey, List<PlaidItem> oldItems) {
        List<PlaidItem> items = new ArrayList<>(oldItems);
        for (PlaidItem item : oldItems) {
            if (dataSourceKey.equals(item.dataSource)) {
                items.remove(item);
            }
        }
        feedData.postValue(items);
    }

    private List<SourceUiModel> createNewSourceUiModels(List<SourceItem> sources) {
        List<SourceItem> mutableSources = new ArrayList<>(sources);
        Collections.sort(mutableSources, SourceItem.SourceComparator);
        return mutableSources.stream().map(source -> {
            return new SourceUiModel(
                    source.key,
                    source.name,
                    source.active,
                    source.iconRes,
                    source.isSwipeDismissable,
                    sourceUiModel -> sourcesRepository.changeSourceActiveState(sourceUiModel.key),
                    sourceUiModel -> {
                        if (sourceUiModel.isSwipeDismissable) {
                            sourcesRepository.removeSource(sourceUiModel.key);
                        }
                    }
            );
        }).collect(Collectors.toList());
    }
}