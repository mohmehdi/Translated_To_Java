




package io.plaidapp.search.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.feed.FeedProgressUiModel;
import io.plaidapp.core.feed.FeedUiModel;
import io.plaidapp.search.domain.SearchDataSourceFactoriesRegistry;
import io.plaidapp.search.domain.SearchUseCase;
import io.plaidapp.search.domain.SearchUseCaseFactory;
import kotlinx.coroutines.Dispatchers;

public class SearchViewModel extends ViewModel {

    private final SearchDataSourceFactoriesRegistry sourcesRegistry;
    private final CoroutinesDispatcherProvider dispatcherProvider;
    private SearchUseCase searchUseCase;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private final LiveData<FeedUiModel> results;
    private final MutableLiveData<FeedProgressUiModel> _searchProgress = new MutableLiveData<>();
    public final LiveData<FeedProgressUiModel> searchProgress = _searchProgress;

    private SearchViewModel(@NonNull SearchDataSourceFactoriesRegistry sourcesRegistry,
                             @NonNull CoroutinesDispatcherProvider dispatcherProvider) {
        this.sourcesRegistry = sourcesRegistry;
        this.dispatcherProvider = dispatcherProvider;
        this.results = Transformations.switchMap(searchQuery, input -> {
            searchUseCase = new SearchUseCaseFactory(sourcesRegistry, input).create();
            loadMore();
            return searchUseCase.searchResult;
        });
    }

  

    public void searchFor(String query) {
        searchQuery.postValue(query);
    }

    public void loadMore() {
        viewModelScope.launch(dispatcherProvider.computation) {
            _searchProgress.postValue(new FeedProgressUiModel(true));
            searchUseCase.loadMore();
            _searchProgress.postValue(new FeedProgressUiModel(false));
        };
    }

    public void clearResults() {
        searchUseCase = null;
    }

}
