




package io.plaidapp.core.data.prefs;

import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import kotlin.Lazy;
import kotlin.LazyThreadSafetyMode;
import kotlin.jvm.Volatile;

public class SourcesRepository {

    private final List<SourceItem> defaultSources;
    private final SourcesLocalDataSource dataSource;
    private final CoroutinesDispatcherProvider dispatcherProvider;
    private final Lazy<List<SourceItem>> cache;
    private final List<FiltersChangedCallback> callbacks;

    public SourcesRepository(List<SourceItem> defaultSources,
                             SourcesLocalDataSource dataSource,
                             CoroutinesDispatcherProvider dispatcherProvider) {
        this.defaultSources = defaultSources;
        this.dataSource = dataSource;
        this.dispatcherProvider = dispatcherProvider;
        this.cache = new Lazy<>(() -> {
            List<SourceItem> sourceList = Collections.synchronizedList(new ArrayList<>());
            return sourceList;
        }, LazyThreadSafetyMode.SYNCHRONIZED);
        this.callbacks = new ArrayList<>();
    }

    public void registerFilterChangedCallback(FiltersChangedCallback callback) {
        callbacks.add(callback);
    }

    public List<SourceItem> getSources() {
        return getSourcesSync();
    }

    @Deprecated
    public List<SourceItem> getSourcesSync() {
        if (!cache.getValue().isEmpty()) {
            return cache.getValue();
        }

        List<String> sourceKeys = dataSource.getKeys();
        if (sourceKeys == null) {
            addSources(defaultSources);
            return defaultSources;
        }

        List<SourceItem> sources = new ArrayList<>();
        for (String sourceKey : sourceKeys) {
            boolean activeState = dataSource.getSourceActiveState(sourceKey);
            if (sourceKey.startsWith(DribbbleSourceItem.DRIBBBLE_QUERY_PREFIX)) {
                String query = sourceKey.replace(DribbbleSourceItem.DRIBBBLE_QUERY_PREFIX, "");
                sources.add(new DribbbleSourceItem(query, activeState));
            } else if (sourceKey.startsWith(DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX)) {
                String query = sourceKey.replace(DesignerNewsSearchSource.DESIGNER_NEWS_QUERY_PREFIX, "");
                sources.add(new DesignerNewsSearchSource(query, activeState));
            } else if (isDeprecatedDesignerNewsSource(sourceKey)) {
                dataSource.removeSource(sourceKey);
            } else if (isDeprecatedDribbbleV1Source(sourceKey)) {
                dataSource.removeSource(sourceKey);
            } else {
                SourceItem sourceItem = getSourceFromDefaults(sourceKey, activeState);
                if (sourceItem != null) {
                    sources.add(sourceItem);
                }
            }
        }
        sources.sort(SourceItem::compare);
        cache.getValue().addAll(sources);
        dispatchSourcesUpdated();
        return cache.getValue();
    }

    public void addSources(List<SourceItem> sources) {
        for (SourceItem source : sources) {
            dataSource.addSource(source.getKey(), source.isActive());
        }
        cache.getValue().addAll(sources);
        dispatchSourcesUpdated();
    }

    public void addOrMarkActiveSources(List<SourceItem> sources) {
        List<SourceItem> sourcesToAdd = new ArrayList<>();
        for (SourceItem toAdd : sources) {
            boolean sourcePresent = false;
            for (int i = 0; i < cache.getValue().size(); i++) {
                SourceItem existing = cache.getValue().get(i);
                if (existing.getClass().equals(toAdd.getClass()) &&
                        existing.getKey().equalsIgnoreCase(toAdd.getKey())) {
                    sourcePresent = true;
                    if (!existing.isActive()) {
                        changeSourceActiveState(existing.getKey());
                    }
                }
            }
            if (!sourcePresent) {
                sourcesToAdd.add(toAdd);
            }
        }
        addSources(sourcesToAdd);
    }

    public void changeSourceActiveState(String sourceKey) {
        SourceItem source = cache.getValue().stream()
                .filter(item -> item.getKey().equals(sourceKey))
                .findFirst()
                .orElse(null);
        if (source != null) {
            boolean newActiveState = !source.isActive();
            source.setActive(newActiveState);
            dataSource.updateSource(sourceKey, newActiveState);
            dispatchSourceChanged(source);
        }
        dispatchSourcesUpdated();
    }

    public void removeSource(String sourceKey) {
        dataSource.removeSource(sourceKey);
        Iterator<SourceItem> iterator = cache.getValue().iterator();
        while (iterator.hasNext()) {
            SourceItem item = iterator.next();
            if (item.getKey().equals(sourceKey)) {
                iterator.remove();
                dispatchSourceRemoved(sourceKey);
                break;
            }
        }
        dispatchSourcesUpdated();
    }

    public int getActiveSourcesCount() {
        return (int) getSourcesSync().stream().filter(SourceItem::isActive).count();
    }

    private SourceItem getSourceFromDefaults(String key, boolean active) {
        return defaultSources.stream()
                .filter(source -> source.getKey().equals(key))
                .findFirst()
                .map(source -> {
                    source.setActive(active);
                    return source;
                })
                .orElse(null);
    }

    private void dispatchSourcesUpdated() {
        callbacks.forEach(FiltersChangedCallback::onFiltersUpdated);
    }

    private void dispatchSourceChanged(SourceItem source) {
        callbacks.forEach(callback -> callback.onFiltersChanged(source));
    }

    private void dispatchSourceRemoved(String sourceKey) {
        callbacks.forEach(callback -> callback.onFilterRemoved(sourceKey));
    }

    public static SourcesRepository getInstance(List<SourceItem> defaultSources,
                                                SourcesLocalDataSource dataSource,
                                                CoroutinesDispatcherProvider dispatcherProvider) {
        SourcesRepository instance = SourcesRepository.INSTANCE;
        if (instance == null) {
            synchronized (SourcesRepository.class) {
                instance = SourcesRepository.INSTANCE;
                if (instance == null) {
                    instance = new SourcesRepository(defaultSources, dataSource, dispatcherProvider);
                    SourcesRepository.INSTANCE = instance;
                }
            }
        }
        return instance;
    }

    @Volatile
    private static SourcesRepository INSTANCE;
}