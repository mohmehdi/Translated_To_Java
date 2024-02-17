

package com.github.shadowsocks.plugin;

import org.junit.Assert;
import org.junit.Test;

public class PluginOptionsTest {
    @Test
    public void equal() {
        PluginOptions o1 = new PluginOptions("obfs-local;obfs=http;obfs-host=localhost");;
        PluginOptions o2 = new PluginOptions("obfs-local", "obfs-host=localhost;obfs=http");
        Assert.assertEquals(o1.hashCode(), o2.hashCode());
        Assert.assertEquals(true, o1.equals(o2));
        PluginOptions o3 = new PluginOptions(o1.toString(false));
        Assert.assertEquals(true, o2.equals(o3));
        PluginOptions o4 = new PluginOptions(o2.getId(), o2.toString());
        Assert.assertEquals(true, o3.equals(o4));
    }
}