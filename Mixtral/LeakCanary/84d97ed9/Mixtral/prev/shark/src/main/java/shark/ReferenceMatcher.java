

package shark;

import shark.ReferencePattern;

public abstract class ReferenceMatcher {

    private final Pattern pattern;

    public static class LibraryLeakReferenceMatcher extends ReferenceMatcher {
        private final ReferencePattern pattern;
        private final String description;
        private final HeapGraphPatternApplier patternApplies;

        public LibraryLeakReferenceMatcher(ReferencePattern pattern, String description, HeapGraphPatternApplier patternApplies) {
            this.pattern = pattern;
            this.description = description;
            this.patternApplies = patternApplies;
        }

        public LibraryLeakReferenceMatcher(ReferencePattern pattern, String description) {
            this(pattern, description, heapGraph -> true);
        }

        public boolean patternApplies(HeapGraph heapGraph) {
            return patternApplies.apply(heapGraph);
        }
    }

    public static class IgnoredReferenceMatcher extends ReferenceMatcher {
        private final ReferencePattern pattern;

        public IgnoredReferenceMatcher(ReferencePattern pattern) {
            this.pattern = pattern;
        }

    }
}