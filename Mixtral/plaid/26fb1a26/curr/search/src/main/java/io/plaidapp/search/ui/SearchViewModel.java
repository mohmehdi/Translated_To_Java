




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
import io.plaidapp.search.domain.LoadSearchDataUseCase;
import java.util.concurrent.Executor;

public class SearchViewModel extends ViewModel {

    private final SearchDataSourceFactoriesRegistry sourcesRegistry;
    private final CoroutinesDispatcherProvider dispatcherProvider;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private LoadSearchDataUseCase loadSearchData;

    public SearchViewModel(@NonNull SearchDataSourceFactoriesRegistry sourcesRegistry,
                           @NonNull CoroutinesDispatcherProvider dispatcherProvider) {
        this.sourcesRegistry = sourcesRegistry;
        this.dispatcherProvider = dispatcherProvider;
    }

    public void searchFor(String query) {
        searchQuery.postValue(query);
    }

    public LiveData<FeedUiModel> searchResults = Transformations.switchMap(searchQuery, input -> {
        loadSearchData = new LoadSearchDataUseCase(sourcesRegistry.getDataSourceFactories(), input);
        loadMore();
        return loadSearchData.searchResult;
    });

    public LiveData<FeedProgressUiModel> searchProgress = new MutableLiveData<>();

    public void loadMore() {
        Executor computation = dispatcherProvider.getComputation();
        computation.execute(() -> {
            searchProgress.postValue(new FeedProgressUiModel(true));
            loadSearchData.invoke();
            computation.execute(() -> searchProgress.postValue(new FeedProgressUiModel(false)));
        });
    }

    public void clearResults() {
        loadSearchData = null;
    }


}