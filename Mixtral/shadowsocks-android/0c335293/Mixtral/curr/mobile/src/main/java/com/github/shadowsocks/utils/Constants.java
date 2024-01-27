package com.github.shadowsocks.utils;

public final class Key {


public static final String DB_PUBLIC = "config.db";
public static final String DB_PROFILE = "profile.db";

public static final String id = "profileId";
public static final String name = "profileName";

public static final String individual = "Proxyed";

public static final String serviceMode = "serviceMode";
public static final String modeProxy = "proxy";
public static final String modeVpn = "vpn";
public static final String modeTransproxy = "transproxy";
public static final String portProxy = "portProxy";
public static final String portLocalDns = "portLocalDns";
public static final String portTransproxy = "portTransproxy";

public static final String route = "route";

public static final String isAutoConnect = "isAutoConnect";
public static final String directBootAware = "directBootAware";

public static final String proxyApps = "isProxyApps";
public static final String bypass = "isBypassApps";
public static final String udpdns = "isUdpDns";
public static final String ipv6 = "isIpv6";

public static final String host = "proxy";
public static final String password = "sitekey";
public static final String method = "encMethod";
public static final String remotePort = "remotePortNum";
public static final String remoteDns = "remoteDns";

public static final String plugin = "plugin";
public static final String pluginConfigure = "plugin.configure";

public static final String dirty = "profileDirty";

public static final String tfo = "tcp_fastopen";
public static final String assetUpdateTime = "assetUpdateTime";
}

public final class Action {


public static final String SERVICE = "com.github.shadowsocks.SERVICE";
public static final String CLOSE = "com.github.shadowsocks.CLOSE";
public static final String RELOAD = "com.github.shadowsocks.RELOAD";

public static final String EXTRA_PROFILE_ID = "com.github.shadowsocks.EXTRA_PROFILE_ID";
}