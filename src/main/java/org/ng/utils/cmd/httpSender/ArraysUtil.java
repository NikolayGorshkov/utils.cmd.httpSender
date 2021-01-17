package org.ng.utils.cmd.httpSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class ArraysUtil {

    public static int indexOf(byte[] src, int offset, byte[] pattern) {
        outer: for (int i = offset; i < src.length - (pattern.length - 1); i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (src[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static final byte[] chunkedSep = "\r\n".getBytes(StandardCharsets.ISO_8859_1);

    public static byte[] parseChunkedResponse(byte[] src) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(src.length);
        for (int offset = 0;;) {
            int sepIdx = indexOf(src, offset, chunkedSep);
            if (sepIdx == -1) {
                throw new IllegalStateException("Chunked separator not found");
            }
            String lengthStr = new String(src, offset, sepIdx - offset, StandardCharsets.ISO_8859_1);
            int lengthToRead = Integer.parseInt(lengthStr, 16);
            if (lengthToRead == 0) {
                // according to the spec we may have adidtional headers in the traler, but let's
                // skip them for now
                break;
            }
            offset = sepIdx + chunkedSep.length;
            baos.write(src, offset, lengthToRead);
            offset += lengthToRead + chunkedSep.length;
        }
        return baos.toByteArray();
    }

    private ArraysUtil() {
        // noop
    }

}