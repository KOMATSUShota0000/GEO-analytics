package com.geo.analytics.infrastructure.crawler.safety;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class SsrFInetAddressValidator {
    private SsrFInetAddressValidator() {
    }
    public static boolean isSafe(InetAddress addr) {
        if (addr == null) {
            return false;
        }
        if (addr.isLoopbackAddress()) {
            return false;
        }
        if (addr.isLinkLocalAddress()) {
            return false;
        }
        if (addr.isSiteLocalAddress()) {
            return false;
        }
        if (addr.isAnyLocalAddress()) {
            return false;
        }
        if (addr instanceof Inet4Address) {
            byte[] b = addr.getAddress();
            if (b.length == 4) {
                int o0 = b[0] & 255;
                int o1 = b[1] & 255;
                if (o0 == 100 && o1 >= 64 && o1 <= 127) {
                    return false;
                }
            }
        }
        if (addr instanceof Inet6Address i6) {
            byte[] a = i6.getAddress();
            if (a.length == 16) {
                if (a[10] == (byte) 255 && a[11] == (byte) 255) {
                    byte[] v4 = new byte[] {a[12], a[13], a[14], a[15]};
                    try {
                        return isSafe(InetAddress.getByAddress(v4));
                    } catch (UnknownHostException e) {
                        return false;
                    }
                }
                if ((a[0] & 254) == 252) {
                    return false;
                }
            }
        }
        return true;
    }
}
