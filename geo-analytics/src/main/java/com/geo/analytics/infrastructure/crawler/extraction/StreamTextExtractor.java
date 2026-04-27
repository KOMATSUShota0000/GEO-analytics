package com.geo.analytics.infrastructure.crawler.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;

public final class StreamTextExtractor {
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    private static final int BB_CAP = 16384;
    private static final int CB_CAP = 8192;
    private StreamTextExtractor() {
    }
    public static String extract(InputStream raw, long maxBytes, Charset charset) {
        try (CappedInputStream in = new CappedInputStream(Objects.requireNonNull(raw, "raw"), maxBytes);
                ReadableByteChannel ch = Channels.newChannel(in)) {
            CharsetDecoder dec = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            ByteBuffer bbin = ByteBuffer.allocate(BB_CAP);
            CharBuffer cbuf = CharBuffer.allocate(CB_CAP);
            StringBuilder out = new StringBuilder();
            HtmlTagStripper strip = new HtmlTagStripper();
            for (;;) {
                int n = ch.read(bbin);
                if (n == -1) {
                    bbin.flip();
                    if (bbin.hasRemaining()) {
                        runDecode(bbin, dec, cbuf, out, strip, false);
                    }
                    runDecode(EMPTY, dec, cbuf, out, strip, true);
                    tailDecode(dec, cbuf, out, strip);
                    return out.toString();
                }
                bbin.flip();
                runDecode(bbin, dec, cbuf, out, strip, false);
                bbin.compact();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    private static void runDecode(
            ByteBuffer bbin, CharsetDecoder dec, CharBuffer cbuf, StringBuilder out, HtmlTagStripper strip, boolean endOfIn) {
        for (;;) {
            cbuf.clear();
            CoderResult r = dec.decode(bbin, cbuf, endOfIn);
            cbuf.flip();
            feedStrip(cbuf, out, strip);
            if (r.isOverflow()) {
                continue;
            }
            if (r.isUnderflow()) {
                return;
            }
        }
    }
    private static void tailDecode(CharsetDecoder dec, CharBuffer cbuf, StringBuilder out, HtmlTagStripper strip) {
        CoderResult r;
        do {
            cbuf.clear();
            r = dec.flush(cbuf);
            cbuf.flip();
            feedStrip(cbuf, out, strip);
        } while (r.isOverflow());
    }
    private static void feedStrip(CharBuffer cbuf, StringBuilder out, HtmlTagStripper strip) {
        while (cbuf.hasRemaining()) {
            int i = cbuf.position();
            int cp = Character.codePointAt(cbuf, i);
            cbuf.position(i + Character.charCount(cp));
            strip.processCodePoint(cp, out);
        }
    }
}
