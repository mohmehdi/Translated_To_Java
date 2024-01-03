package io.plaidapp.search.domain;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.core.ui.getPlaidItemsForDisplay;

import java.util.List;
import java.util.Set;

public class LoadSearchDataUseCase {

    private List<SearchDataSourceFactory> dataSources;
    private MutableLiveData<List<PlaidItem>> _searchResult;
    public LiveData<List<PlaidItem>> searchResult;

    public LoadSearchDataUseCase(Set<SearchDataSourceFactory> factories, String query) {
        dataSources = factories.stream().map(factory -> factory.create(query)).collect(Collectors.toList());
        _searchResult = new MutableLiveData<>();
        searchResult = _searchResult;
    }

    public void invoke() {
        for (SearchDataSourceFactory dataSource : dataSources) {
            Result<List<PlaidItem>> result = dataSource.loadMore();
            if (result instanceof Result.Success) {
                List<PlaidItem> oldItems = _searchResult.getValue() != null ? _searchResult.getValue() : Collections.emptyList();
                List<PlaidItem> searchResult = getPlaidItemsForDisplay(oldItems, result.getData());
                _searchResult.postValue(searchResult);
            }
        }
    }
}