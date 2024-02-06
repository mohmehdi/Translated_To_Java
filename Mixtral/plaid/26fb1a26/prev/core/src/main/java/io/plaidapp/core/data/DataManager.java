




package io.plaidapp.core.data;

import io.plaidapp.core.data.prefs.SourcesRepository;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource.Companion;
import io.plaidapp.core.designernews.domain.LoadStoriesUseCase;
import io.plaidapp.core.designernews.domain.SearchStoriesUseCase;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.producthunt.data.ProductHuntSourceItem;
import io.plaidapp.core.producthunt.data.ProductHuntSourceItem.Companion;
import io.plaidapp.core.producthunt.domain.LoadPostsUseCase;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import io.plaidapp.core.util.exhaustive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;

public class DataManager implements DataLoadingSubject {

    private static class InFlightRequestData {
        private final String key;
        private final int page;

        public InFlightRequestData(String key, int page) {
            this.key = key;
            this.page = page;
        }

 
    }

    private final LoadStoriesUseCase loadStories;
    private final LoadPostsUseCase loadPosts;
    private final SearchStoriesUseCase searchStories;
    private final ShotsRepository shotsRepository;
    private final SourcesRepository sourcesRepository;
    private final CoroutinesDispatcherProvider dispatcherProvider;

    private final CoroutineScope scope;
    private final Map<InFlightRequestData, Job> parentJobs;
    private final AtomicInteger loadingCount;
    private final List<DataLoadingSubject.DataLoadingCallbacks> loadingCallbacks;
    private OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback;
    private Map<String, Integer> pageIndexes;

    private final FiltersChangedCallback filterListener;

    @Inject
    public DataManager(
            LoadStoriesUseCase loadStories,
            LoadPostsUseCase loadPosts,
            SearchStoriesUseCase searchStories,
            ShotsRepository shotsRepository,
            SourcesRepository sourcesRepository,
            CoroutinesDispatcherProvider dispatcherProvider) {
        this.loadStories = loadStories;
        this.loadPosts = loadPosts;
        this.searchStories = searchStories;
        this.shotsRepository = shotsRepository;
        this.sourcesRepository = sourcesRepository;
        this.dispatcherProvider = dispatcherProvider;

        SupervisorJob supervisorJob = new SupervisorJob();
        this.scope = new CoroutineScope(dispatcherProvider.computation + supervisorJob);
        this.parentJobs = new HashMap<>();
        this.loadingCount = new AtomicInteger(0);
        this.loadingCallbacks = new ArrayList<>();
        this.filterListener = new FiltersChangedCallback() {
            @Override
            public void onFiltersChanged(SourceItem changedFilter) {
                if (changedFilter.isActive()) {
                    loadSource(changedFilter);
                } else {
                    String key = changedFilter.getKey();
                    for (Map.Entry<InFlightRequestData, Job> entry : parentJobs.entrySet()) {
                        InFlightRequestData requestData = entry.getKey();
                        if (requestData.getKey().equals(key)) {
                            entry.getValue().cancel();
                            parentJobs.remove(requestData);
                        }
                    }

                    if (pageIndexes.containsKey(key)) {
                        pageIndexes.put(key, 0);
                    }
                }
            }
        };

        pageIndexes = new HashMap<>();
        List<SourceItem> sources = sourcesRepository.getSourcesSync();
        for (SourceItem source : sources) {
            pageIndexes.put(source.getKey(), 0);
        }
    }

    public void setOnDataLoadedCallback(OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback) {
        this.onDataLoadedCallback = onDataLoadedCallback;
    }

    private void onDataLoaded(List<PlaidItem> data) {
        if (onDataLoadedCallback != null) {
            onDataLoadedCallback.onDataLoaded(data);
        }
    }

    public void loadMore() {
        List<SourceItem> sources = sourcesRepository.getSources();
        for (SourceItem source : sources) {
            loadSource(source);
        }
    }

    public void cancelLoading() {
        for (Job job : parentJobs.values()) {
            job.cancel();
        }
        parentJobs.clear();
    }

    private void loadSource(SourceItem source) {
        if (source.isActive()) {
            loadStarted();
            int page = getNextPageIndex(source.getKey());

            InFlightRequestData requestData = new InFlightRequestData(source.getKey(), page);
            if (SOURCE_DESIGNER_NEWS_POPULAR.equals(source.getKey())) {
                parentJobs.put(requestData, launchLoadDesignerNewsStories(requestData));
            } else if (SOURCE_PRODUCT_HUNT.equals(source.getKey())) {
                parentJobs.put(requestData, launchLoadProductHunt(requestData));
            } else if (source instanceof DribbbleSourceItem) {
                parentJobs.put(requestData, loadDribbbleSearch((DribbbleSourceItem) source, requestData));
            } else if (source instanceof DesignerNewsSearchSource) {
                parentJobs.put(requestData, loadDesignerNewsSearch((DesignerNewsSearchSource) source, requestData));
            }
        }
    }

    private int getNextPageIndex(String dataSource) {
        int nextPage = 1;
        if (pageIndexes.containsKey(dataSource)) {
            nextPage = pageIndexes.get(dataSource) + 1;
        }
        pageIndexes.put(dataSource, nextPage);
        return nextPage;
    }

    private boolean sourceIsEnabled(String key) {
        return pageIndexes.containsKey(key);
    }

    private void sourceLoaded(List<PlaidItem> data, String source, InFlightRequestData request) {
        loadFinished();
        if (data != null && !data.isEmpty() && sourceIsEnabled(source)) {
            setPage(data, request.getPage());
            setDataSource(data, source);
            onDataLoaded(data);
        }
        parentJobs.remove(request);
    }

    private void loadFailed(InFlightRequestData request) {
        loadFinished();
        parentJobs.remove(request);
    }

    private Job launchLoadDesignerNewsStories(InFlightRequestData data) {
        return scope.launch(new Runnable() {
        });
    }

    private Job loadDesignerNewsSearch(DesignerNewsSearchSource source, InFlightRequestData data) {
        return scope.launch(new Runnable() {
        });
    }

    private Job loadDribbbleSearch(DribbbleSourceItem source, InFlightRequestData data) {
        return scope.launch(new Runnable() {
            
        });
    }

    private Job launchLoadProductHunt(InFlightRequestData data) {
        return scope.launch(new Runnable() {
 
        });
    }

    @Override
    public void registerCallback(DataLoadingSubject.DataLoadingCallbacks callback) {
        loadingCallbacks.add(callback);
    }

    private void loadStarted() {
        if (loadingCount.incrementAndGet() == 1) {
            dispatchLoadingStartedCallbacks();
        }
    }

    private void loadFinished() {
        if (loadingCount.decrementAndGet() == 0) {
            dispatchLoadingFinishedCallbacks();
        }
    }

    private void setPage(List<PlaidItem> items, int page) {
        for (PlaidItem item : items) {
            item.setPage(page);
        }
    }

    private void setDataSource(List<PlaidItem> items, String dataSource) {
        for (PlaidItem item : items) {
            item.setDataSource(dataSource);
        }
    }

    private void dispatchLoadingStartedCallbacks() {
        for (DataLoadingSubject.DataLoadingCallbacks callback : loadingCallbacks) {
            callback.dataStartedLoading();
        }
    }

    private void dispatchLoadingFinishedCallbacks() {
        for (DataLoadingSubject.DataLoadingCallbacks callback : loadingCallbacks) {
            callback.dataFinishedLoading();
        }
    }
}