package com.tencent.trtcasr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.UserSig;

/** UserSig tests: structure, HMAC correctness, base64url round-trip. */
class UserSigTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Decodes a UserSig: base64url → zlib inflate → JSON document. */
    private static JsonNode decodeSig(String sig) throws Exception {
        byte[] compressed = UserSig.base64UrlDecode(sig);
        InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed));
        byte[] json = inflater.readAllBytes();
        return MAPPER.readTree(new String(json, StandardCharsets.UTF_8));
    }

    private static String expectedHmac(String key, String userId, long sdkAppId, long now,
            long expire) throws Exception {
        String content = "TLS.identifier:" + userId + "\n"
                + "TLS.sdkappid:" + sdkAppId + "\n"
                + "TLS.time:" + now + "\n"
                + "TLS.expire:" + expire + "\n";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void structureAndSignature() throws Exception {
        long sdkAppId = 1400000000L;
        String key = "test-secret-key-for-unit-testing";
        String userId = "test-user-001";
        long expire = 86400;
        long now = 1756800000L; // fixed timestamp for determinism

        String sig = UserSig.genUserSigAt(sdkAppId, key, userId, expire, now);
        JsonNode doc = decodeSig(sig);

        assertEquals("2.0", doc.get("TLS.ver").asText());
        assertEquals(userId, doc.get("TLS.identifier").asText());
        assertEquals(sdkAppId, doc.get("TLS.sdkappid").asLong());
        assertEquals(expire, doc.get("TLS.expire").asLong());
        assertEquals(now, doc.get("TLS.time").asLong());
        assertFalse(doc.has("TLS.userbuf"));

        // TLS.sig is the standard base64 of the HMAC-SHA256 over the
        // documented content string — what the server recomputes.
        assertEquals(expectedHmac(key, userId, sdkAppId, now, expire),
                doc.get("TLS.sig").asText());
    }

    @Test
    void deterministicForFixedTime() throws Exception {
        String a = UserSig.genUserSigAt(1400000000L, "key", "user", 86400, 1756800000L);
        String b = UserSig.genUserSigAt(1400000000L, "key", "user", 86400, 1756800000L);
        assertEquals(a, b);
    }

    @Test
    void defaultExpire() throws Exception {
        String sig = UserSig.genUserSig(1400000000L, "key", "user", 0);
        JsonNode doc = decodeSig(sig);
        assertEquals(UserSig.DEFAULT_EXPIRE, doc.get("TLS.expire").asLong());
    }

    @Test
    void variousInputs() throws Exception {
        long[][] ids = {{1400000001L}, {1400000002L}, {1400000003L}};
        String[] keys = {"key1", "key2", "key-with-special-chars!@#$%"};
        String[] users = {"user1", "user2", "user-with-dashes"};
        for (int i = 0; i < ids.length; i++) {
            String sig = UserSig.genUserSig(ids[i][0], keys[i], users[i], 86400);
            JsonNode doc = decodeSig(sig);
            assertEquals(ids[i][0], doc.get("TLS.sdkappid").asLong());
            assertEquals(users[i], doc.get("TLS.identifier").asText());
        }
    }

    @Test
    void rejectsEmptyKeyOrUser() {
        assertThrows(ASRException.class, () -> UserSig.genUserSig(1, "", "user", 86400));
        assertThrows(ASRException.class, () -> UserSig.genUserSig(1, "key", "", 86400));
        assertThrows(ASRException.class, () -> UserSig.genUserSig(1, null, "user", 86400));
    }

    @Test
    void base64UrlRoundTripAndAlphabet() throws Exception {
        byte[] data = {(byte) 0xfb, (byte) 0xff, (byte) 0xff, 0x3e, (byte) 0x80};
        String std = Base64.getEncoder().encodeToString(data);
        assertTrue(std.contains("+") || std.contains("/") || std.contains("="));

        String encoded = UserSig.base64UrlEncode(data);
        assertFalse(encoded.contains("+"));
        assertFalse(encoded.contains("/"));
        assertFalse(encoded.contains("="));
        assertTrue(encoded.contains("*") || encoded.contains("-") || encoded.contains("_"));

        byte[] decoded = UserSig.base64UrlDecode(encoded);
        org.junit.jupiter.api.Assertions.assertArrayEquals(data, decoded);
        assertNotNull(decodeSig(UserSig.genUserSig(1, "k", "u", 86400)));
    }
}
