package com.geo.analytics.domain.phase10;

import java.util.Objects;

final class AsciiFlyweightCharSequence implements CharSequence {

  private byte[] buffer;
  private int offset;
  private int length;
  private int maskStart;
  private int maskLength;

  AsciiFlyweightCharSequence() {
    this.maskStart = -1;
    this.maskLength = 0;
  }

  AsciiFlyweightCharSequence(byte[] buffer, int offset, int length) {
    this(buffer, offset, length, -1, 0);
  }

  AsciiFlyweightCharSequence(byte[] buffer, int offset, int length, int maskStart, int maskLength) {
    this.buffer = buffer;
    this.offset = offset;
    this.length = length;
    this.maskStart = maskStart;
    this.maskLength = maskLength;
  }

  void set(byte[] buffer, int offset, int length) {
    this.buffer = Objects.requireNonNull(buffer);
    Objects.checkFromIndexSize(offset, length, buffer.length);
    this.offset = offset;
    this.length = length;
    this.maskStart = -1;
    this.maskLength = 0;
  }

  void setMask(int start, int length) {
    if (length <= 0) {
      this.maskStart = -1;
      this.maskLength = 0;
      return;
    }
    this.maskStart = start;
    this.maskLength = length;
  }

  @Override
  public int length() {
    return length;
  }

  @Override
  public char charAt(int index) {
    if (index < 0 || index >= this.length) {
      throw new StringIndexOutOfBoundsException(index);
    }
    if (maskLength > 0
        && maskStart >= 0
        && index >= maskStart
        && index < maskStart + maskLength) {
      return '*';
    }
    return (char) (buffer[offset + index] & 0xFF);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    int subLen = end - start;
    int ns = -1;
    int nl = 0;
    if (maskLength > 0 && maskStart >= 0) {
      int mEnd = maskStart + maskLength;
      int segLo = Math.max(maskStart, start);
      int segHi = Math.min(mEnd, end);
      if (segHi > segLo) {
        ns = segLo - start;
        nl = segHi - segLo;
      }
    }
    return new AsciiFlyweightCharSequence(buffer, offset + start, subLen, ns, nl);
  }
}
