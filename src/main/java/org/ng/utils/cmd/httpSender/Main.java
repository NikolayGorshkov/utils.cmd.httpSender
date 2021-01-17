package org.ng.utils.cmd.httpSender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.inject.Inject;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

@QuarkusMain
public class Main implements QuarkusApplication {

    private static final class Header {
        String name;
        String value;
        String raw;
    }

    private static final class MirroredOut {
        private final OutputStream outPrimary;
        private final OutputStream outSecondary;
        private final int secondaryLimit;
        private int writtenToSecondary = 0;
        private boolean secondaryLimitMessageWritten = false;

        MirroredOut(OutputStream outPrimaryValye, OutputStream outSecondaryValue, int secondaryLimitValue) {
            outPrimary = outPrimaryValye;
            outSecondary = outSecondaryValue;
            secondaryLimit = secondaryLimitValue;
        }

        void write(byte[] b) throws IOException {
            outPrimary.write(b);
            if (secondaryLimit == -1 || writtenToSecondary < secondaryLimit) {
                outSecondary.write(b);
                writtenToSecondary += b.length;
            } else if (secondaryLimit != -1) {
                writeSecondaryLimitMessage();
            }
        }

        void write(int b) throws IOException {
            outPrimary.write(b);
            if (secondaryLimit == -1 || writtenToSecondary < secondaryLimit) {
                outSecondary.write(b);
                writtenToSecondary++;
            } else if (secondaryLimit != -1) {
                writeSecondaryLimitMessage();
            }
        }

        private void writeSecondaryLimitMessage() throws IOException {
            if (secondaryLimitMessageWritten) {
                return;
            }
            outSecondary.write(("\n[... cropped data over the length of " + secondaryLimit + " ...]")
                    .getBytes(CONVERSION_CHARSET));
            secondaryLimitMessageWritten = true;
        }
    }

    private enum HttpType {
        HTTP_1, HTTP_2
    }

    @Inject
    Vertx vertx;

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final Charset CONVERSION_CHARSET = Charset.defaultCharset();
    private static final byte[] LS = "\r\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] HEADERS_SEP = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final Pattern PATTERN_COLON_NOT_FIRST = Pattern.compile("(?<!^):");
    private static final Pattern PATTERN_COLON = Pattern.compile(":", Pattern.LITERAL);
    private static final Pattern PATTERN_SPACE = Pattern.compile(" ", Pattern.LITERAL);
    private static final Pattern PATTERN_SEMICOLON = Pattern.compile(";", Pattern.LITERAL);

    private InputStream systemIn;
    private PrintStream systemOut;

    public void setSystemStreams(InputStream in, PrintStream out) {
        this.systemIn = in;
        this.systemOut = out;
    }

    static {
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
    }

    @Override
    public int run(String... args) throws Exception {
        if (systemIn == null && systemOut == null) {
            setSystemStreams(System.in, System.out);
        }

        boolean useTls = false;

        HttpType protocol = null;

        // parse arguments
        for (String arg : args) {
            switch (arg) {
                case "-tls":
                    useTls = true;
                    break;
                case "-h2":
                    protocol = HttpType.HTTP_2;
                    break;
                default:
                    printUsage();
                    return 100;
            }
        }

        String host = null;
        int port = -1;
        String path = null;
        String method = null;

        List<Header> headers = new ArrayList<>();
        StringBuilder body = null;

        // intentionally using non-optimal buffer to react on each byte
        try (LineNumberReader in = new LineNumberReader(new InputStreamReader(systemIn, StandardCharsets.UTF_8), 1)) {
            boolean needFirstLineRead = protocol == null || protocol == HttpType.HTTP_1;
            boolean headersRead = false;
            for (String line = null; (line = in.readLine()) != null;) {
                boolean altEnterPressed = false;
                if (line.endsWith("\u001b")) {
                    line = line.substring(0, line.length() - 1);
                    altEnterPressed = true;
                }
                if (needFirstLineRead) {
                    String[] headerParts = PATTERN_SPACE.split(line);
                    if (headerParts.length == 3 && headerParts[2].startsWith("HTTP/")) {
                        method = headerParts[0].trim();
                        path = headerParts[1].trim();
                        protocol = HttpType.HTTP_1;
                    }
                    needFirstLineRead = false;
                }
                boolean bodySeparator = false;
                if (!headersRead && "".equals(line)) {
                    headersRead = true;
                    bodySeparator = true;
                }
                if (!headersRead) {
                    String[] headerPair = PATTERN_COLON_NOT_FIRST.split(line, 2);
                    Header header = new Header();
                    header.raw = line;
                    header.name = headerPair[0].trim();
                    if (headerPair.length > 1) {
                        header.value = headerPair[1].trim();
                    }

                    boolean skipHeaderSending = false;
                    // special cases for headers
                    switch (header.name.toLowerCase()) {
                        case ":authority": // HTTP/2
                            protocol = HttpType.HTTP_2;
                            skipHeaderSending = true;
                            // fall-through
                        case "host": // HTTP/1
                        {
                            // TODO: support case for user:password@
                            int colonIdx = header.value.indexOf(':');
                            if (colonIdx != -1) {
                                port = Integer.parseInt(header.value.substring(colonIdx + 1));
                                host = header.value.substring(0, colonIdx);
                            } else {
                                host = header.value;
                            }
                        }
                            break;
                        case ":scheme": // HTTP/2
                            protocol = HttpType.HTTP_2;
                            if ("https".equals(header.value)) {
                                useTls = true;
                            }
                            skipHeaderSending = true;
                            break;
                        case ":method": // HTTP/2
                            protocol = HttpType.HTTP_2;
                            method = header.value;
                            skipHeaderSending = true;
                            break;
                        case ":path": // HTTP/2
                            protocol = HttpType.HTTP_2;
                            path = header.value;
                            skipHeaderSending = true;
                            break;
                    }
                    if (!skipHeaderSending) {
                        headers.add(header);
                    }
                } else if (!bodySeparator) {
                    if (body == null) {
                        body = new StringBuilder();
                    }
                    body.append(line);
                }

                if (altEnterPressed) {
                    break;
                }
            }
        }

        Objects.requireNonNull(protocol, "Protocol not detected from headers or not set explicitly");
        Objects.requireNonNull(host,
                "Host header (for HTTP/1.1) or :authority pseudo-header field (for HTTP/2) not present");
        Objects.requireNonNull(path,
                "Method not present in first line (HTTP/1.1) or :method pseudo-header field not present (HTTP/2)");
        Objects.requireNonNull(method,
                "Method not present in first line (HTTP/1.1) or :path pseudo-header field not present (HTTP/2)");
        if (port == -1) {
            port = useTls ? 443 : 80;
        }

        printLine("Protocol: " + protocol);

        switch (protocol) {
            case HTTP_1:
                sendHttp1(useTls, host, port, headers,
                        body == null ? null : body.toString().getBytes(CONVERSION_CHARSET));
                break;
            case HTTP_2:
                sendHttp2(method, host, port, path, headers,
                        body == null ? null : body.toString().getBytes(CONVERSION_CHARSET));
                break;
        }

        return 0;
    }

    private void sendHttp1(boolean useTls, String host, int port, List<Header> requestHeaders, byte[] requestBody)
            throws Exception {
        SocketFactory socketFactory;
        if (useTls) {
            socketFactory = buildTrustAllSSLContext().getSocketFactory();
        } else {
            socketFactory = SocketFactory.getDefault();
        }

        Socket socket = socketFactory.createSocket(InetAddress.getByName(host), port);

        Thread readerThread = new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = socket.getInputStream()) {
                    MirroredOut out = new MirroredOut(baos, systemOut, 1000);
                    // I intentionally did not use buffer here to skip waiting for the buffer to be
                    // filled
                    for (int b = -1; (b = in.read()) != -1;) {
                        out.write(b);
                    }
                }

                printLine();
                printLine("RESPONSE RECEIVED");

                byte[] response = baos.toByteArray();
                int headersSepIdx = ArraysUtil.indexOf(response, 0, HEADERS_SEP);
                if (headersSepIdx == -1) {
                    return;
                }

                List<Header> headersParsed = new ArrayList<>();

                try (LineNumberReader lnr = new LineNumberReader(
                        new InputStreamReader(new ByteArrayInputStream(response, 0, headersSepIdx)))) {
                    for (String line = null; (line = lnr.readLine()) != null;) {
                        String[] lineParts = PATTERN_COLON.split(line, 2);
                        if (lineParts.length != 2) {
                            continue;
                        }
                        Header header = new Header();
                        header.name = lineParts[0].trim();
                        header.value = lineParts[1].trim();
                        headersParsed.add(header);
                    }
                }

                byte[] body = new byte[response.length - headersSepIdx - HEADERS_SEP.length];
                System.arraycopy(response, headersSepIdx + HEADERS_SEP.length, body, 0, body.length);

                analyzeHttpsResponse(headersParsed, body, false);
            } catch (Exception e) {
                LOG.error("Failed parse and print response", e);
            }
        });
        readerThread.setUncaughtExceptionHandler((thread, e) -> {
            LOG.error("Uncaught exception in reader thread", e);
        });
        readerThread.start();

        try (OutputStream outStream = socket.getOutputStream()) {
            MirroredOut out = new MirroredOut(outStream, systemOut, -1);
            printLine("SENDING REQUEST");
            requestHeaders.stream().map(h -> {
                if (requestBody != null && "content-length".equals(h.name.toLowerCase())) {
                    return h.name + ": " + requestBody.length;
                }
                return h.raw;
            }).map(s -> s.getBytes(CONVERSION_CHARSET)).forEach(s -> {
                try {
                    out.write(s);
                    out.write(LS);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed writing to socket", e);
                }
            });
            out.write(LS);
            if (requestBody != null) {
                out.write(requestBody);
            }

            printLine();
            printLine("REQUEST SENT");

            readerThread.join();
        }
    }

    private void sendHttp2(String method, String host, int port, String path, List<Header> requestHeaders,
            byte[] requestBody) throws Exception {
        WebClient client = WebClient.create(vertx, new WebClientOptions() //
                .setProtocolVersion(HttpVersion.HTTP_2) //
                .setFollowRedirects(false) //
                .setKeepAlive(false) //
                .setUseAlpn(true) //
                .setVerifyHost(false) //
                .setTrustAll(true) //
                .setLogActivity(true) //
        );
        HttpRequest<Buffer> request = client.raw(method, port, host, path).ssl(true);
        requestHeaders.stream().filter(h -> h.name != null && h.value != null).forEach(h -> {
            request.headers().add(h.name, h.value);
        });
        HttpResponse<Buffer> response;
        if (requestBody == null) {
            response = request.sendAndAwait();
        } else {
            response = request.sendBufferAndAwait(Buffer.buffer(requestBody));
        }

        List<Header> responseHeaders = new ArrayList<>();
        printLine("RESPONSE HEADERS");
        systemOut.println(response.statusCode() + " " + response.statusMessage() + " " + response.version());
        response.headers().forEach(e -> {
            Header header = new Header();
            header.name = e.getKey();
            header.value = e.getValue();
            responseHeaders.add(header);
            systemOut.println(e.getKey() + ": " + e.getValue());
        });

        byte[] body;
        if (response.body() == null) {
            printLine("RESPONSE HAS NO BODY");
            body = new byte[0];
        } else {
            body = response.body().getBytes();
            printLine("RESPONSE BODY");
            int bodyOutLimit = 500;
            if (body.length <= bodyOutLimit) {
                systemOut.write(body);
            } else {
                systemOut.write(body, 0, bodyOutLimit);
                systemOut.println("\n[... cropped data over the length of " + bodyOutLimit + " ...]");
            }
            printLine();
        }

        analyzeHttpsResponse(responseHeaders, body, true);
    }

    // ========================== UTILS ========================

    private void analyzeHttpsResponse(List<Header> headers, byte[] body, boolean ignoreChunkedProcessing)
            throws Exception {
        boolean isChunkedResponse = false;
        boolean isGzipped = false;
        boolean isDeflated = false;
        boolean isJSON = false;
        boolean isXML = false;
        boolean isText = false;
        Charset charset = null;

        for (Header headerParsed : headers) {
            String headerName = headerParsed.name.trim().toLowerCase();
            String headerValue = headerParsed.value.trim().toLowerCase();
            switch (headerName) {
                case "transfer-encoding":
                    if ("chunked".equals(headerValue) && !ignoreChunkedProcessing) {
                        isChunkedResponse = true;
                    }
                    break;
                case "content-encoding":
                    if ("gzip".equals(headerValue)) {
                        isGzipped = true;
                    } else if ("deflate".equals(headerValue)) {
                        isDeflated = true;
                    }
                    break;
                case "content-type": //
                {
                    String[] parts = PATTERN_SEMICOLON.split(headerValue);
                    // I, personally, like nested switches when they fit OK in the logic
                    switch (parts[0]) {
                        case "application/json":
                            isJSON = true;
                            isText = true;
                            break;
                        case "application/xml":
                        case "text/xml":
                        case "application/soap+xml":
                            isXML = true;
                            isText = true;
                            break;
                    }
                    if (parts[0].startsWith("text/")) {
                        isText = true;
                    }
                    if (parts.length == 2 && parts[1].startsWith("charset=")) {
                        String encodingName = parts[1].substring(8);
                        try {
                            charset = Charset.forName(encodingName);
                        } catch (Exception e) {
                            LOG.error("Unsuported charset: " + encodingName, e);
                            return;
                        }
                    }
                    if (isText && charset == null) {
                        // let's assume it's UTF-8
                        charset = StandardCharsets.UTF_8;
                    }
                }
                    break;
            }
        }

        if (isChunkedResponse) {
            printLine("CONVERTING FROM CHUNKED FORMAT");
            body = ArraysUtil.parseChunkedResponse(body);
        }
        if (isGzipped) {
            printLine("UNGZIPPING");
            body = unGzip(body);
        } else if (isDeflated) {
            printLine("UNDEFLATING");
            body = unDeflate(body);
        }
        if (isText) {
            Objects.requireNonNull(charset, "Charset not detected");
            String responseText = new String(body, charset);
            if (isJSON) {
                printLine("JSON");
                String parsed;
                try {
                    parsed = FormatterUtils.formatJSON(responseText);
                } catch (Exception e) {
                    LOG.error("Error during JSON pretty-print", e);
                    printLine("TEXT");
                    parsed = responseText;
                }
                systemOut.println(parsed);
            } else if (isXML) {
                printLine("XML");
                String parsed;
                try {
                    parsed = FormatterUtils.formatXML(responseText);
                } catch (Exception e) {
                    LOG.error("Error during XML pretty-print", e);
                    printLine("TEXT");
                    parsed = responseText;
                }
                systemOut.println(parsed);
            } else {
                printLine("TEXT");
                systemOut.println(responseText);
            }
        } else {
            printLine("RAW");
            systemOut.write(body);
            systemOut.println();
        }

        printLine("END");
    }

    private static SSLContext buildTrustAllSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        } };
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        return sc;
    }

    private static byte[] unGzip(byte[] src) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new GZIPInputStream(new ByteArrayInputStream(src)).transferTo(baos);
        return baos.toByteArray();
    }

    private static byte[] unDeflate(byte[] src) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new InflaterInputStream(new ByteArrayInputStream(src)).transferTo(baos);
        return baos.toByteArray();
    }

    private void printLine() {
        systemOut.println();
    }

    private void printLine(String line) {
        final int infoLineWidth = 60;
        StringBuilder result = new StringBuilder(infoLineWidth);
        if (line.length() + 6 >= infoLineWidth) {
            result.append("=[ ").append(line).append(" ]=");
        } else {
            int totalPrefix = infoLineWidth - (line.length() + 6);
            int prefixLength = totalPrefix / 2;
            int suffixLength = (totalPrefix & 1) == 1 ? (prefixLength + 1) : prefixLength;
            for (int i = 0; i < prefixLength; i++) {
                result.append('=');
            }
            result.append("=[ ").append(line).append(" ]=");
            for (int i = 0; i < suffixLength; i++) {
                result.append('=');
            }
        }
        systemOut.println(result);
    }

    private void printUsage() {
        systemOut.println("Usage:\n\n" //
                + "Start the tool, enter text to send, press Alt+Enter. The tool will parse headers\n" //
                + "from the request and send it to host and URL specified in the headers (host from\n" //
                + "\"host\" header, and URL from the first line of the HTTP request). Port to connect\n" //
                + "to will be taken from the host, if set. Otherwise port 433 will be used for SSL/TLS,\n" //
                + "and 80 for non-SSL/TLS connection." //
                + "\n\n" //
                + "Params:\n" //
                + "\t -tls - force use SSL/TLS\n" //
                + "\t -h2 - force use of HTTP/2" //
        );
    }
}