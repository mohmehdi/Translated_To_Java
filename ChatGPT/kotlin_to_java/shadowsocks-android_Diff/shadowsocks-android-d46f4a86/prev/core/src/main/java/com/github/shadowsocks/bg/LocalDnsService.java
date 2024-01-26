package com.github.shadowsocks.bg;

import com.github.shadowsocks.Core;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.UtilsKt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.Inet6Address;

public class LocalDnsService {
    public interface Interface extends BaseService.Interface {
        @Override
        default void startProcesses() {
            BaseService.Interface.super.startProcesses();
            DataStore data = DataStore.INSTANCE;
            Core.Profile profile = data.getProxy().getProfile();

            JSONObject makeDns(String name, String address, int timeout, boolean edns) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("Name", name);
                if (UtilsKt.parseNumericAddress(address) instanceof Inet6Address) {
                    jsonObject.put("Address", "[" + address + "]");
                } else {
                    jsonObject.put("Address", address);
                }
                jsonObject.put("Timeout", timeout);
                jsonObject.put("EDNSClientSubnet", new JSONObject().put("Policy", "disable"));
                if (edns) {
                    jsonObject.put("Protocol", "tcp");
                    jsonObject.put("Socks5Address", "127.0.0.1:" + DataStore.INSTANCE.getPortProxy());
                } else {
                    jsonObject.put("Protocol", "udp");
                }
                return jsonObject;
            }

            String buildOvertureConfig(String file) {
                File configFile = new File(Core.INSTANCE.getDeviceStorage().getNoBackupFilesDir(), file);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("BindAddress", DataStore.INSTANCE.getListenAddress() + ":" + DataStore.INSTANCE.getPortLocalDns());
                jsonObject.put("RedirectIPv6Record", true);
                jsonObject.put("DomainBase64Decode", false);
                jsonObject.put("HostsFile", "hosts");
                jsonObject.put("MinimumTTL", 120);
                jsonObject.put("CacheSize", 4096);
                JSONArray remoteDns = new JSONArray();
                for (int i = 0; i < profile.getRemoteDns().split(",").length; i++) {
                    String dns = profile.getRemoteDns().split(",")[i].trim();
                    remoteDns.put(makeDns("UserDef-" + i, dns + ":53", 12, true));
                }
                JSONArray localDns = new JSONArray();
                localDns.put(makeDns("Primary-1", "208.67.222.222:443", 9, false));
                localDns.put(makeDns("Primary-2", "119.29.29.29:53", 9, false));
                localDns.put(makeDns("Primary-3", "114.114.114.114:53", 9, false));
                switch (profile.getRoute()) {
                    case Acl.BYPASS_CHN:
                    case Acl.BYPASS_LAN_CHN:
                    case Acl.GFWLIST:
                    case Acl.CUSTOM_RULES:
                        jsonObject.put("PrimaryDNS", localDns);
                        jsonObject.put("AlternativeDNS", remoteDns);
                        jsonObject.put("IPNetworkFile", new JSONObject().put("Alternative", "china_ip_list.txt"));
                        jsonObject.put("AclFile", "domain_exceptions.acl");
                        break;
                    case Acl.CHINALIST:
                        jsonObject.put("PrimaryDNS", localDns);
                        jsonObject.put("AlternativeDNS", remoteDns);
                        break;
                    default:
                        jsonObject.put("PrimaryDNS", remoteDns);
                        jsonObject.put("OnlyPrimaryDNS", true);
                        break;
                }
                UtilsKt.writeText(configFile, jsonObject.toString());
                return file;
            }

            if (!profile.isUdpdns()) {
                data.getProcesses().start(buildAdditionalArguments(new ArrayList<String>() {{
                    add(new File(app.getApplicationInfo().nativeLibraryDir, Executable.OVERTURE).getAbsolutePath());
                    add("-c");
                    add(buildOvertureConfig("overture.conf"));
                }}));
            }
        }
    }
}