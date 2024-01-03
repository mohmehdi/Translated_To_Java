package io.plaidapp.core.data;

import androidx.annotation.DrawableRes;
import java.util.Comparator;

public abstract class SourceItem {
    private String id;
    private String key;
    private int sortOrder;
    private String name;
    @DrawableRes
    private int iconRes;
    private boolean active;
    private boolean isSwipeDismissable;

    public SourceItem(String id, String key, int sortOrder, String name, int iconRes, boolean active, boolean isSwipeDismissable) {
        this.id = id;
        this.key = key;
        this.sortOrder = sortOrder;
        this.name = name;
        this.iconRes = iconRes;
        this.active = active;
        this.isSwipeDismissable = isSwipeDismissable;
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getName() {
        return name;
    }

    public int getIconRes() {
        return iconRes;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSwipeDismissable() {
        return isSwipeDismissable;
    }

    @Override
    public String toString() {
        return "SourceItem{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                '}';
    }

    public static class SourceComparator implements Comparator<SourceItem> {
        @Override
        public int compare(SourceItem lhs, SourceItem rhs) {
            return lhs.getSortOrder() - rhs.getSortOrder();
        }
    }
}