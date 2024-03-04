package com.github.shadowsocks.database;

import android.net.Uri;
import org.junit.Assert;
import org.junit.Test;

public class ProfileTest {
    @Test
    public void parsing() {
        List<Profile> results = Profile.findAll("garble ss");
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Uri.parse("ss"), results.get(0).toUri());
    }
}