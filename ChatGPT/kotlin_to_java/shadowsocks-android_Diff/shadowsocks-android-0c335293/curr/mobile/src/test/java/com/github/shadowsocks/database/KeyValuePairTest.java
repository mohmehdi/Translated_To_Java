package com.github.shadowsocks.database;

import org.junit.Assert;
import org.junit.Test;

public class KeyValuePairTest {
    @Test
    public void putAndGet() {
        KeyValuePair kvp = new KeyValuePair();
        Assert.assertEquals(true, kvp.put(true).getBoolean());
        Assert.assertEquals(3f, kvp.put(3f).getFloat(), 0);
        Assert.assertEquals(3, kvp.put(3).getInt());
        Assert.assertEquals(3L, kvp.put(3L).getLong());
        Assert.assertEquals("3", kvp.put("3").getString());
        Set<String> set = IntStream.range(0, 3).mapToObj(Integer::toString).collect(Collectors.toSet());
        Assert.assertEquals(set, kvp.put(set).getStringSet());
        Assert.assertEquals(null, kvp.getBoolean());
    }
}