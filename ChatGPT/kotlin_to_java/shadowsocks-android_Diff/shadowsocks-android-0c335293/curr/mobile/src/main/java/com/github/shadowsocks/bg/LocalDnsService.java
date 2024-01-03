package com.github.shadowsocks.bg;

import android.content.Context;
import com.github.shadowsocks.App;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.parseNumericAddress;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.Inet6Address;
import java.util.*;

public class LocalDnsService {
    public interface Interface extends BaseService.Interface {
        GuardedProcess overtureProcess = null;

        default GuardedProcess getOvertureProcess() {
            return overtureProcesses.get(this);
        }

        default void setOvertureProcess(GuardedProcess value) {
            if (value == null) {
                overtureProcesses.remove(this);
            } else {
                overtureProcesses.put(this, value);
            }
        }

        @Override
        default void startNativeProcesses() {
            BaseService.Interface.super.startNativeProcesses();
            DataStore data = DataStore.INSTANCE;
            Profile profile = data.getProfile();

            JSONObject makeDns(String name, String address, boolean edns) {
                JSONObject dns = new JSONObject()
                        .put("Name", name)
                        .put("Address", (address.parseNumericAddress() instanceof Inet6Address) ? "[" + address + "]" : address + ":53")
                        .put("Timeout", 3)
                        .put("EDNSClientSubnet", new JSONObject().put("Policy", "disable"));
                if (edns) {
                    dns.put("Protocol", "tcp")
                            .put("Socks5Address", "127.0.0.1:" + DataStore.INSTANCE.getPortProxy());
                } else {
                    dns.put("Protocol", "udp");
                }

                return dns;
            }

            String buildOvertureConfig(String file) {
                JSONObject config = new JSONObject()
                        .put("BindAddress", "127.0.0.1:" + DataStore.INSTANCE.getPortLocalDns())
                        .put("RedirectIPv6Record", true)
                        .put("DomainBase64Decode", false)
                        .put("HostsFile", "hosts")
                        .put("MinimumTTL", 120)
                        .put("CacheSize", 4096);
                JSONArray remoteDns = new JSONArray();
                for (int i = 0; i < profile.getRemoteDns().split(",").length; i++) {
                    String dns = profile.getRemoteDns().split(",")[i].trim();
                    remoteDns.put(makeDns("UserDef-" + i, dns));
                }
                JSONArray localDns = new JSONArray();
                localDns.put(makeDns("Primary-1", "119.29.29.29", false));
                localDns.put(makeDns("Primary-2", "114.114.114.114", false));

                switch (profile.getRoute()) {
                    case Acl.BYPASS_CHN:
                    case Acl.BYPASS_LAN_CHN:
                    case Acl.GFWLIST:
                    case Acl.CUSTOM_RULES:
                        config.put("PrimaryDNS", localDns)
                                .put("AlternativeDNS", remoteDns)
                                .put("IPNetworkFile", "china_ip_list.txt")
                                .put("DomainFile", data.getAclFile().getAbsolutePath());
                        break;
                    case Acl.CHINALIST:
                        config.put("PrimaryDNS", localDns)
                                .put("AlternativeDNS", remoteDns);
                        break;
                    default:
                        config.put("PrimaryDNS", remoteDns)
                                .put("OnlyPrimaryDNS", true);
                        break;
                }
                new File(App.Companion.getDeviceContext().getFilesDir(), file).writeText(config.toString());
                return file;
            }

            if (!profile.isUdpdns()) {
                overtureProcess = new GuardedProcess(buildAdditionalArguments(new ArrayList<String>() {{
                    add(new File(App.Companion.getApplicationInfo().nativeLibraryDir, Executable.OVERTURE).getAbsolutePath());
                    add("-c");
                    add(buildOvertureConfig("overture.conf"));
                }})).start();
            }
        }

        @Override
        default void killProcesses() {
            BaseService.Interface.super.killProcesses();
            if (overtureProcess != null) {
                overtureProcess.destroy();
                overtureProcess = null;
            }
        }
    }

    private static WeakHashMap<Interface, GuardedProcess> overtureProcesses = new WeakHashMap<>();
}