




package io.plaidapp.search.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.core.ui.getPlaidItemsForDisplay;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchUseCase {

    private final Set<SearchDataSourceFactory> factories;
    private final String query;
    private final MutableLiveData<List<PlaidItem>> _searchResult;

    public SearchUseCase(@NonNull Set<SearchDataSourceFactory> factories, String query) {
        this.factories = factories;
        this.query = query;
        this._searchResult = new MutableLiveData<>();
        this.searchResult = Transformations.switchMap(this._searchResult, items -> items);
    }


    public void loadMore() {
        for (SearchDataSourceFactory factory : factories) {
            SearchDataSource dataSource = factory.create(query);
            Result result = dataSource.loadMore();
            if (result instanceof Result.Success) {
                Result.Success successResult = (Result.Success) result;
                List<PlaidItem> oldItems = _searchResult.getValue() != null ? new ArrayList<>(_searchResult.getValue()) : new ArrayList<>();
                List<PlaidItem> searchResult = getPlaidItemsForDisplay(oldItems, successResult.getData());
                _searchResult.postValue(searchResult);
            }
        }
    }
}