package com.tencent.trtcasr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.trtcasr.common.SignatureParams;
import com.tencent.trtcasr.common.SpeakerRole;

/** SignatureParams query-building tests, ported from the Go SDK. */
class SignatureParamsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String queryGet(String qs, String key) {
        for (String pair : qs.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals(key)) {
                return percentDecode(pair.substring(idx + 1));
            }
        }
        return null;
    }

    private static List<String> queryKeys(String qs) {
        List<String> keys = new ArrayList<>();
        for (String pair : qs.split("&")) {
            keys.add(pair.substring(0, pair.indexOf('=')));
        }
        return keys;
    }

    static String percentDecode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') {
                out.write(' ');
            } else if (c == '%' && i + 2 < s.length()) {
                out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                out.write((byte) c);
            }
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void defaults() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        assertEquals(1300403317L, p.getAppId());
        assertEquals("16k_zh", p.getEngineModelType());
        assertEquals("voice-001", p.getVoiceId());
        assertEquals(1, p.getVoiceFormat());
        assertEquals(1, p.getNeedVad());
        assertTrue(p.getTimestamp() != 0);
        assertTrue(p.getExpired() > p.getTimestamp());
        assertTrue(p.getNonce() >= 1 && p.getNonce() <= 9_999_999);
    }

    @Test
    void queryStringContainsRequiredKeys() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        String qs = p.buildQueryString();
        assertFalse(qs.isEmpty());
        for (String key : new String[]{
                "secretid=", "timestamp=", "expired=", "nonce=", "engine_model_type=",
                "voice_id="}) {
            assertTrue(qs.contains(key), "missing " + key);
        }
        assertTrue(qs.contains("secretid=1300403317"));
        assertFalse(qs.contains("signature="));
    }

    @Test
    void queryStringWithSignature() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        p.setSdkAppId(1400000000L);
        String userSig = "eJwtzDEOgCAQRdG9UBMH-test-user-sig";
        String qs = p.buildQueryStringWithSignature(userSig);

        assertEquals(userSig, queryGet(qs, "signature"));
        assertEquals(userSig, queryGet(qs, "usersig"));
        assertEquals("1400000000", queryGet(qs, "sdkappid"));
        for (String key : new String[]{"secretid", "timestamp", "expired", "nonce"}) {
            assertTrue(queryGet(qs, key) != null, "missing " + key);
        }
    }

    @Test
    void secretKeyNeverInQueryString() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        String qs = p.buildQueryStringWithSignature("some-user-sig");
        assertFalse(qs.contains("secret_key"));
        assertFalse(qs.contains("secretkey"));
    }

    @Test
    void omitsUnsetOptionalParams() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        String qs = p.buildQueryString();
        for (String key : new String[]{
                "speaker_diarization", "speaker_number", "speaker_roles", "voiceprintids",
                "noise_threshold", "vad_level", "filter_empty_result", "hotword_list",
                "replace_text_id", "input_sample_rate", "sdkappid", "language"}) {
            assertFalse(qs.contains(key + "="), key + " should be omitted: " + qs);
        }
    }

    @Test
    void speakerDiarizationCluster() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        p.setSpeakerDiarization(SignatureParams.SPEAKER_DIARIZATION_CLUSTER);
        p.setSpeakerNumber(2);
        // Enrollment input only applies to mode 3 and must not leak into mode 1.
        p.setSpeakerRoles(List.of(new SpeakerRole("teacher", "https://example.com/a.wav")));
        p.setVoiceprintIds(List.of("vp-1"));

        String qs = p.buildQueryString();
        assertEquals("1", queryGet(qs, "speaker_diarization"));
        assertEquals("2", queryGet(qs, "speaker_number"));
        assertNull(queryGet(qs, "speaker_roles"));
        assertNull(queryGet(qs, "voiceprintids"));
    }

    @Test
    void speakerDiarizationVoiceprint() throws Exception {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        p.setSpeakerDiarization(SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT);
        p.setSpeakerRoles(List.of(
                new SpeakerRole("teacher", "https://example.com/a.wav"),
                new SpeakerRole("student", "https://example.com/b.wav")));
        p.setVoiceprintIds(List.of("vp-1", "vp-2"));
        p.setSpeakerNumber(0); // auto detection: omitted

        String qs = p.buildQueryString();
        assertEquals("3", queryGet(qs, "speaker_diarization"));
        assertNull(queryGet(qs, "speaker_number"));

        JsonNode roles = MAPPER.readTree(queryGet(qs, "speaker_roles"));
        assertEquals(2, roles.size());
        assertEquals("teacher", roles.get(0).get("RoleName").asText());
        assertEquals("https://example.com/b.wav", roles.get(1).get("AudioUrl").asText());

        JsonNode ids = MAPPER.readTree(queryGet(qs, "voiceprintids"));
        assertEquals(2, ids.size());
        assertEquals("vp-1", ids.get(0).asText());
    }

    @Test
    void triStateVadTuning() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        p.setVadLevel(0);
        p.setNoiseThreshold(0.0);
        p.setFilterEmptyResult(0);

        // An explicit 0 differs from "unset": the server defaults vad_level
        // to 1 and filter_empty_result to 1, so both must reach the wire.
        String qs = p.buildQueryString();
        assertEquals("0", queryGet(qs, "vad_level"));
        assertEquals("0", queryGet(qs, "filter_empty_result"));
        // Go strconv.FormatFloat('f', 3): "0.000".
        assertEquals("0.000", queryGet(qs, "noise_threshold"));

        p.setNoiseThreshold(1.5);
        qs = p.buildQueryString();
        assertEquals("1.500", queryGet(qs, "noise_threshold"));
    }

    @Test
    void advancedOptionalParams() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        p.setHotwordList("腾讯云|5,ASR|11");
        p.setReplaceTextId("replace-1");
        p.setInputSampleRate(8000);
        p.setLanguage("zh");

        String qs = p.buildQueryString();
        assertEquals("腾讯云|5,ASR|11", queryGet(qs, "hotword_list"));
        assertEquals("replace-1", queryGet(qs, "replace_text_id"));
        assertEquals("8000", queryGet(qs, "input_sample_rate"));
        assertEquals("zh", queryGet(qs, "language"));
    }

    @Test
    void queryKeysAreSorted() {
        SignatureParams p = new SignatureParams(1300403317L, "16k_zh", "voice-001");
        p.setHotwordId("hw");
        String qs = p.buildQueryStringWithSignature("sig");
        List<String> keys = queryKeys(qs);
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(String::compareTo);
        assertEquals(sorted, keys, "query keys must be sorted like Go's sort.Strings");
    }

    @Test
    void queryEscapeMatchesGoSemantics() {
        assertEquals("abcXYZ019-_.~", SignatureParams.queryEscape("abcXYZ019-_.~"));
        assertEquals("a+b", SignatureParams.queryEscape("a b"));
        assertEquals("a%2Bb", SignatureParams.queryEscape("a+b"));
        assertEquals("%E8%AF%8D%7C5%2C", SignatureParams.queryEscape("词|5,"));
        assertEquals("100%25", SignatureParams.queryEscape("100%"));
    }
}
