package org.ng.utils.cmd.httpSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.core.Vertx;

@QuarkusTest
public class MainTest {

    private Main main;

    @BeforeEach
    private void setMain() {
        main = new Main();
        main.vertx = Vertx.vertx();
    }

    @Test
    public void testChunkedResponseHttp1() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "http", 10001, "/test")
                .responseBody("zzz123\n456789\r\nqwerty").chunkedResponse().build()) {

            // @formatter:off
            String request =
                      "GET /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Connection: close";
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
              "====================[ Protocol: HTTP_1 ]====================\n"
            + "====================[ SENDING REQUEST ]=====================\n"
            + "GET /test HTTP/1.1\r\n"
            + "Host: localhost:10001\r\n"
            + "Connection: close\r\n"
            + "\r\n"
            + "\n"
            + "======================[ REQUEST SENT ]======================\n"
            + "HTTP/1.1 200 OK\r\n"
            + "transfer-encoding: chunked\r\n"
            + "connection: close\r\n"
            + "\r\n"
            + "15\r\n"
            + "zzz123\n"
            + "456789\r\n"
            + "qwerty\r\n"
            + "0\r\n"
            + "\r\n"
            + "\n"
            + "===================[ RESPONSE RECEIVED ]====================\n"
            + "=============[ CONVERTING FROM CHUNKED FORMAT ]=============\n"
            + "==========================[ RAW ]===========================\n"
            + "zzz123\n"
            + "456789\r\n"
            + "qwerty\n"
            + "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testChunkedResponseHttp2() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "https", 10001, "/test")
                .responseBody("zzz123\n456789\r\nqwerty").chunkedResponse().useHttp2().build()) {

            // @formatter:off
            String request =
                      ":authority: localhost:10001\n" //
                    + ":method: GET\n"
                    + ":path: /test\n"
                    + ":scheme: https";
            // @formatter:on

            String appOut = runApp(request, "-h2");

            // @formatter:off
            assertEquals(
                  "====================[ Protocol: HTTP_2 ]====================\n"
				+ "====================[ RESPONSE HEADERS ]====================\n"
				+ "200 OK HTTP_2\n"
				+ "=====================[ RESPONSE BODY ]======================\n"
				+ "zzz123\n"
				+ "456789\r\n"
				+ "qwerty\n"
				+ "==========================[ RAW ]===========================\n"
				+ "zzz123\n"
				+ "456789\r\n"
				+ "qwerty\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testPostHttp1() throws Exception {
        String requestBody = "TEST_TEST_TEST";
        try (TestServer server = new TestServer.Builder("POST", "http", 10001, "/test")
                .expectedRequestBody(requestBody.getBytes(StandardCharsets.UTF_8)).build()) {

            // @formatter:off
            String request = withBody(
                      "POST /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Connection: close",

                    requestBody);
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
                  "====================[ Protocol: HTTP_1 ]====================\n"
				+ "====================[ SENDING REQUEST ]=====================\n"
				+ "POST /test HTTP/1.1\r\n"
				+ "Host: localhost:10001\r\n"
				+ "Connection: close\r\n"
				+ "Content-Length: 14\r\n"
				+ "\r\n"
				+ "TEST_TEST_TEST\n"
				+ "======================[ REQUEST SENT ]======================\n"
				+ "HTTP/1.1 204 No Content\r\n"
				+ "connection: close\r\n"
				+ "\r\n"
				+ "\n"
				+ "===================[ RESPONSE RECEIVED ]====================\n"
				+ "==========================[ RAW ]===========================\n"
				+ "\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testPostHttp2() throws Exception {
        String requestBody = "TEST_TEST_TEST";
        try (TestServer server = new TestServer.Builder("POST", "https", 10001, "/test")
                .expectedRequestBody(requestBody.getBytes(StandardCharsets.UTF_8)).useHttp2().build()) {

            // @formatter:off
            String request = withBody(
                      ":authority: localhost:10001\n" //
                    + ":method: POST\n"
                    + ":path: /test\n"
                    + ":scheme: https",

                    requestBody);
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
                  "====================[ Protocol: HTTP_2 ]====================\n"
				+ "====================[ RESPONSE HEADERS ]====================\n"
				+ "204 No Content HTTP_2\n"
				+ "==================[ RESPONSE HAS NO BODY ]==================\n"
				+ "==========================[ RAW ]===========================\n"
				+ "\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testGzipHttp1() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "http", 10001, "/test").responseBody("TEST_TEST_TEST")
                .build()) {

            // @formatter:off
            String request = 
                      "GET /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Accept-Encoding: gzip\n" //
                    + "Connection: close";
            // @formatter:on

            String appOut = runApp(request);

            // We will check out start and end frather than full match not to bother with
            // gzip byte mess in the middle

            // @formatter:off
            String outShouldStartWith = 
                  "====================[ Protocol: HTTP_1 ]====================\n"
				+ "====================[ SENDING REQUEST ]=====================\n"
				+ "GET /test HTTP/1.1\r\n"
				+ "Host: localhost:10001\r\n"
				+ "Accept-Encoding: gzip\r\n"
				+ "Connection: close\r\n"
				+ "\r\n"
				+ "\n"
				+ "======================[ REQUEST SENT ]======================\n"
				+ "HTTP/1.1 200 OK\r\n"
				+ "connection: close\r\n"
				+ "content-encoding: gzip\r\n"
				+ "transfer-encoding: chunked\r\n"
				+ "\r\n";
            String outShouldEndWith =
				  "0\r\n"
				+ "\r\n"
				+ "\n"
				+ "===================[ RESPONSE RECEIVED ]====================\n"
				+ "=============[ CONVERTING FROM CHUNKED FORMAT ]=============\n"
				+ "=======================[ UNGZIPPING ]=======================\n"
				+ "==========================[ RAW ]===========================\n"
				+ "TEST_TEST_TEST\n"
                + "==========================[ END ]===========================\n";
            // @formatter:on

            assertTrue(appOut.startsWith(outShouldStartWith), "Out should start with test text");
            assertTrue(appOut.endsWith(outShouldEndWith), "Out should end with test text");
        }
    }

    @Test
    public void testGzipHttp2() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "https", 10001, "/test").responseBody("TEST_TEST_TEST")
                .useHttp2().build()) {

            // @formatter:off
            String request = 
                  ":authority: localhost:10001\n" //
                + ":method: GET\n"
                + ":path: /test\n"
                + ":scheme: https\n"
                + "accept-encoding: gzip";
            // @formatter:on

            String appOut = runApp(request);

            // We will check out start and end frather than full match not to bother with
            // gzip byte mess in the middle

            // @formatter:off
            String outShouldStartWith = 
                  "====================[ Protocol: HTTP_2 ]====================\n"
                + "====================[ RESPONSE HEADERS ]====================\n"
                + "200 OK HTTP_2\n"
                + "content-encoding: gzip\n"
                + "=====================[ RESPONSE BODY ]======================\n";
            String outShouldEndWith =
                  "=======================[ UNGZIPPING ]=======================\n"
                + "==========================[ RAW ]===========================\n"
                + "TEST_TEST_TEST\n"
                + "==========================[ END ]===========================\n";
            // @formatter:on

            assertTrue(appOut.startsWith(outShouldStartWith), "Out should start with test text");
            assertTrue(appOut.endsWith(outShouldEndWith), "Out should end with test text");
        }
    }

    @Test
    public void testDeflateHttp1() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "http", 10001, "/test").responseBody("TEST_TEST_TEST")
                .build()) {

            // @formatter:off
            String request = 
                      "GET /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Accept-Encoding: deflate\n" //
                    + "Connection: close";
            // @formatter:on

            String appOut = runApp(request);

            // We will check out start and end frather than full match not to bother with
            // gzip byte mess in the middle

            // @formatter:off
            String outShouldStartWith = 
                  "====================[ Protocol: HTTP_1 ]====================\n"
				+ "====================[ SENDING REQUEST ]=====================\n"
				+ "GET /test HTTP/1.1\r\n"
				+ "Host: localhost:10001\r\n"
				+ "Accept-Encoding: deflate\r\n"
				+ "Connection: close\r\n"
				+ "\r\n"
				+ "\n"
				+ "======================[ REQUEST SENT ]======================\n"
				+ "HTTP/1.1 200 OK\r\n"
				+ "connection: close\r\n"
				+ "content-encoding: deflate\r\n"
				+ "transfer-encoding: chunked\r\n"
                + "\r\n"
                + "10\r\n";
            String outShouldEndWith =
				  "0\r\n"
				+ "\r\n"
				+ "\n"
				+ "===================[ RESPONSE RECEIVED ]====================\n"
				+ "=============[ CONVERTING FROM CHUNKED FORMAT ]=============\n"
				+ "======================[ UNDEFLATING ]=======================\n"
				+ "==========================[ RAW ]===========================\n"
				+ "TEST_TEST_TEST\n"
                + "==========================[ END ]===========================\n";
            // @formatter:on

            assertTrue(appOut.startsWith(outShouldStartWith), "Out should start with test text");
            assertTrue(appOut.endsWith(outShouldEndWith), "Out should end with test text");
        }
    }

    @Test
    public void testDeflateHttp2() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "https", 10001, "/test").responseBody("TEST_TEST_TEST")
                .useHttp2().build()) {

            // @formatter:off
            String request = 
                  ":authority: localhost:10001\n" //
                + ":method: GET\n"
                + ":path: /test\n"
                + ":scheme: https\n"
                + "accept-encoding: deflate";
            // @formatter:on

            String appOut = runApp(request);

            // We will check out start and end frather than full match not to bother with
            // gzip byte mess in the middle

            // @formatter:off
            String outShouldStartWith = 
                  "====================[ Protocol: HTTP_2 ]====================\n"
                + "====================[ RESPONSE HEADERS ]====================\n"
                + "200 OK HTTP_2\n"
                + "content-encoding: deflate\n"
                + "=====================[ RESPONSE BODY ]======================\n";
            String outShouldEndWith =
                  "======================[ UNDEFLATING ]=======================\n"
                + "==========================[ RAW ]===========================\n"
                + "TEST_TEST_TEST\n"
                + "==========================[ END ]===========================\n";
            // @formatter:on

            assertTrue(appOut.startsWith(outShouldStartWith), "Out should start with test text");
            assertTrue(appOut.endsWith(outShouldEndWith), "Out should end with test text");
        }
    }

    @Test
    public void testJSONHttp1() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "http", 10001, "/test")
                .responseContentType("application/json")
                .responseBody("{\"value1\":null,\"value2\":{\"value3\":[1,2,3]}}").build()) {

            // @formatter:off
            String request = 
                      "GET /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Connection: close";
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
                "====================[ Protocol: HTTP_1 ]====================\n"
				+ "====================[ SENDING REQUEST ]=====================\n"
				+ "GET /test HTTP/1.1\r\n"
				+ "Host: localhost:10001\r\n"
				+ "Connection: close\r\n"
				+ "\r\n"
				+ "\n"
				+ "======================[ REQUEST SENT ]======================\n"
				+ "HTTP/1.1 200 OK\r\n"
				+ "content-type: application/json\r\n"
				+ "content-length: 43\r\n"
				+ "connection: close\r\n"
				+ "\r\n"
				+ "{\"value1\":null,\"value2\":{\"value3\":[1,2,3]}}\n"
				+ "===================[ RESPONSE RECEIVED ]====================\n"
				+ "==========================[ JSON ]==========================\n"
				+ "{\n"
				+ "  \"value2\": {\"value3\": [\n"
				+ "    1,\n"
				+ "    2,\n"
				+ "    3\n"
				+ "  ]},\n"
				+ "  \"value1\": null\n"
				+ "}\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testJSONHttp2() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "https", 10001, "/test")
                .responseContentType("application/json")
                .responseBody("{\"value1\":null,\"value2\":{\"value3\":[1,2,3]}}").useHttp2().build()) {

            // @formatter:off
            String request = 
                  ":authority: localhost:10001\n" //
                + ":method: GET\n"
                + ":path: /test\n"
                + ":scheme: https";
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
                 "====================[ Protocol: HTTP_2 ]====================\n"
				+ "====================[ RESPONSE HEADERS ]====================\n"
				+ "200 OK HTTP_2\n"
				+ "content-type: application/json\n"
				+ "content-length: 43\n"
				+ "=====================[ RESPONSE BODY ]======================\n"
				+ "{\"value1\":null,\"value2\":{\"value3\":[1,2,3]}}\n"
				+ "==========================[ JSON ]==========================\n"
				+ "{\n"
				+ "  \"value2\": {\"value3\": [\n"
				+ "    1,\n"
				+ "    2,\n"
				+ "    3\n"
				+ "  ]},\n"
				+ "  \"value1\": null\n"
				+ "}\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testXMLHttp2() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "https", 10001, "/test")
                .responseContentType("application/xml")
                .responseBody(
                        "<root> <tag1> text &lt;&amp;&gt; </tag1><tag2 attr1=\"attr2value\"><tag3><![CDATA[[CDATA VALUE]]></tag3></tag2></root>")
                .useHttp2().build()) {

            // @formatter:off
            String request = 
                  ":authority: localhost:10001\n" //
                + ":method: GET\n"
                + ":path: /test\n"
                + ":scheme: https";
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
                  "====================[ Protocol: HTTP_2 ]====================\n"
				+ "====================[ RESPONSE HEADERS ]====================\n"
				+ "200 OK HTTP_2\n"
				+ "content-type: application/xml\n"
				+ "content-length: 116\n"
				+ "=====================[ RESPONSE BODY ]======================\n"
				+ "<root> <tag1> text &lt;&amp;&gt; </tag1><tag2 attr1=\"attr2value\"><tag3><![CDATA[[CDATA VALUE]]></tag3></tag2></root>\n"
				+ "==========================[ XML ]===========================\n"
				+ "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
				+ "<root>\n"
				+ "    <tag1> text &lt;&amp;&gt; </tag1>\n"
				+ "    <tag2 attr1=\"attr2value\">\n"
				+ "        <tag3>\n"
				+ "            <![CDATA[[CDATA VALUE]]>\n"
				+ "        </tag3>\n"
				+ "    </tag2>\n"
				+ "</root>\n"
				+ "\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testXMLHttp1() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "http", 10001, "/test")
                .responseContentType("application/xml")
                .responseBody(
                        "<root> <tag1> text &lt;&amp;&gt; </tag1><tag2 attr1=\"attr2value\"><tag3><![CDATA[[CDATA VALUE]]></tag3></tag2></root>")
                .build()) {

            // @formatter:off
            String request = 
                      "GET /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Connection: close";
            // @formatter:on

            String appOut = runApp(request);

            // @formatter:off
            assertEquals(
                    "====================[ Protocol: HTTP_1 ]====================\n"
                  + "====================[ SENDING REQUEST ]=====================\n"
                  + "GET /test HTTP/1.1\r\n"
                  + "Host: localhost:10001\r\n"
                  + "Connection: close\r\n"
                  + "\r\n"
                  + "\n"
                  + "======================[ REQUEST SENT ]======================\n"
                  + "HTTP/1.1 200 OK\r\n"
                  + "content-type: application/xml\r\n"
                  + "content-length: 116\r\n"
                  + "connection: close\r\n"
                  + "\r\n"
                  + "<root> <tag1> text &lt;&amp;&gt; </tag1><tag2 attr1=\"attr2value\"><tag3><![CDATA[[CDATA VALUE]]></tag3></tag2></root>\n"
                  + "===================[ RESPONSE RECEIVED ]====================\n"
                  + "==========================[ XML ]===========================\n"
                  + "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                  + "<root>\n"
                  + "    <tag1> text &lt;&amp;&gt; </tag1>\n"
                  + "    <tag2 attr1=\"attr2value\">\n"
                  + "        <tag3>\n"
                  + "            <![CDATA[[CDATA VALUE]]>\n"
                  + "        </tag3>\n"
                  + "    </tag2>\n"
                  + "</root>\n"
                  + "\n"
                  + "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    @Test
    public void testGetHttp1Tls() throws Exception {
        try (TestServer server = new TestServer.Builder("GET", "https", 10001, "/test").useTls().build()) {

            // @formatter:off
            String request =
                      "GET /test HTTP/1.1\n" //
                    + "Host: localhost:10001\n" //
                    + "Connection: close";
            // @formatter:on

            String appOut = runApp(request, "-tls");

            // @formatter:off
            assertEquals(
                  "====================[ Protocol: HTTP_1 ]====================\n"
				+ "====================[ SENDING REQUEST ]=====================\n"
				+ "GET /test HTTP/1.1\r\n"
				+ "Host: localhost:10001\r\n"
				+ "Connection: close\r\n"
				+ "\r\n"
				+ "\n"
				+ "======================[ REQUEST SENT ]======================\n"
				+ "HTTP/1.1 204 No Content\r\n"
				+ "connection: close\r\n"
				+ "\r\n"
				+ "\n"
				+ "===================[ RESPONSE RECEIVED ]====================\n"
				+ "==========================[ RAW ]===========================\n"
				+ "\n"
				+ "==========================[ END ]===========================\n", appOut);
            // @formatter:on
        }
    }

    // =========================== UTILS ======================

    private String runApp(String request, String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        main.setSystemStreams(new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(baos));
        main.run(args);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static final String withBody(String headers, String body) {
        int lentgh = body.getBytes(StandardCharsets.UTF_8).length;
        return headers + "\n" //
                + "Content-Length: " + lentgh //
                + "\n\n" //
                + body;
    }

    // private static void w(String s) throws Exception {
    // System.out.println("-------------");
    // System.out.println(s);
    // Files.writeString(Paths.get("/home/nick/dev/_temp/z1.txt"), s);
    // System.out.println("-------------");
    // }

}