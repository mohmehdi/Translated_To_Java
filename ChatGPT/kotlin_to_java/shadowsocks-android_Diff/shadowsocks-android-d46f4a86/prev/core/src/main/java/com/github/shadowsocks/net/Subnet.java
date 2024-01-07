package com.github.shadowsocks.net;

import com.github.shadowsocks.utils.parseNumericAddress;
import java.net.InetAddress;
import java.util.Objects;

public class Subnet implements Comparable<Subnet> {
    private InetAddress address;
    private int prefixSize;

    public Subnet(InetAddress address, int prefixSize) {
        this.address = address;
        this.prefixSize = prefixSize;
        if (prefixSize < 0 || prefixSize > getAddressLength()) {
            throw new IllegalArgumentException("prefixSize: " + prefixSize);
        }
    }

    public static Subnet fromString(String value) {
        String[] parts = value.split("/", 2);
        InetAddress addr = parseNumericAddress(parts[0]);
        if (addr == null) {
            return null;
        }
        if (parts.length == 2) {
            try {
                int prefixSize = Integer.parseInt(parts[1]);
                if (prefixSize < 0 || prefixSize > addr.getAddress().length << 3) {
                    return null;
                } else {
                    return new Subnet(addr, prefixSize);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return new Subnet(addr, addr.getAddress().length << 3);
        }
    }

    private int getAddressLength() {
        return address.getAddress().length << 3;
    }

    @Override
    public String toString() {
        if (prefixSize == getAddressLength()) {
            return address.getHostAddress();
        } else {
            return address.getHostAddress() + "/" + prefixSize;
        }
    }

    private int unsigned(byte b) {
        return b & 0xFF;
    }

    @Override
    public int compareTo(Subnet other) {
        byte[] addrThis = address.getAddress();
        byte[] addrThat = other.address.getAddress();
        int result = Integer.compare(addrThis.length, addrThat.length);
        if (result != 0) {
            return result;
        }
        for (int i = 0; i < addrThis.length; i++) {
            result = Integer.compare(unsigned(addrThis[i]), unsigned(addrThat[i]));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(prefixSize, other.prefixSize);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Subnet that = (Subnet) other;
        return address.equals(that.address) && prefixSize == that.prefixSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, prefixSize);
    }
}