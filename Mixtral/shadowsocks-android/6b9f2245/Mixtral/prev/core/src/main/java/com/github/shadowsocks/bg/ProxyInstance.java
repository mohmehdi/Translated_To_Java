package com.github.shadowsocks.bg;

import android.content.Context;
import android.net.InetAddress;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.AclSyncer;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Executable;
import com.github.shadowsocks.utils.FileUtils;
import com.github.shadowsocks.utils.disconnectFromMain;
import com.github.shadowsocks.utils.parseNumericAddress;
import com.github.shadowsocks.utils.signaturesCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ProxyInstance implements AutoCloseable {

  private Profile profile;
  private String route;
  private File configFile;
  private TrafficMonitor trafficMonitor;
  private PluginConfiguration plugin;
  private String pluginPath;

  public ProxyInstance(Profile profile, String route) {
    this.profile = profile;
    this.route = route == null ? profile.route : route;
  }

  public ProxyInstance(Profile profile) {
    this(profile, null);
  }

  @Override
  public void close() throws IOException {
    trafficMonitor.close();

    try {
      Profile profile = ProfileManager.getProfile(this.profile.id);
      profile.tx += trafficMonitor.getCurrent().txTotal;
      profile.rx += trafficMonitor.getCurrent().rxTotal;
      ProfileManager.updateProfile(profile);
    } catch (IOException e) {
      if (!DataStore.directBootAware) throw e;

      List deviceProfileList = DirectBoot.getDeviceProfile();
      Profile deviceProfile = deviceProfileList
        .stream()
        .filter(profile1 -> profile1.id == this.profile.id)
        .findFirst()
        .orElse(null);

      deviceProfile.tx += trafficMonitor.getCurrent().txTotal;
      deviceProfile.rx += trafficMonitor.getCurrent().rxTotal;
      deviceProfile.dirty = true;
      DirectBoot.update(deviceProfile);
      DirectBoot.listenForUnlock();
    }

    trafficMonitor = null;
    configFile.delete();
    configFile = null;
  }

  public void start(
    BaseService.Interface service,
    File stat,
    File configFile,
    String extraFlag
  )
    throws JSONException, InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, SocketException, IOException {
    this.trafficMonitor = new TrafficMonitor(stat);

    this.configFile = configFile;
    JSONObject config = this.profile.toJson();

    if (pluginPath != null) {
      config.put("plugin", pluginPath);
      config.put("plugin_opts", plugin.toString());
    }

    configFile.createNewFile();
    FileOutputStream fos = new FileOutputStream(configFile);
    OutputStreamWriter osw = new OutputStreamWriter(fos);
    osw.write(config.toString());
    osw.flush();
    osw.close();
    fos.close();

    List cmd = service.buildAdditionalArguments(
      Arrays.asList(
        new File(
          service.getApplicationInfo().nativeLibraryDir,
          Executable.SS_LOCAL
        )
          .getAbsolutePath(),
        "-b",
        DataStore.listenAddress,
        "-l",
        Integer.toString(DataStore.portProxy),
        "-t",
        "600",
        "-S",
        stat.getAbsolutePath(),
        "-c",
        configFile.getAbsolutePath()
      )
    );

    if (extraFlag != null) {
      cmd.add(extraFlag);
    }

    if (!route.equals(Acl.ALL)) {
      cmd.add("--acl");
      cmd.add(Acl.getFile(route).getAbsolutePath());
    }

    if (profile.route.equals(Acl.ALL) || profile.route.equals(Acl.BYPASS_LAN)) {
      cmd.add("-D");
    }

    if (DataStore.tcpFastOpen) {
      cmd.add("--fast-open");
    }

    Process process = service.data.processes.start(cmd);
  }

  public void scheduleUpdate() {
    if (!route.equals(Acl.ALL) && !route.equals(Acl.CUSTOM_RULES)) {
      AclSyncer.schedule(route);
    }
  }

  private void init(Function<String, InetAddress[]> resolver) {
    if (profile.host.parseNumericAddress() == null) {
      Future future = Executors
        .newSingleThreadExecutor()
        .submit(() -> {
          try {
            return resolver.apply(profile.host).get(0);
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
          }
        });

      InetAddress address = null;
      try {
        address = future.get(10, TimeUnit.SECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        future.cancel(true);
        throw new RuntimeException(e);
      }

      if (address == null) {
        throw new UnknownHostException();
      }

      profile.host = address.getHostAddress();
    }

    if (profile.host.equals("198.199.101.152")) {
      MessageDigest mdg = MessageDigest.getInstance("SHA-1");
      mdg.update(Core.packageInfo.signaturesCompat.get(0).toByteArray());

      HttpURLConnection conn = (HttpURLConnection) RemoteConfig.proxyUrl.openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);

      PrintWriter writer = new PrintWriter(conn.getOutputStream());
      writer.print(
        "sig=" + Base64.encodeToString(mdg.digest(), Base64.DEFAULT)
      );
      writer.close();

      List proxies = new ArrayList<>();
      try (
        BufferedReader reader = new BufferedReader(
          new InputStreamReader(conn.getInputStream())
        )
      ) {
        String line;
        while ((line = reader.readLine()) != null) {
          proxies.add(line);
        }
      }

      Collections.shuffle(proxies);
      String[] proxy = proxies.get(0).split(":");

      profile.host = proxy[0].trim();
      profile.remotePort = Integer.parseInt(proxy[1].trim());
      profile.password = proxy[2].trim();
      profile.method = proxy[3].trim();

      conn.disconnectFromMain();
    }

    if (route.equals(Acl.CUSTOM_RULES)) {
      Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
    }
  }
}
