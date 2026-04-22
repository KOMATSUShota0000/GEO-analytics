package com.geo.analytics.domain.phase10;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public final class NativeDatSearcher {

  private static final int ROOT_INDEX = 1;

  private final DictionaryHandle handle;

  public NativeDatSearcher(DictionaryHandle handle) {
    this.handle = handle;
  }

  public int findExact(CharSequence text) {
    MemorySegment seg = handle.segment();
    VarHandle vh = handle.intLeHandle();
    long baseOff = handle.baseOffsetBytes();
    long chkOff = handle.checkOffsetBytes();
    int n = handle.nodeCount();
    int current = ROOT_INDEX;
    int len = text.length();
    int idx = 0;
    while (idx < len) {
      int cp = Character.codePointAt(text, idx);
      idx += Character.charCount(cp);
      if (cp < 0x80) {
        int next = step(seg, vh, baseOff, chkOff, n, current, cp);
        if (next < 0) {
          return -1;
        }
        current = next;
      } else if (cp < 0x800) {
        int b0 = 0xC0 | (cp >> 6);
        int b1 = 0x80 | (cp & 0x3F);
        int next = step(seg, vh, baseOff, chkOff, n, current, b0);
        if (next < 0) {
          return -1;
        }
        current = next;
        next = step(seg, vh, baseOff, chkOff, n, current, b1);
        if (next < 0) {
          return -1;
        }
        current = next;
      } else if (cp < 0x10000) {
        int b0 = 0xE0 | (cp >> 12);
        int b1 = 0x80 | ((cp >> 6) & 0x3F);
        int b2 = 0x80 | (cp & 0x3F);
        int next = step(seg, vh, baseOff, chkOff, n, current, b0);
        if (next < 0) {
          return -1;
        }
        current = next;
        next = step(seg, vh, baseOff, chkOff, n, current, b1);
        if (next < 0) {
          return -1;
        }
        current = next;
        next = step(seg, vh, baseOff, chkOff, n, current, b2);
        if (next < 0) {
          return -1;
        }
        current = next;
      } else {
        int b0 = 0xF0 | (cp >> 18);
        int b1 = 0x80 | ((cp >> 12) & 0x3F);
        int b2 = 0x80 | ((cp >> 6) & 0x3F);
        int b3 = 0x80 | (cp & 0x3F);
        int next = step(seg, vh, baseOff, chkOff, n, current, b0);
        if (next < 0) {
          return -1;
        }
        current = next;
        next = step(seg, vh, baseOff, chkOff, n, current, b1);
        if (next < 0) {
          return -1;
        }
        current = next;
        next = step(seg, vh, baseOff, chkOff, n, current, b2);
        if (next < 0) {
          return -1;
        }
        current = next;
        next = step(seg, vh, baseOff, chkOff, n, current, b3);
        if (next < 0) {
          return -1;
        }
        current = next;
      }
    }
    if (current < 0 || current >= n) {
      return -1;
    }
    long termPhys = baseOff + (long) current * Integer.BYTES;
    int b = (int) vh.get(seg, termPhys);
    if (b >= 0) {
      return -1;
    }
    return ~b;
  }

  private static int step(
      MemorySegment seg,
      VarHandle vh,
      long baseOff,
      long chkOff,
      int nodeCount,
      int current,
      int code) {
    if (current < 0 || current >= nodeCount) {
      return -1;
    }
    long basePhys = baseOff + (long) current * Integer.BYTES;
    int baseVal = (int) vh.get(seg, basePhys);
    long sum = (long) baseVal + (long) code;
    if (sum < Integer.MIN_VALUE || sum > Integer.MAX_VALUE) {
      return -1;
    }
    int next = (int) sum;
    if (next < 0 || next >= nodeCount) {
      return -1;
    }
    long checkPhys = chkOff + (long) next * Integer.BYTES;
    int chk = (int) vh.get(seg, checkPhys);
    if (chk != current) {
      return -1;
    }
    return next;
  }
}
