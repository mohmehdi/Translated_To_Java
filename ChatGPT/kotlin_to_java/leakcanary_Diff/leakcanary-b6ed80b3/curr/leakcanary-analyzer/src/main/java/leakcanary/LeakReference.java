

package leakcanary;

import java.io.Serializable;

public class LeakReference implements Serializable {

  private final LeakTraceElement.Type type;
  private final String name;
  private final String value;

  public LeakReference(LeakTraceElement.Type type, String name, String value) {
    this.type = type;
    this.name = name;
    this.value = value;
  }


  @Override
  public String toString() {
    switch (type) {
      case ARRAY_ENTRY:
      case INSTANCE_FIELD:
        return getDisplayName() + " = " + value;
      case STATIC_FIELD:
        return "static " + getDisplayName() + " = " + value;
      case LOCAL:
        return getDisplayName();
      default:
        return "";
    }
  }
}