

package leakcanary;

import leakcanary.Reachability.Status;

import java.io.Serializable;
import java.util.List;

public data class LeakTrace implements Serializable {
  
  public List<LeakTraceElement> elements;
  public List<Reachability> expectedReachability;

  @Override
  public String toString() {
    String leakInfo = "┬" + "\n";
    LeakTraceElement lastElement = elements.get(elements.size() - 1);
    Reachability lastReachability = expectedReachability.get(expectedReachability.size() - 1);
    for (int i = 0; i < elements.size() - 1; i++) {
      LeakTraceElement leakTraceElement = elements.get(i);
      Reachability currentReachability = expectedReachability.get(i);

      leakInfo += "├─ " + leakTraceElement.className + "\n" +
          "│" + getReachabilityString(currentReachability) + "\n" +
          "│" + getPossibleLeakString(currentReachability, leakTraceElement, i) + "\n";
    }
    leakInfo += "╰→ " + lastElement.className + "\n" +
        DEFAULT_NEWLINE_SPACE + getReachabilityString(lastReachability);

    return leakInfo;
  }

  public String toDetailedString() {
    return elements.stream()
        .map(LeakTraceElement::toDetailedString)
        .collect(Collectors.joining());
  }

  private String getPossibleLeakString(Reachability reachability, LeakTraceElement leakTraceElement, int index) {
    boolean maybeLeakCause;
    switch (reachability.status) {
      case UNKNOWN:
        maybeLeakCause = true;
        break;
      case REACHABLE:
        if (index < elements.size() - 1) {
          Reachability nextReachability = expectedReachability.get(index + 1);
          maybeLeakCause = nextReachability.status != Status.REACHABLE;
        } else {
          maybeLeakCause = true;
        }
        break;
      default:
        maybeLeakCause = false;
    }
    return DEFAULT_NEWLINE_SPACE + "↓" + " " + leakTraceElement.toString(maybeLeakCause);
  }

  private String getReachabilityString(Reachability reachability) {
    return DEFAULT_NEWLINE_SPACE + "Leaking: " + switch (reachability.status) {
      case UNKNOWN -> "UNKNOWN";
      case REACHABLE -> "NO (" + reachability.reason + ")";
      case UNREACHABLE -> "YES (" + reachability.reason + ")";
    };
  }

  private static final String DEFAULT_NEWLINE_SPACE = "                 ";
  private static final char ZERO_WIDTH_SPACE = '\u200b';
}