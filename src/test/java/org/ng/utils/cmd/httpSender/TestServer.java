package org.ng.utils.cmd.httpSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.core.http.HttpServerResponse;
import io.vertx.mutiny.core.net.SelfSignedCertificate;

public final class TestServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TestServer.class);

    private final HttpServer server;

    public volatile Throwable exceptionThrown;

    private TestServer(String method, String scheme, int port, String path, byte[] expectedRequestBody, boolean useTls,
            boolean useHttp2, String responseContentType, String responseBody, boolean chunkedResponse) {
        Vertx vertx = Vertx.vertx();
        HttpServerOptions options = new HttpServerOptions().setCompressionSupported(true).setUseAlpn(true);
        if (useHttp2) {
            options.setAlpnVersions(List.of(HttpVersion.HTTP_2));
        } else {
            options.setAlpnVersions(List.of(HttpVersion.HTTP_1_1));
        }
        if (useTls || useHttp2) {
            SelfSignedCertificate cert = SelfSignedCertificate.create("localhost");
            options.setSsl(true).setKeyCertOptions(cert.keyCertOptions());
        }
        server = vertx.exceptionHandler(this::handleExceptionCaught).createHttpServer(options).requestHandler(req -> {
            req.bodyHandler(bodyBuff -> {
                byte[] requestBodyReceived = bodyBuff.getBytes();
                vertx.executeBlocking(h -> {
                    assertArrayEquals(expectedRequestBody == null ? new byte[0] : expectedRequestBody,
                            requestBodyReceived, "Expected and actual request body should match");
                    assertEquals(method, req.rawMethod(), "Method should match");
                    assertEquals(scheme, req.scheme(), "Scheme should match");
                    assertEquals(path, req.path(), "Path should match");
                    if (useHttp2) {
                        assertTrue(req.isSSL(), "Request should use TLS for HTTP/2");
                        assertEquals(HttpVersion.HTTP_2, req.version(), "Request protocol should match");
                    } else {
                        assertEquals(useTls, req.isSSL(), "Requested connection security must match expected one");
                    }
                    HttpServerResponse res = req.response();
                    res.setChunked(chunkedResponse);
                    if (responseContentType != null) {
                        res.putHeader(HttpHeaders.CONTENT_TYPE, responseContentType);
                    }
                    if (responseBody != null) {
                        res.setStatusCode(200);
                        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                        if (!chunkedResponse) {
                            res.putHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(responseBytes.length));
                        }
                        res.writeAndAwait(Buffer.buffer(responseBytes));
                    } else {
                        res.setStatusCode(204);
                    }
                    res.endAndAwait();
                    res.close();
                }).subscribe().with(res -> {
                    System.out.println("ZZZ");
                }, e -> TestServer.this.handleExceptionCaught(e));
            });
        }).exceptionHandler(this::handleExceptionCaught).listenAndAwait(port, "localhost");

    }

    private void handleExceptionCaught(Throwable e) {
        LOG.error("Server execution error", e);
        exceptionThrown = e;
        server.closeAndForget();
    }

    @Override
    public void close() throws Exception {
        server.closeAndAwait();
        if (exceptionThrown != null) {
            throw new RuntimeException("Server run resulted in exception", exceptionThrown);
        }
    }

    // =================== BUILDER ================

    public static final class Builder {

        private final String method;
        private final String scheme;
        private final int port;
        private final String path;

        private byte[] expectedRequestBody;
        private boolean useTls;
        private boolean useHttp2;
        private String responseContentType;
        private String responseBody;
        private boolean chunkedResponse;

        public Builder(String method, String scheme, int port, String path) {
            this.method = method;
            this.scheme = scheme;
            this.port = port;
            this.path = path;
        }

        public Builder expectedRequestBody(byte[] expectedRequestBody) {
            this.expectedRequestBody = expectedRequestBody;
            return this;
        }

        public Builder useTls() {
            this.useTls = true;
            return this;
        }

        public Builder useHttp2() {
            this.useHttp2 = true;
            return this;
        }

        public Builder responseContentType(String responseContentType) {
            this.responseContentType = responseContentType;
            return this;
        }

        public Builder responseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public Builder chunkedResponse() {
            this.chunkedResponse = true;
            return this;
        }

        public TestServer build() {
            return new TestServer(method, scheme, port, path, expectedRequestBody, useTls, useHttp2,
                    responseContentType, responseBody, chunkedResponse);
        }
    }

}