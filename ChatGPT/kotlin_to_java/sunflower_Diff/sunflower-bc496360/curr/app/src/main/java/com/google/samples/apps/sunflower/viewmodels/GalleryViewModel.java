
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;
import androidx.paging.PagingData;
import androidx.paging.cachedIn;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;
import com.google.samples.apps.sunflower.data.UnsplashRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.Flow;
import javax.inject.Inject;

@HiltViewModel
public class GalleryViewModel extends ViewModel {
    private String currentQueryValue = null;
    private Flow<PagingData<UnsplashPhoto>> currentSearchResult = null;

    @Inject
    public GalleryViewModel(UnsplashRepository repository) {
        this.repository = repository;
    }

    public Flow<PagingData<UnsplashPhoto>> searchPictures(String queryString) {
        currentQueryValue = queryString;
        Flow<PagingData<UnsplashPhoto>> newResult =
            repository.getSearchResultStream(queryString).cachedIn(viewModelScope);
        currentSearchResult = newResult;
        return newResult;
    }
}
