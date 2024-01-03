package com.github.shadowsocks.utils;

import java.net.InetAddress;
import java.util.Objects;

public class Subnet implements Comparable<Subnet> {
    private InetAddress address;
    private int prefixSize;

    public Subnet(InetAddress address, int prefixSize) {
        this.address = address;
        this.prefixSize = prefixSize;
        if (prefixSize < 0 || prefixSize > getAddressLength()) {
            throw new IllegalArgumentException();
        }
    }

    public static Subnet fromString(String value) throws IllegalArgumentException {
        String[] parts = value.split("/");
        InetAddress addr = parseNumericAddress(parts[0]);
        switch (parts.length) {
            case 1:
                return new Subnet(addr, addr.getAddress().length << 3);
            case 2:
                return new Subnet(addr, Integer.parseInt(parts[1]));
            default:
                throw new IllegalArgumentException();
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

    private static InetAddress parseNumericAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}