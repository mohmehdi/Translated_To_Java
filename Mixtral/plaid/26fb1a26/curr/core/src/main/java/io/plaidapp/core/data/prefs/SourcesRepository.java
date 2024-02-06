




package io.plaidapp.core.data.prefs;

import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.data.SourceLocalDataSource;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSourceItem;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.ui.filter.FiltersChangedCallback;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public class SourcesRepository {

    private final List<SourceItem> defaultSources;
    private final SourceLocalDataSource dataSource;
    private final CoroutinesDispatcherProvider dispatcherProvider;
    private final List<SourceItem> cache = new ArrayList<>();
    private final List<FiltersChangedCallback> callbacks = new ArrayList<>();

    public SourcesRepository(
            List<SourceItem> defaultSources,
            SourceLocalDataSource dataSource,
            CoroutinesDispatcherProvider dispatcherProvider) {
        this.defaultSources = defaultSources;
        this.dataSource = dataSource;
        this.dispatcherProvider = dispatcherProvider;
    }

    public void registerFilterChangedCallback(FiltersChangedCallback callback) {
        callbacks.add(callback);
    }

    public List<SourceItem> getSources() {
        return getSourcesSync();
    }

    public List<SourceItem> getSourcesSync() {
        if (!cache.isEmpty()) {
            return cache;
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
            } else if (sourceKey.startsWith(DesignerNewsSearchSourceItem.DESIGNER_NEWS_QUERY_PREFIX)) {
                String query = sourceKey.replace(DesignerNewsSearchSourceItem.DESIGNER_NEWS_QUERY_PREFIX, "");
                sources.add(new DesignerNewsSearchSourceItem(query, activeState));
            } else if (isDeprecatedDesignerNewsSource(sourceKey)) {
                dataSource.removeSource(sourceKey);
            } else if (isDeprecatedDribbbleV1Source(sourceKey)) {
                dataSource.removeSource(sourceKey);
            } else {
                SourceItem source = getSourceFromDefaults(sourceKey, activeState);
                if (source != null) {
                    sources.add(source);
                }
            }
        }
        Collections.sort(sources, SourceItem::compare);
        cache.addAll(sources);
        dispatchSourcesUpdated();
        return cache;
    }

    public void addSources(List<SourceItem> sources) {
        for (SourceItem source : sources) {
            dataSource.addSource(source.key, source.active);
        }
        cache.addAll(sources);
        dispatchSourcesUpdated();
    }

    public void addOrMarkActiveSources(List<SourceItem> sources) {
        List<SourceItem> sourcesToAdd = new ArrayList<>();
        for (SourceItem toAdd : sources) {
            boolean sourcePresent = false;
            for (int i = 0; i < cache.size(); i++) {
                SourceItem existing = cache.get(i);
                if (existing.getClass() == toAdd.getClass() &&
                        existing.key.equalsIgnoreCase(toAdd.key)) {
                    sourcePresent = true;
                    if (!existing.active) {
                        changeSourceActiveState(existing.key);
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
        for (int i = 0; i < cache.size(); i++) {
            SourceItem item = cache.get(i);
            if (item.key.equals(sourceKey)) {
                boolean newActiveState = !item.active;
                item.active = newActiveState;
                dataSource.updateSource(sourceKey, newActiveState);
                dispatchSourceChanged(item);
                break;
            }
        }
        dispatchSourcesUpdated();
    }

    public void removeSource(String sourceKey) {
        dataSource.removeSource(sourceKey);
        for (int i = 0; i < cache.size(); i++) {
            SourceItem item = cache.get(i);
            if (item.key.equals(sourceKey)) {
                cache.remove(i);
                break;
            }
        }
        dispatchSourceRemoved(sourceKey);
        dispatchSourcesUpdated();
    }

    public int getActiveSourcesCount() {
        return getSourcesSync().stream()
                .filter(SourceItem::isActive)
                .mapToInt(source -> 1)
                .sum();
    }

    private SourceItem getSourceFromDefaults(String key, boolean active) {
        for (SourceItem source : defaultSources) {
            if (source.key.equals(key)) {
                source.active = active;
                return source;
            }
        }
        return null;
    }

    private void dispatchSourcesUpdated() {
        for (FiltersChangedCallback callback : callbacks) {
            callback.onFiltersUpdated(cache);
        }
    }

    private void dispatchSourceChanged(SourceItem source) {
        for (FiltersChangedCallback callback : callbacks) {
            callback.onFiltersChanged(source);
        }
    }

    private void dispatchSourceRemoved(String sourceKey) {
        for (FiltersChangedCallback callback : callbacks) {
            callback.onFilterRemoved(sourceKey);
        }
    }

    public static SourcesRepository getInstance(
            List<SourceItem> defaultSources,
            SourcesLocalDataSource dataSource,
            CoroutinesDispatcherProvider dispatcherProvider) {
        SourcesRepository instance = INSTANCE;
        if (instance == null) {
            synchronized (SourcesRepository.class) {
                instance = INSTANCE;
                if (instance == null) {
                    instance = new SourcesRepository(defaultSources, dataSource, dispatcherProvider);
                    INSTANCE = instance;
                }
            }
        }
        return instance;
    }

    private static volatile SourcesRepository INSTANCE;
}