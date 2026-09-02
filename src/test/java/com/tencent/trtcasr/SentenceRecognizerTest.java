package com.tencent.trtcasr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.trtcasr.asr.SentenceRecognizer;
import com.tencent.trtcasr.asr.SentenceRecognizer.SentenceRecognitionRequest;
import com.tencent.trtcasr.asr.SentenceRecognizer.SentenceRecognitionResult;
import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.Credential;
import com.tencent.trtcasr.common.ErrorCodes;

/** SentenceRecognizer tests, ported from the Go SDK's sentence_recognizer_test.go. */
class SentenceRecognizerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Credential testCredential() {
        return new Credential(1300000000L, 1400000000L, "test-secret");
    }

    @Test
    void recognizeRejectsInvalidRequests() {
        SentenceRecognizer r = new SentenceRecognizer(testCredential());

        ASRException err = assertThrows(ASRException.class,
                () -> r.recognize(new SentenceRecognitionRequest()));
        assertEquals(ErrorCodes.INVALID_PARAM, err.getCode());
        assertTrue(err.getMessage().contains("EngServiceType is required"));

        SentenceRecognitionRequest noFormat = new SentenceRecognitionRequest();
        noFormat.setEngServiceType("16k_zh");
        err = assertThrows(ASRException.class, () -> r.recognize(noFormat));
        assertTrue(err.getMessage().contains("VoiceFormat is required"));

        SentenceRecognitionRequest urlNoUrl = new SentenceRecognitionRequest();
        urlNoUrl.setEngServiceType("16k_zh");
        urlNoUrl.setVoiceFormat("pcm");
        urlNoUrl.setSourceType(SentenceRecognizer.SOURCE_TYPE_URL);
        err = assertThrows(ASRException.class, () -> r.recognize(urlNoUrl));
        assertTrue(err.getMessage().contains("Url is required when SourceType=0"));

        SentenceRecognitionRequest dataNoData = new SentenceRecognitionRequest();
        dataNoData.setEngServiceType("16k_zh");
        dataNoData.setVoiceFormat("pcm");
        dataNoData.setSourceType(SentenceRecognizer.SOURCE_TYPE_DATA);
        err = assertThrows(ASRException.class, () -> r.recognize(dataNoData));
        assertTrue(err.getMessage().contains("Data is required when SourceType=1"));
    }

    @Test
    void recognizeDataRejectsEmptyAndOversized() {
        SentenceRecognizer r = new SentenceRecognizer(testCredential());

        ASRException err = assertThrows(ASRException.class,
                () -> r.recognizeData(new byte[0], "pcm", "16k_zh"));
        assertTrue(err.getMessage().contains("audio data is empty"));

        byte[] big = new byte[3 * 1024 * 1024 + 1];
        err = assertThrows(ASRException.class, () -> r.recognizeData(big, "pcm", "16k_zh"));
        assertTrue(err.getMessage().contains("3MB"));
    }

    @Test
    void recognizeUrlRejectsEmptyUrl() {
        SentenceRecognizer r = new SentenceRecognizer(testCredential());
        ASRException err = assertThrows(ASRException.class,
                () -> r.recognizeUrl("", "wav", "16k_zh"));
        assertTrue(err.getMessage().contains("audio URL is empty"));
    }

    @Test
    void recognizeDataSuccess() throws Exception {
        try (MockHttpServer server = new MockHttpServer(req -> {
            try {
                assertEquals("POST", req.method);
                assertEquals("/v1/SentenceRecognition", req.path);
                assertEquals("application/json; charset=utf-8", req.header("Content-Type"));
                assertNotNull(req.header("X-TRTC-SdkAppId"));
                assertNotNull(req.header("X-TRTC-UserSig"));
                assertNotNull(req.queryParam("AppId"));
                assertNotNull(req.queryParam("Secretid"));
                assertNotNull(req.queryParam("RequestId"));
                assertNotNull(req.queryParam("Timestamp"));

                JsonNode body = MAPPER.readTree(req.body);
                assertEquals("16k_zh_en", body.get("EngSerViceType").asText());
                assertEquals(SentenceRecognizer.SOURCE_TYPE_DATA, body.get("SourceType").asInt());
                assertEquals("pcm", body.get("VoiceFormat").asText());
                byte[] raw = Base64.getDecoder().decode(body.get("Data").asText());
                assertEquals("fake-pcm-audio", new String(raw, StandardCharsets.UTF_8));
                assertEquals(14, body.get("DataLen").asInt());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return MockHttpServer.MockResponse.json(
                    "{\"Response\":{\"Result\":\"今天天气不错。\",\"AudioDuration\":2380,"
                            + "\"WordSize\":1,\"WordList\":[{\"Word\":\"今天\",\"StartTime\":200,\"EndTime\":500}],"
                            + "\"RequestId\":\"req-1\"}}");
        })) {
            SentenceRecognizer r = new SentenceRecognizer(testCredential());
            r.setEndpoint(server.url());

            SentenceRecognitionResult result =
                    r.recognizeData("fake-pcm-audio".getBytes(StandardCharsets.UTF_8), "pcm", "16k_zh_en");
            assertEquals("今天天气不错。", result.getResult());
            assertEquals(2380, result.getAudioDuration());
            assertEquals(1, result.getWordSize());
            assertEquals(1, result.getWordList().size());
            assertEquals("今天", result.getWordList().get(0).getWord());
            assertEquals("req-1", result.getRequestId());
        }
    }

    @Test
    void recognizeUrlSuccess() throws Exception {
        try (MockHttpServer server = new MockHttpServer(req -> {
            try {
                JsonNode body = MAPPER.readTree(req.body);
                assertEquals(SentenceRecognizer.SOURCE_TYPE_URL, body.get("SourceType").asInt());
                assertEquals("https://example.com/test.wav", body.get("Url").asText());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return MockHttpServer.MockResponse.json(
                    "{\"Response\":{\"Result\":\"hello\",\"AudioDuration\":1000,\"RequestId\":\"req-2\"}}");
        })) {
            SentenceRecognizer r = new SentenceRecognizer(testCredential());
            r.setEndpoint(server.url());
            SentenceRecognitionResult result =
                    r.recognizeUrl("https://example.com/test.wav", "wav", "16k_zh_en");
            assertEquals("hello", result.getResult());
        }
    }

    @Test
    void recognizeServerError() throws Exception {
        try (MockHttpServer server = new MockHttpServer(req -> MockHttpServer.MockResponse.json(
                "{\"Response\":{\"Error\":{\"Code\":\"4002\",\"Message\":\"鉴权失败\"},\"RequestId\":\"req-err\"}}"))) {
            SentenceRecognizer r = new SentenceRecognizer(testCredential());
            r.setEndpoint(server.url());

            ASRException err = assertThrows(ASRException.class,
                    () -> r.recognizeData("fake-audio".getBytes(StandardCharsets.UTF_8), "pcm", "16k_zh_en"));
            assertEquals(ErrorCodes.SERVER_ERROR, err.getCode());
            assertTrue(err.getMessage().contains("4002"), err.getMessage());
            assertTrue(err.getMessage().contains("req-err"), err.getMessage());
        }
    }

    @Test
    void recognizeHttpError() throws Exception {
        try (MockHttpServer server = new MockHttpServer(
                req -> new MockHttpServer.MockResponse(500, "internal server error"))) {
            SentenceRecognizer r = new SentenceRecognizer(testCredential());
            r.setEndpoint(server.url());

            ASRException err = assertThrows(ASRException.class,
                    () -> r.recognizeData("fake-audio".getBytes(StandardCharsets.UTF_8), "pcm", "16k_zh_en"));
            assertTrue(err.getMessage().contains("500"), err.getMessage());
        }
    }

    @Test
    void recognizeDataWithOptions() throws Exception {
        try (MockHttpServer server = new MockHttpServer(req -> {
            try {
                JsonNode body = MAPPER.readTree(req.body);
                assertEquals(1, body.get("FilterDirty").asInt());
                assertEquals(2, body.get("WordInfo").asInt());
                assertEquals("hw-123", body.get("HotwordId").asText());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return MockHttpServer.MockResponse.json(
                    "{\"Response\":{\"Result\":\"ok\",\"AudioDuration\":10,\"RequestId\":\"r\"}}");
        })) {
            SentenceRecognizer r = new SentenceRecognizer(testCredential());
            r.setEndpoint(server.url());

            SentenceRecognitionRequest req = new SentenceRecognitionRequest();
            req.setEngServiceType("16k_zh_en");
            req.setVoiceFormat("pcm");
            req.setFilterDirty(1);
            req.setWordInfo(2);
            req.setHotwordId("hw-123");
            SentenceRecognitionResult result = r.recognizeDataWithOptions(
                    "fake-audio".getBytes(StandardCharsets.UTF_8), req);
            assertEquals("ok", result.getResult());
        }
    }

    @Test
    void presetUserSigIsSentVerbatim() throws Exception {
        try (MockHttpServer server = new MockHttpServer(
                req -> MockHttpServer.MockResponse.json("{\"Response\":{\"Result\":\"ok\",\"RequestId\":\"r\"}}"))) {
            Credential cred = testCredential();
            cred.setUserSig("preset-user-sig-value");
            SentenceRecognizer r = new SentenceRecognizer(cred);
            r.setEndpoint(server.url());

            r.recognizeData("fake-audio".getBytes(StandardCharsets.UTF_8), "pcm", "16k_zh");
            assertEquals(1, server.requests().size());
            assertEquals("preset-user-sig-value",
                    server.requests().get(0).header("X-TRTC-UserSig"));
        }
    }

    @Test
    void requestSerializationOmitsEmptyFields() throws Exception {
        SentenceRecognitionRequest req = new SentenceRecognitionRequest();
        req.setEngServiceType("16k_zh_en");
        req.setSourceType(SentenceRecognizer.SOURCE_TYPE_URL);
        req.setVoiceFormat("wav");
        req.setUrl("https://example.com/a.wav");
        String json = MAPPER.writeValueAsString(req);
        assertTrue(json.contains("EngSerViceType"));
        assertTrue(json.contains("\"SourceType\":0"), "SourceType=0 must be sent: " + json);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("HotwordId"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("FilterDirty"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("DataLen"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("Language"));
    }
}
