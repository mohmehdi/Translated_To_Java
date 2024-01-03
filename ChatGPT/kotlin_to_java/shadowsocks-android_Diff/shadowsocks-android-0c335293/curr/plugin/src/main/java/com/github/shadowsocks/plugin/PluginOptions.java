package com.github.shadowsocks.plugin;

import java.util.HashMap;
import java.util.Objects;
import java.util.StringTokenizer;

public class PluginOptions extends HashMap<String, String> {
    private String id = "";

    public PluginOptions() {
        super();
    }

    public PluginOptions(int initialCapacity) {
        super(initialCapacity);
    }

    public PluginOptions(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    private PluginOptions(String options, boolean parseId) {
        this();
        boolean parseId = parseId;
        if (options == null || options.isEmpty()) {
            return;
        }
        StringTokenizer tokenizer = new StringTokenizer(options + ';', "\\=;", true);
        StringBuilder current = new StringBuilder();
        String key = null;
        while (tokenizer.hasMoreTokens()) {
            String nextToken = tokenizer.nextToken();
            switch (nextToken) {
                case "\\":
                    current.append(tokenizer.nextToken());
                    break;
                case "=":
                    if (key == null) {
                        key = current.toString();
                        current.setLength(0);
                    } else {
                        current.append(nextToken);
                    }
                    break;
                case ";":
                    if (key != null) {
                        put(key, current.toString());
                        key = null;
                    } else if (current.length() > 0) {
                        if (parseId) {
                            id = current.toString();
                        } else {
                            put(current.toString(), null);
                        }
                    }
                    current.setLength(0);
                    parseId = false;
                    break;
                default:
                    current.append(nextToken);
                    break;
            }
        }
    }

    public PluginOptions(String options) {
        this(options, true);
    }

    public PluginOptions(String id, String options) {
        this(options, false);
        this.id = id;
    }

    private void append(StringBuilder result, String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                case '=':
                case ';':
                    result.append('\\');
                    result.append(c);
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
    }

    public String toString(boolean trimId) {
        StringBuilder result = new StringBuilder();
        if (!trimId) {
            if (id.isEmpty()) {
                return "";
            } else {
                append(result, id);
            }
        }
        for (Entry<String, String> entry : entrySet()) {
            if (result.length() > 0) {
                result.append(';');
            }
            append(result, entry.getKey());
            String value = entry.getValue();
            if (value != null) {
                result.append('=');
                append(result, value);
            }
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return toString(true);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PluginOptions that = (PluginOptions) other;
        return super.equals(other) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id);
    }
}