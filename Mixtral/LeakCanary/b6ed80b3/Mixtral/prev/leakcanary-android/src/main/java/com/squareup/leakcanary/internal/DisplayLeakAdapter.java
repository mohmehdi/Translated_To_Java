
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
import com.squareup.leakcanary.LeakTraceElement.Type.STATIC_FIELD;
import com.squareup.leakcanary.Reachability;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.SquigglySpan;

public class DisplayLeakAdapter extends BaseAdapter {

  private boolean[] opened;
  private LeakTrace leakTrace;
  private String referenceKey;
  private String referenceName;
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
    opened = new boolean[0];
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    Context context = parent.getContext();
    if (getItemViewType(position) == TOP_ROW) {
      if (convertView == null) {
        convertView = LayoutInflater.from(context)
          .inflate(R.layout.leak_canary_ref_top_row, parent, false);
      }
      TextView textView = convertView.findViewById(R.id.leak_canary_row_text);
      textView.setText(context.getPackageName());
    } else {
      if (convertView == null) {
        convertView = LayoutInflater.from(context)
          .inflate(R.layout.leak_canary_ref_row, parent, false);
      }

      TextView titleView = convertView.findViewById(R.id.leak_canary_row_title);
      TextView detailView = convertView.findViewById(R.id.leak_canary_row_details);
      DisplayLeakConnectorView connector = convertView.findViewById(R.id.leak_canary_row_connector);
      MoreDetailsView moreDetailsView = convertView.findViewById(R.id.leak_canary_row_more);

      connector.setType(getConnectorType(position));
      moreDetailsView.setOpened(opened[position]);

      if (opened[position]) {
        detailView.setVisibility(View.VISIBLE);
      } else {
        detailView.setVisibility(View.GONE);
      }

      Resources resources = convertView.getResources();
      if (position == 1) {
        String helpColorHexString = /* your value here */ ;
        titleView.setText(Html.fromHtml(
          "<font color='" +
          helpColorHexString +
          "'>" +
          "<b>" + resources.getString(R.string.leak_canary_help_title) + "</b>" +
          "</font>"
        ));
        SpannableStringBuilder detailText = (SpannableStringBuilder) Html.fromHtml(
          resources.getString(R.string.leak_canary_help_detail)
        );
        SquigglySpan.replaceUnderlineSpans(detailText, resources);
        detailView.setText(detailText);
      } else {
        boolean isLeakingInstance = position == count - 1;
        Object element = getItem(position);

        int reachability = leakTrace.expectedReachability[elementIndex(position)];
        boolean maybeLeakCause;
        if (isLeakingInstance || reachability == Reachability.Status.UNREACHABLE) {
          maybeLeakCause = false;
        } else {
          int nextReachability = leakTrace.expectedReachability[elementIndex(position + 1)];
          maybeLeakCause = nextReachability.status != Reachability.Status.REACHABLE;
        }

        String htmlTitle = htmlTitle(element, maybeLeakCause, resources);

        titleView.setText(htmlTitle);

        if (opened[position]) {
          String htmlDetail = htmlDetails(isLeakingInstance, element);
          detailView.setText(htmlDetail);
        }
      }
    }

    return convertView;
  }

  private Spanned htmlTitle(LeakTraceElement element, boolean maybeLeakCause, Resources resources) {
    String htmlString = "";

    String simpleName = element.getSimpleClassName();
    simpleName = simpleName.replace("[]", "[ ]");

    String styledClassName = "<font color='" + classNameColorHexString + "'>" + simpleName + "</font>";

    Reference reference = element.getReference();
    if (reference != null) {
      String referenceName = reference.getDisplayName().replace("<", "&lt;")
        .replace(">", "&gt;");

      if (maybeLeakCause) {
        referenceName = "<u><font color='" + leakColorHexString + "'>" + referenceName + "</font></u>";
      } else {
        referenceName = "<font color='" + referenceColorHexString + "'>" + referenceName + "</font>";
      }

      if (reference.getType() == Reference.TYPE_STATIC_FIELD) {
        referenceName = "<i>" + referenceName + "</i>";
      }

      String classAndReference = "$styledClassName.$referenceName";

      if (maybeLeakCause) {
        classAndReference = "<b>$classAndReference</b>";
      }

      htmlString += classAndReference;
    } else {
      htmlString += styledClassName;
    }

    Reference exclusion = element.getExclusion();
    if (exclusion != null) {
      htmlString += " (excluded)";
    }

    SpannableStringBuilder builder = (SpannableStringBuilder) Html.fromHtml(htmlString);
    if (maybeLeakCause) {
      SquigglySpan.replaceUnderlineSpans(builder, resources);
    }

    return builder;
  }

  private Spanned htmlDetails(boolean isLeakingInstance, LeakTraceElement element) {
    String htmlString = "";
    if (element.extra != null) {
      htmlString += " <font color='" + extraColorHexString + "'>" + element.extra + "</font>";
    }

    Exclusion exclusion = element.exclusion;
    if (exclusion != null) {
      htmlString += "<br/><br/>Excluded by rule";
      if (exclusion.name != null) {
        htmlString += " <font color='#ffffff'>" + exclusion.name + "</font>";
      }
      htmlString += " matching <font color='#f3cf83'>" + exclusion.matching + "</font>";
      if (exclusion.reason != null) {
        htmlString += " because <font color='#f3cf83'>" + exclusion.reason + "</font>";
      }
    }
    htmlString += ("<br>" +
      "<font color='" + extraColorHexString + "'>" +
      element.toDetailedString().replace("\n", "<br>") +
      "</font>");

    if (isLeakingInstance && referenceName != null && !referenceName.isEmpty()) {
      htmlString += " <font color='" + extraColorHexString + "'>" + referenceName + "</font>";
    }

    return Html.fromHtml(htmlString);
  }

  private Type getConnectorType(int position) {
    if (position == 1) {
      return Type.HELP;
    } else if (position == 2) {
      List < Reachability > expectedReachability = leakTrace.getExpectedReachability();
      if (expectedReachability.size() == 1) {
        return Type.START_LAST_REACHABLE;
      }
      Reachability nextReachability = expectedReachability.get(elementIndex(position + 1));
      if (nextReachability.getStatus() != Reachability.Status.REACHABLE) {
        return Type.START_LAST_REACHABLE;
      }
      return Type.START;
    } else {
      boolean isLeakingInstance = position == count - 1;
      if (isLeakingInstance) {
        List < Reachability > expectedReachability = leakTrace.getExpectedReachability();
        Reachability previousReachability = expectedReachability.get(elementIndex(position - 1));
        if (previousReachability.getStatus() != Reachability.Status.UNREACHABLE) {
          return Type.END_FIRST_UNREACHABLE;
        } else {
          return Type.END;
        }
      } else {
        List < Reachability > expectedReachability = leakTrace.getExpectedReachability();
        Reachability reachability = expectedReachability.get(elementIndex(position));
        Reachability.Status status = reachability.getStatus();
        if (status == Reachability.Status.UNKNOWN) {
          return Type.NODE_UNKNOWN;
        } else if (status == Reachability.Status.REACHABLE) {
          List < Reachability > expectedReachability = leakTrace.getExpectedReachability();
          Reachability nextReachability = expectedReachability.get(elementIndex(position + 1));
          if (nextReachability.getStatus() != Reachability.Status.REACHABLE) {
            return Type.NODE_LAST_REACHABLE;
          } else {
            return Type.NODE_REACHABLE;
          }
        } else if (status == Reachability.Status.UNREACHABLE) {
          List < Reachability > expectedReachability = leakTrace.getExpectedReachability();
          Reachability previousReachability = expectedReachability.get(elementIndex(position - 1));
          if (previousReachability.getStatus() != Reachability.Status.UNREACHABLE) {
            return Type.NODE_FIRST_UNREACHABLE;
          } else {
            return Type.NODE_UNREACHABLE;
          }
        } else {
          throw new IllegalStateException("Unknown value: " + status);
        }
      }
    }
  }

  public void update(LeakTrace leakTrace, String referenceKey, String referenceName) {
    if (referenceKey.equals(this.referenceKey)) {
      return;
    }
    this.referenceKey = referenceKey;
    this.referenceName = referenceName;
    this.leakTrace = leakTrace;
    this.opened = new boolean[2 + (leakTrace != null ? leakTrace.elements.size : 0)];
    notifyDataSetChanged();
  }

  public void toggleRow(int position) {
    opened[position] = !opened[position];
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return leakTrace != null ? 2 + leakTrace.elements.size : 2;
  }

  @Override
  public LeakTraceElement getItem(int position) {
    if (getItemViewType(position) == TOP_ROW) {
      return null;
    }
    if (position == 1) {
      return null;
    } else {
      return leakTrace != null ? leakTrace.elements.get(elementIndex(position)) : null;
    }
  }

  private int elementIndex(int position) {
    return position - 2;
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
    return (long) position;
  }
  private static final int TOP_ROW = 0;
  private static final int NORMAL_ROW = 1;

  private static String hexStringColor(Resources resources, @ColorRes int colorResId) {
    return String.format("#%06X", 0xFFFFFF & resources.getColor(colorResId));
  }

  @Suppress("unchecked")
  private static T findById(View view, int id) {
    return (T) view.findViewById(id);
  }

}