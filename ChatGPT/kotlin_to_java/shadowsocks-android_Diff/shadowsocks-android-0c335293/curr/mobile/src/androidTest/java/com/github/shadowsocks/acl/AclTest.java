package com.github.shadowsocks.acl;

import org.junit.Assert;
import org.junit.Test;

public class AclTest {
    private static final String INPUT1 = "[proxy_all]\n[bypass_list]\n1.0.1.0/24\n(^|\\.)4tern\\.com$";

    @Test
    public void parse() {
        Assert.assertEquals(INPUT1, new Acl().fromReader(INPUT1.reader()).toString());
    }
}