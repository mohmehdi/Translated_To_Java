




package io.plaidapp.designernews.domain.search;

import com.nhaarman.mockitokotlin2.Mockito;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import org.junit.Assert;
import org.junit.Test;

public class DesignerNewsSearchDataSourceFactoryTest {

    private StoriesRepository repository = Mockito.mock(StoriesRepository.class);
    private DesignerNewsSearchDataSourceFactory factory = new DesignerNewsSearchDataSourceFactory(repository);

    @Test
    public void create() {
        
        String query = "Android";

        
        DesignerNewsSearchDataSource dataSource = factory.create(query);

        
        Assert.assertEquals(query, dataSource.getSourceItem().getKey());
    }
}