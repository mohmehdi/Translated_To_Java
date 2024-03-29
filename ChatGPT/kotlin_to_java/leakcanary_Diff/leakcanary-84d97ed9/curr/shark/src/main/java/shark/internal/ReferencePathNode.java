
package shark.internal;

import shark.GcRoot;
import shark.LeakReference;
import shark.LibraryLeakReferenceMatcher;

internal abstract class ReferencePathNode {

    public static class RootNode extends ReferencePathNode {
        private final GcRoot gcRoot;
        private final long instance;

        public RootNode(GcRoot gcRoot, long instance) {
            this.gcRoot = gcRoot;
            this.instance = instance;
        }

    }

    public static abstract class ChildNode extends ReferencePathNode {


        public static class LibraryLeakNode extends ChildNode {
            private final long instance;
            private final ReferencePathNode parent;
            private final LeakReference referenceFromParent;
            private final LibraryLeakReferenceMatcher matcher;

            public LibraryLeakNode(long instance, ReferencePathNode parent, LeakReference referenceFromParent, LibraryLeakReferenceMatcher matcher) {
                this.instance = instance;
                this.parent = parent;
                this.referenceFromParent = referenceFromParent;
                this.matcher = matcher;
            }


        }

        public static class NormalNode extends ChildNode {
            private final long instance;
            private final ReferencePathNode parent;
            private final LeakReference referenceFromParent;

            public NormalNode(long instance, ReferencePathNode parent, LeakReference referenceFromParent) {
                this.instance = instance;
                this.parent = parent;
                this.referenceFromParent = referenceFromParent;
            }

        }
    }
}