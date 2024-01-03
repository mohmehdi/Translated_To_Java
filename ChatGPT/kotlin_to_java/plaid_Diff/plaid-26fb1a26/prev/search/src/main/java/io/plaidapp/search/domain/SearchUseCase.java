package io.plaidapp.search.domain;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.core.ui.getPlaidItemsForDisplay;

import java.util.List;
import java.util.Set;

public class SearchUseCase {

    private List<SearchDataSourceFactory> dataSources;
    private MutableLiveData<List<PlaidItem>> _searchResult;
    public LiveData<List<PlaidItem>> searchResult;

    public SearchUseCase(Set<SearchDataSourceFactory> factories, String query) {
        dataSources = factories.stream().map(factory -> factory.create(query)).collect(Collectors.toList());
        _searchResult = new MutableLiveData<>();
        searchResult = _searchResult;
    }

    public void loadMore() {
        for (SearchDataSourceFactory dataSource : dataSources) {
            Result result = dataSource.loadMore();
            if (result instanceof Result.Success) {
                List<PlaidItem> oldItems = _searchResult.getValue().orElse(Collections.emptyList()).stream().collect(Collectors.toList());
                List<PlaidItem> searchResult = getPlaidItemsForDisplay(oldItems, ((Result.Success) result).getData());
                _searchResult.postValue(searchResult);
            }
        }
    }
}