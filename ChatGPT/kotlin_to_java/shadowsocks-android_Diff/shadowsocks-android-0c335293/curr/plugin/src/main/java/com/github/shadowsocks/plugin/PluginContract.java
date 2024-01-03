package com.github.shadowsocks.plugin;

public class PluginContract {

    public static final String ACTION_NATIVE_PLUGIN = "com.github.shadowsocks.plugin.ACTION_NATIVE_PLUGIN";

    public static final String ACTION_CONFIGURE = "com.github.shadowsocks.plugin.ACTION_CONFIGURE";

    public static final String ACTION_HELP = "com.github.shadowsocks.plugin.ACTION_HELP";

    public static final String EXTRA_ENTRY = "com.github.shadowsocks.plugin.EXTRA_ENTRY";

    public static final String EXTRA_OPTIONS = "com.github.shadowsocks.plugin.EXTRA_OPTIONS";

    public static final String EXTRA_HELP_MESSAGE = "com.github.shadowsocks.plugin.EXTRA_HELP_MESSAGE";

    public static final String METADATA_KEY_ID = "com.github.shadowsocks.plugin.id";

    public static final String METADATA_KEY_DEFAULT_CONFIG = "com.github.shadowsocks.plugin.default_config";

    public static final String METHOD_GET_EXECUTABLE = "shadowsocks:getExecutable";

    public static final int RESULT_FALLBACK = 1;

    public static final String COLUMN_PATH = "path";

    public static final String COLUMN_MODE = "mode";

    public static final String SCHEME = "plugin";

    public static final String AUTHORITY = "com.github.shadowsocks";
}