package com.tencent.trtcasr.asr;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.SignatureParams;
import com.tencent.trtcasr.common.SpeakerRole;

/**
 * Shared parameter validation for the recognizers.
 *
 * <p>The service validates every parameter as well, but rejecting an
 * obviously invalid value locally turns a remote 4001 ("参数不合法") into an
 * immediate, descriptive error and avoids burning a connection or a task
 * quota.
 */
public final class ParamsValidator {
    /** Server-side accepted noise threshold range. */
    public static final double MIN_NOISE_THRESHOLD = 0.0;
    public static final double MAX_NOISE_THRESHOLD = 4.0;

    private ParamsValidator() {
    }

    /**
     * Checks the diarization mode and its enrollment input. roles /
     * voiceprintIds are only meaningful with mode 3, but supplying them for
     * another mode is a caller mistake worth surfacing.
     */
    public static void validateSpeakerDiarization(int mode, int speakerNumber,
            List<SpeakerRole> roles, List<String> voiceprintIds) throws ASRException {
        switch (mode) {
            case SignatureParams.SPEAKER_DIARIZATION_OFF:
            case SignatureParams.SPEAKER_DIARIZATION_CLUSTER:
            case SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT:
                break;
            default:
                throw ASRException.invalidParam(
                        "SpeakerDiarization must be 0 (off), 1 (cluster) or 3 (voiceprint), got %d",
                        mode);
        }

        if (speakerNumber < 0) {
            throw ASRException.invalidParam(
                    "SpeakerNumber must be >= 0 (0 = auto detection), got %d", speakerNumber);
        }

        boolean hasRoles = roles != null && !roles.isEmpty();
        boolean hasIds = voiceprintIds != null && !voiceprintIds.isEmpty();
        if (mode != SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT && (hasRoles || hasIds)) {
            throw ASRException.invalidParam("SpeakerRoles/VoiceprintIds require SpeakerDiarization=3");
        }

        if (roles != null) {
            for (int i = 0; i < roles.size(); i++) {
                SpeakerRole role = roles.get(i);
                if (role.getRoleName() == null || role.getRoleName().isEmpty()) {
                    throw ASRException.invalidParam("SpeakerRoles[%d].RoleName is empty", i);
                }
                validateEnrollmentUrl(i, role.getAudioUrl());
            }
        }

        if (voiceprintIds != null) {
            for (int i = 0; i < voiceprintIds.size(); i++) {
                if (voiceprintIds.get(i) == null || voiceprintIds.get(i).isEmpty()) {
                    throw ASRException.invalidParam("VoiceprintIds[%d] is empty", i);
                }
            }
        }
    }

    /**
     * Requires an absolute http(s) URL for enrollment audio.
     *
     * <p>The URL is fetched by the ASR service, not by the SDK: this is a
     * customer-facing client library, so it only rejects inputs that can
     * never work (bad syntax, non-http scheme, missing host). Reachability
     * and network policies belong to the service-side allow list.
     */
    private static void validateEnrollmentUrl(int index, String rawUrl) throws ASRException {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw ASRException.invalidParam("SpeakerRoles[%d].AudioUrl is empty", index);
        }
        // Split scheme://authority explicitly to match Go's
        // url.ParseRequestURI semantics: "https:///a.wav" has an empty host
        // there (java.net.URI would also report a null host, but the explicit
        // split keeps the behavior obvious and matches the other SDK ports).
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw ASRException.invalidParam(
                    "SpeakerRoles[%d].AudioUrl is not a valid URL: %s", index, e.getMessage());
        }
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw ASRException.invalidParam(
                    "SpeakerRoles[%d].AudioUrl must use http or https, got \"%s\"", index, scheme);
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw ASRException.invalidParam("SpeakerRoles[%d].AudioUrl has no host", index);
        }
    }

    /** Checks the VAD profile and noise threshold. */
    public static void validateVadTuning(Integer vadLevel, Double noiseThreshold)
            throws ASRException {
        if (vadLevel != null && vadLevel != 0 && vadLevel != 1) {
            throw ASRException.invalidParam(
                    "VadLevel must be 0 (high recall) or 1 (far-field filtering), got %d", vadLevel);
        }
        if (noiseThreshold != null) {
            double v = noiseThreshold;
            // NaN fails every comparison, so test the valid range positively.
            if (!(v >= MIN_NOISE_THRESHOLD && v <= MAX_NOISE_THRESHOLD)) {
                throw ASRException.invalidParam(
                        "NoiseThreshold must be between %.1f and %.1f, got %s",
                        MIN_NOISE_THRESHOLD, MAX_NOISE_THRESHOLD, Double.toString(v));
            }
        }
    }

    /** Checks a small enumerated option such as input_sample_rate. */
    public static void validateEnumOption(String name, int value, int... allowed)
            throws ASRException {
        for (int candidate : allowed) {
            if (value == candidate) {
                return;
            }
        }
        throw ASRException.invalidParam("%s must be one of %s, got %d",
                name, java.util.Arrays.toString(allowed), value);
    }
}
