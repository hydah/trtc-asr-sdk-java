package com.tencent.trtcasr.common;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TRTC UserSig generation (TLS sig API v2 compatible).
 *
 * <p>Layout of the ticket (identical to the official Go/Java
 * implementations):
 * <ol>
 * <li>Build a JSON document
 * {@code {"TLS.ver":"2.0","TLS.identifier":..,"TLS.sdkappid":..,"TLS.expire":..,"TLS.time":..,"TLS.sig":..}}
 * where {@code TLS.sig} is the standard base64 of the HMAC-SHA256 of the
 * string
 * {@code "TLS.identifier:<id>\nTLS.sdkappid:<appid>\nTLS.time:<now>\nTLS.expire:<expire>\n"}
 * keyed by the SDK secret key.</li>
 * <li>zlib-compress the JSON document.</li>
 * <li>Encode with the Tencent variant of base64url: alphabet
 * {@code A-Za-z0-9*-}, padding {@code _} (i.e. {@code +}→{@code *},
 * {@code /}→{@code -}, {@code =}→{@code _}).</li>
 * </ol>
 */
public final class UserSig {
    /** Default UserSig validity: 180 days in seconds (matches the Go SDK). */
    public static final long DEFAULT_EXPIRE = 86400L * 180;

    private UserSig() {
    }

    /**
     * Generates a TRTC UserSig.
     *
     * @param sdkAppId TRTC application ID
     * @param key      TRTC SDK secret key
     * @param userId   unique user identifier (maps to voice_id in ASR)
     * @param expire   signature validity in seconds; 0 uses
     *                 {@link #DEFAULT_EXPIRE}
     */
    public static String genUserSig(long sdkAppId, String key, String userId, long expire)
            throws ASRException {
        if (expire <= 0) {
            expire = DEFAULT_EXPIRE;
        }
        return genUserSigAt(sdkAppId, key, userId, expire, Instant.now().getEpochSecond());
    }

    /**
     * Deterministic core of {@link #genUserSig} with an explicit timestamp,
     * exposed for tests and for callers that need reproducible signatures.
     */
    public static String genUserSigAt(long sdkAppId, String key, String userId, long expire,
            long now) throws ASRException {
        if (key == null || key.isEmpty()) {
            throw ASRException.invalidParam("secret key is empty");
        }
        if (userId == null || userId.isEmpty()) {
            throw ASRException.invalidParam("user id is empty");
        }

        String content = "TLS.identifier:" + userId + "\n"
                + "TLS.sdkappid:" + sdkAppId + "\n"
                + "TLS.time:" + now + "\n"
                + "TLS.expire:" + expire + "\n";
        byte[] sig = hmacSha256(key, content);

        String doc = "{"
                + "\"TLS.ver\":\"2.0\","
                + "\"TLS.identifier\":\"" + jsonEscape(userId) + "\","
                + "\"TLS.sdkappid\":" + sdkAppId + ","
                + "\"TLS.expire\":" + expire + ","
                + "\"TLS.time\":" + now + ","
                + "\"TLS.sig\":\"" + Base64.getEncoder().encodeToString(sig) + "\""
                + "}\n";

        return base64UrlEncode(zlibCompress(doc.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmacSha256(String key, String content) throws ASRException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ASRException(ErrorCodes.INVALID_PARAM, "hmac-sha256 failed: " + e.getMessage(), e);
        }
    }

    private static byte[] zlibCompress(byte[] data) throws ASRException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(out,
                    new Deflater(Deflater.DEFAULT_COMPRESSION))) {
                deflater.write(data);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new ASRException(ErrorCodes.INVALID_PARAM, "zlib compress failed: " + e.getMessage(), e);
        }
    }

    /** Encodes bytes with the Tencent base64url variant used by UserSig. */
    public static String base64UrlEncode(byte[] data) {
        return Base64.getEncoder().encodeToString(data)
                .replace('+', '*')
                .replace('/', '-')
                .replace('=', '_');
    }

    /** Decodes the Tencent base64url variant. Provided for tooling/tests. */
    public static byte[] base64UrlDecode(String s) {
        String std = s.replace('_', '=').replace('-', '/').replace('*', '+');
        return Base64.getDecoder().decode(std);
    }

    static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
