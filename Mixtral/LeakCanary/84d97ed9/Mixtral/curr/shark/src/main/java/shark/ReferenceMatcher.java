

package shark;

import java.util.function.Predicate;

public abstract class ReferenceMatcher {

    protected abstract ReferencePattern pattern;

}

record LibraryLeakReferenceMatcher(ReferencePattern pattern, String description, Predicate<HeapGraph> patternApplies) implements ReferenceMatcher {}

class IgnoredReferenceMatcher extends ReferenceMatcher {

    private final ReferencePattern pattern;

    public IgnoredReferenceMatcher(ReferencePattern pattern) {
        this.pattern = pattern;
    }

}