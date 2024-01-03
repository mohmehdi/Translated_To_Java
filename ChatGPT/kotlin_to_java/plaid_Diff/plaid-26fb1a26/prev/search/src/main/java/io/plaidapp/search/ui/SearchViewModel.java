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
import io.plaidapp.search.domain.SearchUseCase;
import kotlinx.coroutines.launch;

public class SearchViewModel extends ViewModel {

    private SearchDataSourceFactoriesRegistry sourcesRegistry;
    private CoroutinesDispatcherProvider dispatcherProvider;
    private SearchUseCase searchUseCase;
    private MutableLiveData<String> searchQuery;
    private LiveData<FeedUiModel> results;
    private MutableLiveData<FeedProgressUiModel> _searchProgress;
    public LiveData<FeedProgressUiModel> searchProgress;

    public SearchViewModel(SearchDataSourceFactoriesRegistry sourcesRegistry, CoroutinesDispatcherProvider dispatcherProvider) {
        this.sourcesRegistry = sourcesRegistry;
        this.dispatcherProvider = dispatcherProvider;
        this.searchQuery = new MutableLiveData<>();
        this._searchProgress = new MutableLiveData<>();
        this.searchProgress = _searchProgress;
        this.results = Transformations.switchMap(searchQuery, input -> {
            searchUseCase = new SearchUseCase(factories, input);
            loadMore();
            return searchUseCase.searchResult;
        });
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
            searchUseCase.loadMore();
            _searchProgress.postValue(new FeedProgressUiModel(false));
        }
    }

    public void clearResults() {
        searchUseCase = null;
    }
}