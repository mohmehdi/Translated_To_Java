package io.plaidapp.core.data;

import androidx.annotation.DrawableRes;
import java.util.Comparator;

public abstract class SourceItem {
    private String key;
    private int sortOrder;
    private String name;
    @DrawableRes
    private int iconRes;
    private boolean active;
    private boolean isSwipeDismissable;

    public SourceItem(String key, int sortOrder, String name, int iconRes, boolean active, boolean isSwipeDismissable) {
        this.key = key;
        this.sortOrder = sortOrder;
        this.name = name;
        this.iconRes = iconRes;
        this.active = active;
        this.isSwipeDismissable = isSwipeDismissable;
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
            return lhs.sortOrder - rhs.sortOrder;
        }
    }
}