




package io.plaidapp.dribbble.domain.search;

import com.nhaarman.mockitokotlin2.Mockito;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import org.junit.Assert;
import org.junit.Test;

public class DribbbleSearchDataSourceFactoryTest {

    private ShotsRepository repository = Mockito.mock(ShotsRepository.class);
    private DribbbleSearchDataSourceFactory factory = new DribbbleSearchDataSourceFactory(repository);

    @Test
    public void create() {
        
        String query = "Android";

        
        DribbbleSearchDataSource dataSource = factory.create(query);

        
        Assert.assertEquals(query, dataSource.getSourceItem().getKey());
    }
}