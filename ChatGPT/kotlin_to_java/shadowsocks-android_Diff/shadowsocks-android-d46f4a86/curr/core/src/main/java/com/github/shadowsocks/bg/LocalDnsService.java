package com.github.shadowsocks.bg;

import com.github.shadowsocks.Core.app;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.net.LocalDnsServer;
import com.github.shadowsocks.net.Subnet;
import com.github.shadowsocks.utils.parseNumericAddress;
import java.util.*;

public class LocalDnsService {
    private static final Regex googleApisTester =
            "(^|\\.)googleapis(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?){1,2}\$".toRegex();
    private static final List<Subnet> chinaIpList = new ArrayList<>();

    private static final WeakHashMap<Interface, LocalDnsServer> servers = new WeakHashMap<>();

    public interface Interface extends BaseService.Interface {
        @Override
        default void startProcesses() {
            BaseService.Interface.super.startProcesses();
            Data data = getData();
            Profile profile = data.getProxy().getProfile();
            if (!profile.isUdpdns()) {
                servers.put(this, new LocalDnsServer(this::resolver,
                        parseNumericAddress(profile.getRemoteDns().split(",")[0])));
                LocalDnsServer server = servers.get(this);
                if (server != null) {
                    switch (profile.getRoute()) {
                        case Acl.BYPASS_CHN:
                        case Acl.BYPASS_LAN_CHN:
                        case Acl.GFWLIST:
                        case Acl.CUSTOM_RULES:
                            server.setRemoteDomainMatcher(googleApisTester);
                            server.setLocalIpMatcher(chinaIpList);
                            break;
                        case Acl.CHINALIST:
                            break;
                        default:
                            server.setForwardOnly(true);
                            break;
                    }
                    server.start();
                }
            }
        }

        @Override
        default void killProcesses() {
            servers.remove(this).shutdown();
            BaseService.Interface.super.killProcesses();
        }
    }
}