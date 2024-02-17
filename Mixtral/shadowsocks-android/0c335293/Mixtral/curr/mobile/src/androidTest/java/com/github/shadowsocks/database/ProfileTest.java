

package com.github.shadowsocks.database;

import android.net.Uri;
import org.junit.Assert;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ProfileTest {
    @Test
    public void parsing() {
        List<Profile> results = Profile.findAll("garble ss://example.com\n").collect(Collectors.toList());
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Uri.parse("ss://example.com"), results.get(0).toUri());
    }
}