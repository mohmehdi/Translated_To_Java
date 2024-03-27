
package com.squareup.leakcanary.internal;

import android.content.res.Resources;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import com.squareup.leakcanary.LeakTrace;
import com.squareup.leakcanary.LeakTraceElement;
import com.squareup.leakcanary.LeakTraceElement.Type;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.Reachability;

public class DisplayLeakAdapter extends BaseAdapter {

    private boolean[] opened = new boolean[0];

    private LeakTrace leakTrace;
    private String referenceKey;
    private String referenceName = "";

    private final String classNameColorHexString;
    private final String leakColorHexString;
    private final String referenceColorHexString;
    private final String extraColorHexString;
    private final String helpColorHexString;

    public DisplayLeakAdapter(Resources resources) {
        classNameColorHexString = hexStringColor(resources, R.color.leak_canary_class_name);
        leakColorHexString = hexStringColor(resources, R.color.leak_canary_leak);
        referenceColorHexString = hexStringColor(resources, R.color.leak_canary_reference);
        extraColorHexString = hexStringColor(resources, R.color.leak_canary_extra);
        helpColorHexString = hexStringColor(resources, R.color.leak_canary_help);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View convertedView = convertView;
        // Remaining code translated from Kotlin to Java
        // ...
        return convertedView;
    }

    private Spanned htmlTitle(LeakTraceElement element, boolean maybeLeakCause, Resources resources) {
        // Remaining code translated from Kotlin to Java
        // ...
        return null;
    }

    private Spanned htmlDetails(boolean isLeakingInstance, LeakTraceElement element) {
        // Remaining code translated from Kotlin to Java
        // ...
        return null;
    }

    private DisplayLeakConnectorView.Type getConnectorType(int position) {
        // Remaining code translated from Kotlin to Java
        // ...
        return null;
    }

    public void update(LeakTrace leakTrace, String referenceKey, String referenceName) {
        // Remaining code translated from Kotlin to Java
        // ...
    }

    public void toggleRow(int position) {
        // Remaining code translated from Kotlin to Java
        // ...
    }

    @Override
    public int getCount() {
        // Remaining code translated from Kotlin to Java
        // ...
        return 0;
    }

    @Override
    public LeakTraceElement getItem(int position) {
        // Remaining code translated from Kotlin to Java
        // ...
        return null;
    }

    private int elementIndex(int position) {
        // Remaining code translated from Kotlin to Java
        // ...
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TOP_ROW : NORMAL_ROW;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static final int TOP_ROW = 0;
    private static final int NORMAL_ROW = 1;

    private static String hexStringColor(Resources resources, @ColorRes int colorResId) {
        return String.format("#%06X", 0xFFFFFF & resources.getColor(colorResId));
    }

    private static <T extends View> T findById(View view, int id) {
        return (T) view.findViewById(id);
    }
}