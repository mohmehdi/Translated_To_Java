




package io.plaidapp.search.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.nhaarman.mockitokotlin2.MockitoAnnotations;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.search.domain.SearchDataSourceFactoriesRegistry;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.search.R;
import io.plaidapp.search.shots;
import io.plaidapp.search.testShot1;
import io.plaidapp.test.shared.LiveDataTestUtil;
import io.plaidapp.test.shared.provideFakeCoroutinesDispatcherProvider;
import java.util.Set;
import java.util.concurrent.Executor;
import kotlin.Unit;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Runnable;
import kotlinx.coroutines.newSingleThreadContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SearchViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FakeSearchDataSourceFactory factory;
    private SearchDataSourceFactoriesRegistry registry;
    private PlaidDataSource dataSource;
    private SearchViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        registry = mock();
        factory = new FakeSearchDataSourceFactory();
        Set<SearchDataSourceFactory> dataSourceFactories = setOf(factory);
        whenever(registry.dataSourceFactories).thenReturn(dataSourceFactories);
        dataSource = factory.create("query");
    }

    @Test
    public void searchFor_searchesInDataManager() throws Exception {
        String query = "Plaid";

        Result<List<PlaidItem>> result = Result.success(shots);
        dataSource.result = result;

        viewModel = new SearchViewModel(registry, provideFakeCoroutinesDispatcherProvider());

        viewModel.searchFor(query);

        Result<List<PlaidItem>> results = viewModel.searchResults.getValue();
        Assert.assertEquals(results.data, result.data);
    }

    @Test
    public void loadMore_loadsInDataManager() throws Exception {
        String query = "Plaid";
        viewModel = new SearchViewModel(registry, provideFakeCoroutinesDispatcherProvider());
        viewModel.searchFor(query);

        Result<List<PlaidItem>> moreResult = Result.success(listOf(testShot1));
        dataSource.result = moreResult;

        viewModel.loadMore();

        Result<List<PlaidItem>> results = viewModel.searchResults.getValue();
        Assert.assertEquals(results.data, moreResult.data);
    }
}

class FakeSearchDataSourceFactory implements SearchDataSourceFactory {
    PlaidDataSource dataSource;

    @Override
    public PlaidDataSource create(String query) {
        dataSource = new FakeDataSource();
        return dataSource;
    }
}

class FakeDataSource extends PlaidDataSource<PlaidItem> {

    Result<List<PlaidItem>> result = Result.success(Collections.emptyList());

    public FakeDataSource() {
        super(new SourceItem("query", 100, "name", 0, true, true));
    }

    @Override
    public Result<List<PlaidItem>> loadMore() {
        return result;
    }
}