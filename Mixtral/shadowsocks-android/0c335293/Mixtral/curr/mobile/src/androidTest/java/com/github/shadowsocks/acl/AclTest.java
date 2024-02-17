

package com.github.shadowsocks.acl;

import org.junit.Assert;
import org.junit.Test;

import java.io.StringReader;

public class AclTest {
    private static final String INPUT1 = "[proxy_all]\n[bypass_list]\n1.0.1.0/24\n(^|\\.)4tern\\.com$\n";

    @Test
    public void parse() {
        Assert.assertEquals(INPUT1, new Acl().fromReader(new StringReader(INPUT1)).toString());
    }
}