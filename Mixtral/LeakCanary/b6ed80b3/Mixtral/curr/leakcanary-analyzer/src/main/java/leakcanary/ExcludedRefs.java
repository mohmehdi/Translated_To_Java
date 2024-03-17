

package leakcanary;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

class ExcludedRefs implements Serializable {

    private static final long serialVersionUID = 1L;

    final Map<String, Map<String, Exclusion>> fieldNameByClassName;
    final Map<String, Map<String, Exclusion>> staticFieldNameByClassName;
    final Map<String, Exclusion> threadNames;
    final Map<String, Exclusion> classNames;

    ExcludedRefs(BuilderWithParams builder) {
        this.fieldNameByClassName = unmodifiableRefStringMap(builder.fieldNameByClassName);
        this.staticFieldNameByClassName = unmodifiableRefStringMap(builder.staticFieldNameByClassName);
        this.threadNames = unmodifiableRefMap(builder.threadNames);
        this.classNames = unmodifiableRefMap(builder.classNames);
    }

    private Map<String, Map<String, Exclusion>> unmodifiableRefStringMap(
            Map<String, Map<String, ParamsBuilder>> mapmap) {
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
                String always = entry1.getValue().isAlwaysExclude() ? " (always)" : "";
                string.append("| Field: ").append(entry.getKey()).append(".").append(entry1.getKey()).append(always).append("\n");
            }
        }
        for (Map.Entry<String, Map<String, Exclusion>> entry : staticFieldNameByClassName.entrySet()) {
            for (Map.Entry<String, Exclusion> entry1 : entry.getValue().entrySet()) {
                String always = entry1.getValue().isAlwaysExclude() ? " (always)" : "";
                string.append("| Static field: ").append(entry.getKey()).append(".").append(entry1.getKey()).append(always).append("\n");
            }
        }
        for (Map.Entry<String, Exclusion> entry : threadNames.entrySet()) {
            String always = entry.getValue().isAlwaysExclude() ? " (always)" : "";
            string.append("| Thread:").append(entry.getKey()).append(always).append("\n");
        }
        for (Map.Entry<String, Exclusion> entry : classNames.entrySet()) {
            String always = entry.getValue().isAlwaysExclude() ? " (always)" : "";
            string.append("| Class:").append(entry.getKey()).append(always).append("\n");
        }
        return string.toString();
    }

    public static class ParamsBuilder {
        private final String matching;
        private String name;
        private String reason;
        private boolean alwaysExclude;

        public ParamsBuilder(String matching) {
            this.matching = matching;
        }
    }

    public interface Builder {
        Builder instanceField(String className, String fieldName);

        Builder staticField(String className, String fieldName);

        Builder thread(String threadName);

        Builder clazz(String className);

        ExcludedRefs build();
    }

    public static class BuilderWithParams implements Builder {

        private final LinkedHashMap<String, MutableMap<String, ParamsBuilder>> fieldNameByClassName;
        private final LinkedHashMap<String, MutableMap<String, ParamsBuilder>> staticFieldNameByClassName;
        private final LinkedHashMap<String, ParamsBuilder> threadNames;
        private final LinkedHashMap<String, ParamsBuilder> classNames;

        private ParamsBuilder lastParams;

        public BuilderWithParams() {
            this.fieldNameByClassName = new LinkedHashMap<>();
            this.staticFieldNameByClassName = new LinkedHashMap<>();
            this.threadNames = new LinkedHashMap<>();
            this.classNames = new LinkedHashMap<>();
        }

        @Override
        public Builder instanceField(String className, String fieldName) {
            MutableMap<String, ParamsBuilder> excludedFields = fieldNameByClassName.computeIfAbsent(className, k -> new LinkedHashMap<>());
            lastParams = new ParamsBuilder("field " + className + "#" + fieldName);
            excludedFields.put(fieldName, lastParams);
            return this;
        }

        @Override
        public Builder staticField(String className, String fieldName) {
            MutableMap<String, ParamsBuilder> excludedFields = staticFieldNameByClassName.computeIfAbsent(className, k -> new LinkedHashMap<>());
            lastParams = new ParamsBuilder("static field " + className + "#" + fieldName);
            excludedFields.put(fieldName, lastParams);
            return this;
        }

        @Override
        public Builder thread(String threadName) {
            lastParams = new ParamsBuilder("any threads named " + threadName);
            threadNames.put(threadName, lastParams);
            return this;
        }

        @Override
        public Builder clazz(String className) {
            lastParams = new ParamsBuilder("any subclass of " + className);
            classNames.put(className, lastParams);
            return this;
        }

        public BuilderWithParams named(String name) {
            Objects.requireNonNull(lastParams, "lastParams cannot be null");
            lastParams.name = name;
            return this;
        }

        public BuilderWithParams reason(String reason) {
            Objects.requireNonNull(lastParams, "lastParams cannot be null");
            lastParams.reason = reason;
            return this;
        }

        public BuilderWithParams alwaysExclude() {
            Objects.requireNonNull(lastParams, "lastParams cannot be null");
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