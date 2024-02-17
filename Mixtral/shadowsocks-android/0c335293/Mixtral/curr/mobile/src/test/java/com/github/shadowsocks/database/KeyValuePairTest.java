

package com.github.shadowsocks.database;

import org.junit.Assert;
import org.junit.Test;

public class KeyValuePairTest {
    @Test
    public void putAndGet() {
        KeyValuePair kvp = new KeyValuePair();
        Assert.assertEquals(true, (boolean) kvp.put(true).booleanValue());
        Assert.assertEquals(3f, (float) kvp.put(3f).floatValue(), 0.0f);
        Assert.assertEquals(3, (int) kvp.put(3).intValue());
        Assert.assertEquals(3L, (long) kvp.put(3L).longValue());
        Assert.assertEquals("3", kvp.put("3").string);
        Set<String> set = new HashSet<>((Collection<? extends String>) IntStream.range(0, 3).mapToObj(Integer::toString)::iterator);
        Assert.assertEquals(set, kvp.put(set).stringSet);
        Assert.assertNull(kvp.boolean);
    }
}