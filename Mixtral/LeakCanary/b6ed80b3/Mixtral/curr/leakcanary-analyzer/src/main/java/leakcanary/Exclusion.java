

package leakcanary;

import leakcanary.ExcludedRefs.ParamsBuilder;
import java.io.Serializable;

public class Exclusion implements Serializable {
  private final String name;
  private final String reason;
  private final boolean alwaysExclude;
  private final String matching;

  public Exclusion(ParamsBuilder builder) {
    this.name = builder.name;
    this.reason = builder.reason;
    this.alwaysExclude = builder.alwaysExclude;
    this.matching = builder.matching;
  }
}