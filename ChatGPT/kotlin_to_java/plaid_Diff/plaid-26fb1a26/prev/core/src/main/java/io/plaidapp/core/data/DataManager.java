package io.plaidapp.core.data;

import io.plaidapp.core.data.prefs.SourcesRepository;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource;
import io.plaidapp.core.designernews.domain.LoadStoriesUseCase;
import io.plaidapp.core.designernews.domain.SearchStoriesUseCase;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.producthunt.data.ProductHuntSourceItem;
import io.plaidapp.core.producthunt.domain.LoadPostsUseCase;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import io.plaidapp.core.util.exhaustive;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;
import kotlinx.coroutines.launch;
import kotlinx.coroutines.withContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

private class InFlightRequestData {
    private String key;
    private int page;

    public InFlightRequestData(String key, int page) {
        this.key = key;
        this.page = page;
    }
}

public class DataManager implements DataLoadingSubject {

    private LoadStoriesUseCase loadStories;
    private LoadPostsUseCase loadPosts;
    private SearchStoriesUseCase searchStories;
    private ShotsRepository shotsRepository;
    private SourcesRepository sourcesRepository;
    private CoroutinesDispatcherProvider dispatcherProvider;

    private SupervisorJob parentJob;
    private CoroutineScope scope;

    private Map<InFlightRequestData, Job> parentJobs;

    private AtomicInteger loadingCount;
    private List<DataLoadingSubject.DataLoadingCallbacks> loadingCallbacks;
    private OnDataLoadedCallback<List<PlaidItem>> onDataLoadedCallback;
    private Map<String, Integer> pageIndexes;

    private FiltersChangedCallback filterListener;

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

        parentJob = new SupervisorJob();
        scope = new CoroutineScope(dispatcherProvider.getComputation() + parentJob);

        parentJobs = new HashMap<>();

        loadingCount = new AtomicInteger(0);
        loadingCallbacks = new ArrayList<>();
        onDataLoadedCallback = null;
        pageIndexes = new HashMap<>();

        filterListener = new FiltersChangedCallback() {
            @Override
            public void onFiltersChanged(SourceItem changedFilter) {
                if (changedFilter.getActive()) {
                    loadSource(changedFilter);
                } else {
                    String key = changedFilter.getKey();
                    for (Map.Entry<InFlightRequestData, Job> entry : parentJobs.entrySet()) {
                        InFlightRequestData requestData = entry.getKey();
                        if (requestData.getKey().equals(key)) {
                            Job job = entry.getValue();
                            job.cancel();
                            parentJobs.remove(requestData);
                        }
                    }

                    pageIndexes.put(key, 0);
                }
            }
        };

        sourcesRepository.registerFilterChangedCallback(filterListener);

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
        scope.launch(dispatcherProvider.getComputation(), () -> {
            List<SourceItem> sources = sourcesRepository.getSources();
            for (SourceItem source : sources) {
                loadSource(source);
            }
        });
    }

    public void cancelLoading() {
        for (Job job : parentJobs.values()) {
            job.cancel();
        }
        parentJobs.clear();
    }

    private void loadSource(SourceItem source) {
        if (source.getActive()) {
            loadStarted();
            int page = getNextPageIndex(source.getKey());

            InFlightRequestData data = new InFlightRequestData(source.getKey(), page);
            if (source.getKey().equals(DesignerNewsSearchSource.Companion.getSOURCE_DESIGNER_NEWS_POPULAR())) {
                parentJobs.put(data, launchLoadDesignerNewsStories(data));
            } else if (source.getKey().equals(ProductHuntSourceItem.Companion.getSOURCE_PRODUCT_HUNT())) {
                parentJobs.put(data, launchLoadProductHunt(data));
            } else if (source instanceof DribbbleSourceItem) {
                parentJobs.put(data, loadDribbbleSearch((DribbbleSourceItem) source, data));
            } else if (source instanceof DesignerNewsSearchSource) {
                parentJobs.put(data, loadDesignerNewsSearch((DesignerNewsSearchSource) source, data));
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
        return pageIndexes.get(key) != 0;
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
        return scope.launch(() -> {
            Result<List<PlaidItem>> result = loadStories.invoke(data.getPage());
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), DesignerNewsSearchSource.Companion.getSOURCE_DESIGNER_NEWS_POPULAR(), data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        });
    }

    private Job loadDesignerNewsSearch(DesignerNewsSearchSource source, InFlightRequestData data) {
        return scope.launch(() -> {
            Result<List<PlaidItem>> result = searchStories.invoke(source.getKey(), data.getPage());
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), source.getKey(), data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        });
    }

    private Job loadDribbbleSearch(DribbbleSourceItem source, InFlightRequestData data) {
        return scope.launch(() -> {
            Result<List<PlaidItem>> result = shotsRepository.search(source.getQuery(), data.getPage());
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), source.getKey(), data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        });
    }

    private Job launchLoadProductHunt(InFlightRequestData data) {
        return scope.launch(() -> {
            Result<List<PlaidItem>> result = loadPosts.invoke(data.getPage() - 1);
            if (result instanceof Result.Success) {
                sourceLoaded(result.getData(), ProductHuntSourceItem.Companion.getSOURCE_PRODUCT_HUNT(), data);
            } else if (result instanceof Result.Error) {
                loadFailed(data);
            }
        });
    }

    @Override
    public void registerCallback(DataLoadingSubject.DataLoadingCallbacks callback) {
        loadingCallbacks.add(callback);
    }

    private void loadStarted() {
        if (loadingCount.getAndIncrement() == 0) {
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