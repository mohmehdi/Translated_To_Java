package shark.internal;

import shark.PrimitiveType;

internal abstract class IndexedObject {
    abstract long position;
    abstract long recordSize;

    static class IndexedClass extends IndexedObject {
        long position;
        long superclassId;
        int instanceSize;
        long recordSize;

        IndexedClass(long position, long superclassId, int instanceSize, long recordSize) {
            this.position = position;
            this.superclassId = superclassId;
            this.instanceSize = instanceSize;
            this.recordSize = recordSize;
        }
    }

    static class IndexedInstance extends IndexedObject {
        long position;
        long classId;
        long recordSize;

        IndexedInstance(long position, long classId, long recordSize) {
            this.position = position;
            this.classId = classId;
            this.recordSize = recordSize;
        }
    }

    static class IndexedObjectArray extends IndexedObject {
        long position;
        long arrayClassId;
        long recordSize;

        IndexedObjectArray(long position, long arrayClassId, long recordSize) {
            this.position = position;
            this.arrayClassId = arrayClassId;
            this.recordSize = recordSize;
        }
    }

    static class IndexedPrimitiveArray extends IndexedObject {
        long position;
        byte primitiveTypeOrdinal;
        long recordSize;

        IndexedPrimitiveArray(long position, PrimitiveType primitiveType, long recordSize) {
            this.position = position;
            this.primitiveTypeOrdinal = (byte) primitiveType.ordinal();
            this.recordSize = recordSize;
        }

        PrimitiveType getPrimitiveType() {
            return PrimitiveType.values()[primitiveTypeOrdinal];
        }
    }
}