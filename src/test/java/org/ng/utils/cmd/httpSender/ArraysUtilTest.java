package org.ng.utils.cmd.httpSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class ArraysUtilTest {

    @Test
    public void testIndexOfStart() {
        byte[] seq = "\r\n\r\ntest123456789".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(0, ArraysUtil.indexOf(seq, 0, pattern));
    }

    @Test
    public void testIndexOfNegative() {
        byte[] seq = "\r\n \r\ntest123456789".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(-1, ArraysUtil.indexOf(seq, 0, pattern));
    }

    @Test
    public void testIndexOfEnd() {
        byte[] seq = "test123456789\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(13, ArraysUtil.indexOf(seq, 0, pattern));
    }

    @Test
    public void testIndexOfMiddle() {
        byte[] seq = "test123\r\n\r\n456789".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(7, ArraysUtil.indexOf(seq, 0, pattern));
    }

    @Test
    public void testIndexOfStartWithPrefix() {
        byte[] seq = "123\r\n\r\ntest123456789".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(3, ArraysUtil.indexOf(seq, 3, pattern));
    }

    @Test
    public void testIndexOfNegativeWithPrefix() {
        byte[] seq = "\r\n\r\ntest123456789".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(-1, ArraysUtil.indexOf(seq, 1, pattern));
    }

    @Test
    public void testIndexOfEndWithPrefix() {
        byte[] seq = "test123456789\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(13, ArraysUtil.indexOf(seq, 3, pattern));
    }

    @Test
    public void testIndexOfMiddleWithPrefix() {
        byte[] seq = "test123\r\n\r\n456789".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pattern = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(7, ArraysUtil.indexOf(seq, 3, pattern));
    }

    @Test
    public void testParseChunkedResponse() {
        assertArrayEquals("ZZZ1ZZZ123\nzzz456\r\nzzz789".getBytes(StandardCharsets.ISO_8859_1),
                ArraysUtil.parseChunkedResponse(("4\r\n" + //
                        "ZZZ1\r\n" + //
                        "15\r\n" + //
                        "ZZZ123\nzzz456\r\nzzz789\r\n" + //
                        "0\r\n" + //
                        "\r\n").getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void testParseChunkedResponse_Negative() {
        assertThrows(IllegalStateException.class, () -> {
            assertArrayEquals("ZZZ1ZZZ123\nzzz456\r\nzzz789".getBytes(StandardCharsets.ISO_8859_1),
                    ArraysUtil.parseChunkedResponse(("4\r\n" + //
            "ZZZ1\r\n" + //
            "15\r\n" + //
            "ZZZ123\nzzz456\r\nzzz789\r\n" + //
            "1\r\n" + //
            "\r\n").getBytes(StandardCharsets.ISO_8859_1)));
        }, "Exception should be thrown for invalid text");
    }

}