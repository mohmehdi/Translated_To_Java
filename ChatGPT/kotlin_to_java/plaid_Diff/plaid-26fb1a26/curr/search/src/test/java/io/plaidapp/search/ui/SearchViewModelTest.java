package io.plaidapp.search.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.PlaidItem;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.search.domain.SearchDataSourceFactoriesRegistry;
import io.plaidapp.search.shots;
import io.plaidapp.search.testShot1;
import io.plaidapp.test.shared.LiveDataTestUtil;
import io.plaidapp.test.shared.provideFakeCoroutinesDispatcherProvider;
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class SearchViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FakeSearchDataSourceFactory factory = new FakeSearchDataSourceFactory();
    private SearchDataSourceFactoriesRegistry registry = mock(SearchDataSourceFactoriesRegistry.class);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        whenever(registry.dataSourceFactories()).thenReturn(setOf(factory));
    }

    @Test
    public void searchFor_searchesInDataManager() throws Exception {

        String query = "Plaid";

        Result<List<PlaidItem>> result = Result.Success(shots);
        factory.dataSource.result = result;
        SearchViewModel viewModel = new SearchViewModel(registry, provideFakeCoroutinesDispatcherProvider());


        viewModel.searchFor(query);


        Result<List<PlaidItem>> results = LiveDataTestUtil.getValue(viewModel.searchResults());
        Assert.assertEquals(results.getItems(), result.getData());
    }

    @Test
    public void loadMore_loadsInDataManager() throws Exception {

        String query = "Plaid";
        SearchViewModel viewModel = new SearchViewModel(registry, provideFakeCoroutinesDispatcherProvider());

        viewModel.searchFor(query);

        Result<List<PlaidItem>> moreResult = Result.Success(listOf(testShot1));
        factory.dataSource.result = moreResult;


        viewModel.loadMore();


        Result<List<PlaidItem>> results = LiveDataTestUtil.getValue(viewModel.searchResults());
        Assert.assertEquals(results.getItems(), moreResult.getData());
    }
}

SourceItem sourceItem = new SourceItem(
    "id", "query", 100, "name", 0, true, true
) {};

class FakeSearchDataSourceFactory implements SearchDataSourceFactory {
    FakeDataSource dataSource = new FakeDataSource();
    @Override
    public PlaidDataSource create(String query) {
        return dataSource;
    }
}

class FakeDataSource extends PlaidDataSource {

    Result<List<PlaidItem>> result = Result.Success(emptyList<PlaidItem>());

    @Override
    public Result<List<PlaidItem>> loadMore() {
        return result;
    }
}