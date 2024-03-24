

package shark.internal;

import shark.PrimitiveType;

internal abstract class IndexedObject {
    public abstract long getPosition();

    public static class IndexedClass extends IndexedObject {
        private final long position;
        private final long superclassId;
        private final int instanceSize;

        public IndexedClass(long position, long superclassId, int instanceSize) {
            this.position = position;
            this.superclassId = superclassId;
            this.instanceSize = instanceSize;
        }

        @Override
        public long getPosition() {
            return position;
        }
    }

    public static class IndexedInstance extends IndexedObject {
        private final long position;
        private final long classId;

        public IndexedInstance(long position, long classId) {
            this.position = position;
            this.classId = classId;
        }

        @Override
        public long getPosition() {
            return position;
        }
    }

    public static class IndexedObjectArray extends IndexedObject {
        private final long position;
        private final long arrayClassId;

        public IndexedObjectArray(long position, long arrayClassId) {
            this.position = position;
            this.arrayClassId = arrayClassId;
        }

        @Override
        public long getPosition() {
            return position;
        }
    }

    public static class IndexedPrimitiveArray extends IndexedObject {
        private final long position;
        private final byte primitiveTypeOrdinal;
        private final PrimitiveType primitiveType;

        public IndexedPrimitiveArray(long position, PrimitiveType primitiveType) {
            this.position = position;
            this.primitiveTypeOrdinal = (byte) primitiveType.ordinal();
            this.primitiveType = PrimitiveType.values()[primitiveTypeOrdinal];
        }

        @Override
        public long getPosition() {
            return position;
        }
    }
}