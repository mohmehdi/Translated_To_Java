package com.github.shadowsocks.plugin;

import android.util.Log;
import com.github.shadowsocks.utils.Commandline;
import java.util.*;

public class PluginConfiguration {
    private Map<String, PluginOptions> pluginsOptions;
    private String selected;

    public PluginConfiguration(Map<String, PluginOptions> pluginsOptions, String selected) {
        this.pluginsOptions = pluginsOptions;
        this.selected = selected;
    }

    private PluginConfiguration(List<PluginOptions> plugins) {
        this.pluginsOptions = plugins.stream()
                .filter(plugin -> !plugin.getId().isEmpty())
                .collect(Collectors.toMap(PluginOptions::getId, Function.identity()));
        if (plugins.isEmpty()) {
            this.selected = "";
        } else {
            this.selected = plugins.get(0).getId();
        }
    }

    public PluginConfiguration(String plugin) {
        List<PluginOptions> plugins = Arrays.stream(plugin.split("\n"))
                .map(line -> {
                    if (line.startsWith("kcptun ")) {
                        PluginOptions opt = new PluginOptions();
                        opt.setId("kcptun");
                        try {
                            Iterator<String> iterator = Arrays.asList(Commandline.translateCommandline(line)).iterator();
                            while (iterator.hasNext()) {
                                String option = iterator.next();
                                if (option.equals("--nocomp")) {
                                    opt.put("nocomp", null);
                                } else if (option.startsWith("--")) {
                                    opt.put(option.substring(2), iterator.next());
                                } else {
                                    throw new IllegalArgumentException("Unknown kcptun parameter: " + option);
                                }
                            }
                        } catch (Exception exc) {
                            Log.w("PluginConfiguration", exc.getMessage());
                        }
                        return opt;
                    } else {
                        return new PluginOptions(line);
                    }
                })
                .collect(Collectors.toList());
        this.pluginsOptions = plugins.stream()
                .filter(plugin -> !plugin.getId().isEmpty())
                .collect(Collectors.toMap(PluginOptions::getId, Function.identity()));
        if (plugins.isEmpty()) {
            this.selected = "";
        } else {
            this.selected = plugins.get(0).getId();
        }
    }

    public PluginOptions getOptions(String id) {
        if (id.isEmpty()) {
            return new PluginOptions();
        } else {
            return pluginsOptions.getOrDefault(id, new PluginOptions(id, PluginManager.fetchPlugins().get(id).getDefaultConfig()));
        }
    }

    public PluginOptions getSelectedOptions() {
        return getOptions(selected);
    }

    @Override
    public String toString() {
        LinkedList<PluginOptions> result = new LinkedList<>();
        for (Map.Entry<String, PluginOptions> entry : pluginsOptions.entrySet()) {
            String id = entry.getKey();
            PluginOptions opt = entry.getValue();
            if (id.equals(this.selected)) {
                result.addFirst(opt);
            } else {
                result.addLast(opt);
            }
        }
        if (!pluginsOptions.containsKey(selected)) {
            result.addFirst(getSelectedOptions());
        }
        return result.stream()
                .map(opt -> opt.toString(false))
                .collect(Collectors.joining("\n"));
    }
}