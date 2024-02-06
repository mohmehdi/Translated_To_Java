




package io.plaidapp.search.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.nhaarman.mockitokotlin2.MockitoAnnotations;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.core.ui.CoroutinesDispatcherProvider;
import io.plaidapp.search.R;
import io.plaidapp.search.domain.SearchDataSourceFactoriesRegistry;
import io.plaidapp.search.shots;
import io.plaidapp.search.testShot1;
import io.plaidapp.test.shared.LiveDataTestUtil;
import io.plaidapp.test.shared.ProvideFakeCoroutinesDispatcherProvider;
import java.util.Set;
import kotlin.coroutines.CoroutineContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SearchViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FakeSearchDataSourceFactory factory;
    private SearchDataSourceFactoriesRegistry registry;
    private Result<List<PlaidItem>> result;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        registry = mock(SearchDataSourceFactoriesRegistry.class);
        result = Result.success(shots);
        Set<SearchDataSourceFactory> dataSourceFactories = Set.of(factory = new FakeSearchDataSourceFactory());
        whenever(registry.dataSourceFactories).thenReturn(dataSourceFactories);
    }

    @Test
    public void searchFor_searchesInDataManager() throws Exception {
        String query = "Plaid";

        factory.dataSource.result = result;
        SearchViewModel viewModel = new SearchViewModel(registry, new ProvideFakeCoroutinesDispatcherProvider());

        viewModel.searchFor(query);

        Result<List<PlaidItem>> results = viewModel.searchResults.getValue();
        Assert.assertEquals(results.data, result.data);
    }

    @Test
    public void loadMore_loadsInDataManager() throws Exception {
        String query = "Plaid";
        SearchViewModel viewModel = new SearchViewModel(registry, new ProvideFakeCoroutinesDispatcherProvider());

        viewModel.searchFor(query);

        Result<List<PlaidItem>> moreResult = Result.success(List.of(testShot1));
        factory.dataSource.result = moreResult;

        viewModel.loadMore();

        Result<List<PlaidItem>> results = viewModel.searchResults.getValue();
        Assert.assertEquals(results.data, moreResult.data);
    }
}

final class FakeDataSource extends PlaidDataSource<PlaidItem> {

    Result<List<PlaidItem>> result;

    public FakeDataSource(SourceItem sourceItem) {
        super(sourceItem);
    }

    @Override
    protected Result<List<PlaidItem>> loadMore() {
        return result;
    }
}

final class FakeSearchDataSourceFactory implements SearchDataSourceFactory {

    PlaidDataSource<PlaidItem> dataSource;

    @Override
    public PlaidDataSource<PlaidItem> create(String query) {
        dataSource = new FakeDataSource(new SourceItem("id", "query", 100, "name", 0, true, true) {
        });
        return dataSource;
    }
}