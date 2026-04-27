package com.geo.analytics.infrastructure.crawler.safety;

import com.geo.analytics.domain.exception.SsrFBlockedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class SafeHttpClient {
    private final HttpClient client;
    private final PerDomainRequestLimiter limiter;
    public SafeHttpClient(PerDomainRequestLimiter limiter) {
        this.limiter = limiter;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        int follows = 0;
        HttpRequest current = request;
        while (true) {
            URI uri = current.uri();
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null) {
                throw new SsrFBlockedException("nohost", null);
            }
            if (scheme == null) {
                throw new SsrFBlockedException("noscheme", host);
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new SsrFBlockedException("badscheme", host);
            }
            limiter.acquire(host);
            try {
                final InetAddress[] all;
                try {
                    all = InetAddress.getAllByName(host);
                } catch (UnknownHostException e) {
                    throw new SsrFBlockedException("dns", host);
                }
                for (InetAddress a : all) {
                    if (!SsrFInetAddressValidator.isSafe(a)) {
                        throw new SsrFBlockedException("ssrf", host);
                    }
                }
                HttpResponse.BodyHandler<T> wrap = (info) -> {
                    if (isFollowRedirectCode(info.statusCode()) && info.headers().firstValue("Location").isPresent()) {
                        return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.replacing(null);
                    }
                    return bodyHandler.apply(info);
                };
                HttpResponse<T> r;
                try {
                    r = client.send(current, wrap);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SsrFBlockedException("interrupted", host);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                int code = r.statusCode();
                if (isFollowRedirectCode(code) && r.headers().firstValue("Location").isPresent()) {
                    if (follows >= 5) {
                        throw new SsrFBlockedException("maxredirects", host);
                    }
                    follows++;
                    String loc;
                    loc = r.headers().firstValue("Location").get();
                    final URI next;
                    try {
                        next = current.uri().resolve(URI.create(loc));
                    } catch (IllegalArgumentException e) {
                        throw new SsrFBlockedException("locationparse", loc);
                    }
                    String nhost = next.getHost();
                    if (nhost == null) {
                        throw new SsrFBlockedException("nohost", null);
                    }
                    String nscheme = next.getScheme();
                    if (nscheme == null) {
                        throw new SsrFBlockedException("noscheme", nhost);
                    }
                    if (!"http".equalsIgnoreCase(nscheme) && !"https".equalsIgnoreCase(nscheme)) {
                        throw new SsrFBlockedException("badlocationscheme", nhost);
                    }
                    current = buildRedirectRequest(current, code, next);
                    continue;
                }
                return r;
            } finally {
                limiter.release(host);
            }
        }
    }
    private static boolean isFollowRedirectCode(int code) {
        return code == 301
                || code == 302
                || code == 303
                || code == 307
                || code == 308;
    }
    private static HttpRequest buildRedirectRequest(HttpRequest current, int status, URI nextUri) {
        HttpRequest.Builder b = HttpRequest.newBuilder(nextUri);
        current.timeout().ifPresent(b::timeout);
        current.version().ifPresent(b::version);
        if (status == 307 || status == 308) {
            b.method(
                    current.method(),
                    current.bodyPublisher().orElseGet(HttpRequest.BodyPublishers::noBody));
        } else {
            b.GET();
        }
        return b.build();
    }
}
