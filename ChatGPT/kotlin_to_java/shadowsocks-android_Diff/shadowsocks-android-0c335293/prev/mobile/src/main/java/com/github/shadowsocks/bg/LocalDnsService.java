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
            Profile profile = DataStore.INSTANCE.getProfile();

            JSONObject makeDns(String name, String address, boolean edns) {
                JSONObject dns = new JSONObject()
                        .put("Name", name)
                        .put("Address", (address.parseNumericAddress() instanceof Inet6Address) ? "[" + address + "]" : address + ":53")
                        .put("Timeout", 6)
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
                        .put("DomainBase64Decode", true)
                        .put("HostsFile", "hosts")
                        .put("MinimumTTL", 3600)
                        .put("CacheSize", 4096);
                JSONArray remoteDns = new JSONArray(Arrays.asList(profile.getRemoteDns().split(","))
                        .stream()
                        .map(dns -> makeDns("UserDef-" + i, dns.trim()))
                        .collect(Collectors.toList()));
                JSONArray localDns = new JSONArray(Arrays.asList(
                        makeDns("Primary-1", "119.29.29.29", false),
                        makeDns("Primary-2", "114.114.114.114", false)
                ));

                Context context = (Context) this;
                try {
                    String localLinkDns = Dns.getDnsResolver(context);
                    localDns.put(makeDns("Primary-3", localLinkDns, false));
                } catch (Exception e) {
                }

                switch (profile.getRoute()) {
                    case Acl.BYPASS_CHN:
                    case Acl.BYPASS_LAN_CHN:
                    case Acl.GFWLIST:
                    case Acl.CUSTOM_RULES:
                        config.put("PrimaryDNS", localDns)
                                .put("AlternativeDNS", remoteDns)
                                .put("IPNetworkFile", "china_ip_list.txt")
                                .put("DomainFile", "gfwlist.txt");
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
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(filesDir, file)))) {
                    writer.write(config.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return file;
            }

            if (!profile.isUdpdns()) {
                overtureProcess = new GuardedProcess(buildAdditionalArguments(new ArrayList<>(Arrays.asList(
                        new File(app.getApplicationInfo().nativeLibraryDir, Executable.OVERTURE).getAbsolutePath(),
                        "-c", buildOvertureConfig("overture.conf")
                )))).start();
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

    private static final WeakHashMap<Interface, GuardedProcess> overtureProcesses = new WeakHashMap<>();
}