
package leakcanary;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExcludedRefs implements Serializable {

    public final Map<String, Map<String, Exclusion>> fieldNameByClassName;
    public final Map<String, Map<String, Exclusion>> staticFieldNameByClassName;
    public final Map<String, Exclusion> threadNames;
    public final Map<String, Exclusion> classNames;

    public ExcludedRefs(BuilderWithParams builder) {
        this.fieldNameByClassName = unmodifiableRefStringMap(builder.fieldNameByClassName);
        this.staticFieldNameByClassName = unmodifiableRefStringMap(builder.staticFieldNameByClassName);
        this.threadNames = unmodifiableRefMap(builder.threadNames);
        this.classNames = unmodifiableRefMap(builder.classNames);
    }

    private Map<String, Map<String, Exclusion>> unmodifiableRefStringMap(Map<String, Map<String, ParamsBuilder>> mapmap) {
        Map<String, Map<String, Exclusion>> fieldNameByClassName = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ParamsBuilder>> entry : mapmap.entrySet()) {
            fieldNameByClassName.put(entry.getKey(), unmodifiableRefMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(fieldNameByClassName);
    }

    private Map<String, Exclusion> unmodifiableRefMap(Map<String, ParamsBuilder> fieldBuilderMap) {
        Map<String, Exclusion> fieldMap = new LinkedHashMap<>();
        for (Map.Entry<String, ParamsBuilder> entry : fieldBuilderMap.entrySet()) {
            fieldMap.put(entry.getKey(), new Exclusion(entry.getValue()));
        }
        return Collections.unmodifiableMap(fieldMap);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        for (Map.Entry<String, Map<String, Exclusion>> entry : fieldNameByClassName.entrySet()) {
            for (Map.Entry<String, Exclusion> entry1 : entry.getValue().entrySet()) {
                String always = entry1.getValue().alwaysExclude ? " (always)" : "";
                string.append("| Field: ").append(entry.getKey()).append(".").append(entry1.getKey()).append(always).append("\n");
            }
        }
        for (Map.Entry<String, Map<String, Exclusion>> entry : staticFieldNameByClassName.entrySet()) {
            for (Map.Entry<String, Exclusion> entry1 : entry.getValue().entrySet()) {
                String always = entry1.getValue().alwaysExclude ? " (always)" : "";
                string.append("| Static field: ").append(entry.getKey()).append(".").append(entry1.getKey()).append(always).append("\n");
            }
        }
        for (Map.Entry<String, Exclusion> entry : threadNames.entrySet()) {
            String always = entry.getValue().alwaysExclude ? " (always)" : "";
            string.append("| Thread:").append(entry.getKey()).append(always).append("\n");
        }
        for (Map.Entry<String, Exclusion> entry : classNames.entrySet()) {
            String always = entry.getValue().alwaysExclude ? " (always)" : "";
            string.append("| Class:").append(entry.getKey()).append(always).append("\n");
        }
        return string.toString();
    }

    @SuppressWarnings("unused")
    public static class ParamsBuilder {
        public String matching;
        public String name;
        public String reason;
        public boolean alwaysExclude;
    }

    public interface Builder {
        BuilderWithParams instanceField(String className, String fieldName);

        BuilderWithParams staticField(String className, String fieldName);

        BuilderWithParams thread(String threadName);

        BuilderWithParams clazz(String className);

        ExcludedRefs build();
    }

    public static class BuilderWithParams implements Builder {

        Map<String, Map<String, ParamsBuilder>> fieldNameByClassName = new LinkedHashMap<>();
        Map<String, Map<String, ParamsBuilder>> staticFieldNameByClassName = new LinkedHashMap<>();
        Map<String, ParamsBuilder> threadNames = new LinkedHashMap<>();
        Map<String, ParamsBuilder> classNames = new LinkedHashMap<>();
        private ParamsBuilder lastParams;

        @Override
        public BuilderWithParams instanceField(String className, String fieldName) {
            Map<String, ParamsBuilder> excludedFields = fieldNameByClassName.get(className);
            if (excludedFields == null) {
                excludedFields = new LinkedHashMap<>();
                fieldNameByClassName.put(className, excludedFields);
            }
            lastParams = new ParamsBuilder();
            lastParams.matching = "field " + className + "#" + fieldName;
            excludedFields.put(fieldName, lastParams);
            return this;
        }

        @Override
        public BuilderWithParams staticField(String className, String fieldName) {
            Map<String, ParamsBuilder> excludedFields = staticFieldNameByClassName.get(className);
            if (excludedFields == null) {
                excludedFields = new LinkedHashMap<>();
                staticFieldNameByClassName.put(className, excludedFields);
            }
            lastParams = new ParamsBuilder();
            lastParams.matching = "static field " + className + "#" + fieldName;
            excludedFields.put(fieldName, lastParams);
            return this;
        }

        @Override
        public BuilderWithParams thread(String threadName) {
            lastParams = new ParamsBuilder();
            lastParams.matching = "any threads named " + threadName;
            threadNames.put(threadName, lastParams);
            return this;
        }

        @Override
        public BuilderWithParams clazz(String className) {
            lastParams = new ParamsBuilder();
            lastParams.matching = "any subclass of " + className;
            classNames.put(className, lastParams);
            return this;
        }

        public BuilderWithParams named(String name) {
            lastParams.name = name;
            return this;
        }

        public BuilderWithParams reason(String reason) {
            lastParams.reason = reason;
            return this;
        }

        public BuilderWithParams alwaysExclude() {
            lastParams.alwaysExclude = true;
            return this;
        }

        @Override
        public ExcludedRefs build() {
            return new ExcludedRefs(this);
        }
    }

    public static Builder builder() {
        return new BuilderWithParams();
    }
}