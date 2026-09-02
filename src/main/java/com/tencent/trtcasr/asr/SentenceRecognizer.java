package com.tencent.trtcasr.asr;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.Credential;
import com.tencent.trtcasr.common.ErrorCodes;
import com.tencent.trtcasr.common.UserSig;

/**
 * One-shot sentence recognition client (HTTP). Audio duration must not
 * exceed 60s and file size must not exceed 3MB.
 */
public class SentenceRecognizer {
    /** Production HTTPS endpoint for sentence recognition. */
    public static final String SENTENCE_ENDPOINT = "https://asr.cloud-rtc.com";

    /** Audio from a URL. */
    public static final int SOURCE_TYPE_URL = 0;
    /** Audio data in the request body (base64 encoded). */
    public static final int SOURCE_TYPE_DATA = 1;

    /** Max audio size for one-shot recognition (before base64 encoding). */
    public static final int MAX_AUDIO_SIZE = 3 * 1024 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Credential credential;
    private String endpoint = SENTENCE_ENDPOINT;
    private HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private Duration requestTimeout = Duration.ofSeconds(30);

    public SentenceRecognizer(Credential credential) {
        this.credential = credential;
    }

    /** Overrides the default API endpoint (for testing). */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** Overrides the HTTP client. */
    public void setHttpClient(HttpClient client) {
        this.httpClient = client;
    }

    /** Overrides the per-request timeout (default 30s). */
    public void setRequestTimeout(Duration timeout) {
        this.requestTimeout = timeout;
    }

    /** Sends a sentence recognition request and returns the result. */
    public SentenceRecognitionResult recognize(SentenceRecognitionRequest req)
            throws ASRException {
        validateRequest(req);

        String requestId = UUID.randomUUID().toString();

        // Generate UserSig using RequestId as the userID per protocol spec.
        String userSig = credential.getUserSig();
        if (userSig.isEmpty()) {
            try {
                userSig = UserSig.genUserSig(credential.getSdkAppId(),
                        credential.getSecretKey(), requestId, 86400);
            } catch (ASRException e) {
                throw new ASRException(ErrorCodes.AUTH_FAILED,
                        "generate user sig failed: " + e.getRawMessage(), e);
            }
        }

        String reqUrl = String.format(
                "%s/v1/SentenceRecognition?AppId=%d&Secretid=%d&RequestId=%s&Timestamp=%d",
                endpoint, credential.getAppId(), credential.getAppId(), requestId,
                System.currentTimeMillis() / 1000);

        byte[] body;
        try {
            body = MAPPER.writeValueAsBytes(req);
        } catch (Exception e) {
            throw ASRException.invalidParam("marshal request failed: " + e.getMessage());
        }

        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(reqUrl))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TRTC-SdkAppId", Long.toString(credential.getSdkAppId()))
                .header("X-TRTC-UserSig", userSig)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> httpResp;
        try {
            httpResp = httpClient.send(httpReq,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ASRException(ErrorCodes.CONNECT_FAILED,
                    "http request failed: " + e.getMessage(), e);
        }

        String respBody = httpResp.body();
        if (httpResp.statusCode() != 200) {
            throw new ASRException(ErrorCodes.SERVER_ERROR,
                    "http status " + httpResp.statusCode() + ": " + respBody);
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(respBody);
        } catch (Exception e) {
            throw new ASRException(ErrorCodes.READ_FAILED,
                    "unmarshal response failed: " + e.getMessage(), e);
        }
        JsonNode response = root.get("Response");
        if (response == null || response.isNull()) {
            throw new ASRException(ErrorCodes.SERVER_ERROR, "empty response from server");
        }
        JsonNode error = response.get("Error");
        if (error != null && error.isObject()) {
            throw new ASRException(ErrorCodes.SERVER_ERROR, String.format(
                    "server error [%s]: %s (RequestId: %s)",
                    error.path("Code").asText(), error.path("Message").asText(),
                    response.path("RequestId").asText()));
        }

        try {
            return MAPPER.treeToValue(response, SentenceRecognitionResult.class);
        } catch (Exception e) {
            throw new ASRException(ErrorCodes.READ_FAILED,
                    "unmarshal response failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method that sends local audio data for recognition,
     * handling base64 encoding automatically.
     */
    public SentenceRecognitionResult recognizeData(byte[] data, String voiceFormat,
            String engineModelType) throws ASRException {
        if (data == null || data.length == 0) {
            throw ASRException.invalidParam("audio data is empty");
        }
        if (data.length > MAX_AUDIO_SIZE) {
            throw ASRException.invalidParam("audio data exceeds 3MB limit");
        }
        SentenceRecognitionRequest req = new SentenceRecognitionRequest();
        req.setEngServiceType(engineModelType);
        req.setSourceType(SOURCE_TYPE_DATA);
        req.setVoiceFormat(voiceFormat);
        req.setData(Base64.getEncoder().encodeToString(data));
        req.setDataLen(data.length);
        return recognize(req);
    }

    /** Convenience method that sends an audio URL for recognition. */
    public SentenceRecognitionResult recognizeUrl(String audioUrl, String voiceFormat,
            String engineModelType) throws ASRException {
        if (audioUrl == null || audioUrl.isEmpty()) {
            throw ASRException.invalidParam("audio URL is empty");
        }
        SentenceRecognitionRequest req = new SentenceRecognitionRequest();
        req.setEngServiceType(engineModelType);
        req.setSourceType(SOURCE_TYPE_URL);
        req.setVoiceFormat(voiceFormat);
        req.setUrl(audioUrl);
        return recognize(req);
    }

    /**
     * Sends local audio data with a pre-configured request, handling base64
     * encoding automatically. Data/DataLen are set from {@code rawData}.
     */
    public SentenceRecognitionResult recognizeDataWithOptions(byte[] rawData,
            SentenceRecognitionRequest req) throws ASRException {
        if (rawData == null || rawData.length == 0) {
            throw ASRException.invalidParam("audio data is empty");
        }
        if (rawData.length > MAX_AUDIO_SIZE) {
            throw ASRException.invalidParam("audio data exceeds 3MB limit");
        }
        req.setSourceType(SOURCE_TYPE_DATA);
        req.setData(Base64.getEncoder().encodeToString(rawData));
        req.setDataLen(rawData.length);
        return recognize(req);
    }

    private void validateRequest(SentenceRecognitionRequest req) throws ASRException {
        if (req == null) {
            throw ASRException.invalidParam("request is null");
        }
        if (req.getEngServiceType() == null || req.getEngServiceType().isEmpty()) {
            throw ASRException.invalidParam("EngServiceType is required");
        }
        if (req.getVoiceFormat() == null || req.getVoiceFormat().isEmpty()) {
            throw ASRException.invalidParam("VoiceFormat is required");
        }
        if (req.getSourceType() == SOURCE_TYPE_URL
                && (req.getUrl() == null || req.getUrl().isEmpty())) {
            throw ASRException.invalidParam("Url is required when SourceType=0");
        }
        if (req.getSourceType() == SOURCE_TYPE_DATA
                && (req.getData() == null || req.getData().isEmpty())) {
            throw ASRException.invalidParam("Data is required when SourceType=1");
        }
    }

    /**
     * JSON request body for sentence recognition. Field names follow the
     * server-side contract (including the quirky {@code EngSerViceType}
     * capitalization).
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class SentenceRecognitionRequest {
        /** Engine model type. Required. E.g. "16k_zh", "16k_zh_en". */
        @JsonProperty("EngSerViceType")
        private String engServiceType = "";

        /** Audio source: 0 = URL, 1 = base64 data in body. Always sent. */
        @JsonProperty("SourceType")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private int sourceType;

        /** Audio format: "wav", "pcm", "ogg-opus", "mp3", "m4a". Always sent. */
        @JsonProperty("VoiceFormat")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String voiceFormat = "";

        /** Audio file URL (required when sourceType = 0). ≤ 60s, ≤ 3MB. */
        @JsonProperty("Url")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String url = "";

        /** Base64-encoded audio data (required when sourceType = 1). */
        @JsonProperty("Data")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String data = "";

        /** Audio data length in bytes (required when sourceType = 1). */
        @JsonProperty("DataLen")
        private long dataLen;

        /** Word-level timing: 0 hide (default), 1 show, 2 with punctuation. */
        @JsonProperty("WordInfo")
        private int wordInfo;

        /** Profanity filter: 0 off (default), 1 filter, 2 replace with *. */
        @JsonProperty("FilterDirty")
        private int filterDirty;

        /** Modal particle filter: 0 off (default), 1 partial, 2 strict. */
        @JsonProperty("FilterModal")
        private int filterModal;

        /** Punctuation filter: 0 off (default), 2 filter all punctuation. */
        @JsonProperty("FilterPunc")
        private int filterPunc;

        /** Arabic numeral conversion: 0 off, 1 smart (default). */
        @JsonProperty("ConvertNumMode")
        private int convertNumMode;

        /** Hotword vocabulary ID from the console. */
        @JsonProperty("HotwordId")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String hotwordId = "";

        /** Temporary inline hotword list: "word1|weight1,word2|weight2". */
        @JsonProperty("HotwordList")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String hotwordList = "";

        /** Custom language model ID. */
        @JsonProperty("CustomizationId")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String customizationId = "";

        /** PCM input sample rate override. Only 8000 is supported. */
        @JsonProperty("InputSampleRate")
        private int inputSampleRate;

        /** Forces the audio language on supporting engines. Empty = auto. */
        @JsonProperty("Language")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String language = "";

        public String getEngServiceType() {
            return engServiceType;
        }

        public void setEngServiceType(String engServiceType) {
            this.engServiceType = engServiceType;
        }

        public int getSourceType() {
            return sourceType;
        }

        public void setSourceType(int sourceType) {
            this.sourceType = sourceType;
        }

        public String getVoiceFormat() {
            return voiceFormat;
        }

        public void setVoiceFormat(String voiceFormat) {
            this.voiceFormat = voiceFormat;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public long getDataLen() {
            return dataLen;
        }

        public void setDataLen(long dataLen) {
            this.dataLen = dataLen;
        }

        public int getWordInfo() {
            return wordInfo;
        }

        public void setWordInfo(int wordInfo) {
            this.wordInfo = wordInfo;
        }

        public int getFilterDirty() {
            return filterDirty;
        }

        public void setFilterDirty(int filterDirty) {
            this.filterDirty = filterDirty;
        }

        public int getFilterModal() {
            return filterModal;
        }

        public void setFilterModal(int filterModal) {
            this.filterModal = filterModal;
        }

        public int getFilterPunc() {
            return filterPunc;
        }

        public void setFilterPunc(int filterPunc) {
            this.filterPunc = filterPunc;
        }

        public int getConvertNumMode() {
            return convertNumMode;
        }

        public void setConvertNumMode(int convertNumMode) {
            this.convertNumMode = convertNumMode;
        }

        public String getHotwordId() {
            return hotwordId;
        }

        public void setHotwordId(String hotwordId) {
            this.hotwordId = hotwordId;
        }

        public String getHotwordList() {
            return hotwordList;
        }

        public void setHotwordList(String hotwordList) {
            this.hotwordList = hotwordList;
        }

        public String getCustomizationId() {
            return customizationId;
        }

        public void setCustomizationId(String customizationId) {
            this.customizationId = customizationId;
        }

        public int getInputSampleRate() {
            return inputSampleRate;
        }

        public void setInputSampleRate(int inputSampleRate) {
            this.inputSampleRate = inputSampleRate;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    /** Successful sentence recognition result. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SentenceRecognitionResult {
        /** Recognition text. */
        @JsonProperty("Result")
        private String result = "";

        /** Audio duration in milliseconds. */
        @JsonProperty("AudioDuration")
        private long audioDuration;

        /** Word count (0 when word info is not enabled). */
        @JsonProperty("WordSize")
        private int wordSize;

        /** Word-level timing details (empty when word info is not enabled). */
        @JsonProperty("WordList")
        private List<SentenceWord> wordList = new ArrayList<>();

        /** Unique request identifier. */
        @JsonProperty("RequestId")
        private String requestId = "";

        public String getResult() {
            return result;
        }

        public long getAudioDuration() {
            return audioDuration;
        }

        public int getWordSize() {
            return wordSize;
        }

        public List<SentenceWord> getWordList() {
            return wordList == null ? new ArrayList<>() : wordList;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    /** Word-level timing information for sentence recognition. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SentenceWord {
        @JsonProperty("Word")
        private String word = "";

        @JsonProperty("StartTime")
        private long startTime;

        @JsonProperty("EndTime")
        private long endTime;

        public String getWord() {
            return word;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }
    }
}
