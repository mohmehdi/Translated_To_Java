
//⚠!#!
//--------------------Class--------------------
//-------------------Functions-----------------
//Functions extra in Java:
//+ LeakReference(Type, String, String)
//+ getDisplayName()

//-------------------Extra---------------------
//---------------------------------------------
package leakcanary;

import leakcanary.LeakTraceElement.Type;

public class LeakReference implements Serializable {
    private final Type type;
    private final String name;
    private final String value;

    public LeakReference(Type type, String name, String value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }


    @Override
    public String toString() {
        return when (type) {
            Type.ARRAY_ENTRY, Type.INSTANCE_FIELD -> getDisplayName() + " = " + value;
            Type.STATIC_FIELD -> "static " + getDisplayName() + " = " + value;
            Type.LOCAL -> getDisplayName();
            default -> "";
        };
    }
}