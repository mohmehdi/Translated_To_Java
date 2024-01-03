package io.plaidapp.search.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.feed.FeedProgressUiModel;
import io.plaidapp.core.feed.FeedUiModel;
import io.plaidapp.search.domain.SearchDataSourceFactoriesRegistry;
import io.plaidapp.search.domain.LoadSearchDataUseCase;

public class SearchViewModel extends ViewModel {

    private SearchDataSourceFactoriesRegistry sourcesRegistry;
    private CoroutinesDispatcherProvider dispatcherProvider;
    private LoadSearchDataUseCase loadSearchData;

    private MutableLiveData<String> searchQuery = new MutableLiveData<>();

    private LiveData<FeedUiModel> results = Transformations.switchMap(searchQuery, input -> {
        loadSearchData = new LoadSearchDataUseCase(factories, input);
        loadMore();
        return loadSearchData.getSearchResult();
    });

    private MutableLiveData<FeedProgressUiModel> _searchProgress = new MutableLiveData<>();
    public LiveData<FeedProgressUiModel> searchProgress = _searchProgress;

    public SearchViewModel(SearchDataSourceFactoriesRegistry sourcesRegistry, CoroutinesDispatcherProvider dispatcherProvider) {
        this.sourcesRegistry = sourcesRegistry;
        this.dispatcherProvider = dispatcherProvider;
    }

    public LiveData<FeedUiModel> getSearchResults() {
        return Transformations.map(results, input -> new FeedUiModel(input));
    }

    public void searchFor(String query) {
        searchQuery.postValue(query);
    }

    public void loadMore() {
        viewModelScope.launch(dispatcherProvider.getComputation()) {
            _searchProgress.postValue(new FeedProgressUiModel(true));
            loadSearchData.invoke();
            _searchProgress.postValue(new FeedProgressUiModel(false));
        }
    }

    public void clearResults() {
        loadSearchData = null;
    }
}