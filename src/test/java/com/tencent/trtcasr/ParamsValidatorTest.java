package com.tencent.trtcasr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tencent.trtcasr.asr.ParamsValidator;
import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.SignatureParams;
import com.tencent.trtcasr.common.SpeakerRole;

/** Parameter validation tests, ported from the Go SDK's params_test.go. */
class ParamsValidatorTest {
    private static SpeakerRole validRole() {
        return new SpeakerRole("teacher", "https://example.com/a.wav");
    }

    @Test
    void diarizationValidCases() {
        assertDoesNotThrow(() -> ParamsValidator.validateSpeakerDiarization(
                SignatureParams.SPEAKER_DIARIZATION_OFF, 0, null, null));
        assertDoesNotThrow(() -> ParamsValidator.validateSpeakerDiarization(
                SignatureParams.SPEAKER_DIARIZATION_CLUSTER, 0, null, null));
        assertDoesNotThrow(() -> ParamsValidator.validateSpeakerDiarization(
                SignatureParams.SPEAKER_DIARIZATION_CLUSTER, 2, null, null));
        assertDoesNotThrow(() -> ParamsValidator.validateSpeakerDiarization(
                SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 2,
                List.of(validRole()), List.of("vp-1")));
    }

    @Test
    void diarizationInvalidCases() {
        record Case(int mode, int number, List<SpeakerRole> roles, List<String> ids,
                String want) {
        }
        List<Case> cases = List.of(
                new Case(2, 0, null, null, "SpeakerDiarization must be 0"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_CLUSTER, -1, null, null,
                        "SpeakerNumber must be >= 0"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_CLUSTER, 0, List.of(validRole()),
                        null, "require SpeakerDiarization=3"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_OFF, 0, null, List.of("vp-1"),
                        "require SpeakerDiarization=3"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 0,
                        List.of(new SpeakerRole("", "https://example.com/a.wav")), null,
                        "RoleName is empty"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 0,
                        List.of(new SpeakerRole("teacher", "")), null, "AudioUrl is empty"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 0,
                        List.of(new SpeakerRole("teacher", "file:///etc/passwd")), null,
                        "must use http or https"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 0,
                        List.of(new SpeakerRole("teacher", "https:///a.wav")), null,
                        "has no host"),
                new Case(SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 0, null,
                        List.of(""), "VoiceprintIds[0] is empty"));

        for (Case c : cases) {
            ASRException err = assertThrows(ASRException.class,
                    () -> ParamsValidator.validateSpeakerDiarization(
                            c.mode(), c.number(), c.roles(), c.ids()),
                    "case: " + c.want());
            assertTrue(err.getMessage().contains(c.want()),
                    "error should contain " + c.want() + ": " + err.getMessage());
        }
    }

    @Test
    void diarizationAllowsInternalHost() {
        // This SDK is customer-facing: internal hosts belong to the caller's
        // own network and stay fetchable for the service.
        assertDoesNotThrow(() -> ParamsValidator.validateSpeakerDiarization(
                SignatureParams.SPEAKER_DIARIZATION_VOICEPRINT, 0,
                List.of(new SpeakerRole("teacher", "http://192.168.1.10/a.wav")), null));
    }

    @Test
    void vadTuningValidCases() {
        assertDoesNotThrow(() -> ParamsValidator.validateVadTuning(null, null));
        assertDoesNotThrow(() -> ParamsValidator.validateVadTuning(0, null));
        assertDoesNotThrow(() -> ParamsValidator.validateVadTuning(1, null));
        assertDoesNotThrow(() -> ParamsValidator.validateVadTuning(null, 0.0));
        assertDoesNotThrow(() -> ParamsValidator.validateVadTuning(null, 4.0));
    }

    @Test
    void vadTuningInvalidCases() {
        ASRException err = assertThrows(ASRException.class,
                () -> ParamsValidator.validateVadTuning(2, null));
        assertTrue(err.getMessage().contains("VadLevel must be 0"));

        for (double bad : new double[]{-0.5, 4.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            ASRException e = assertThrows(ASRException.class,
                    () -> ParamsValidator.validateVadTuning(null, bad), "value " + bad);
            assertTrue(e.getMessage().contains("NoiseThreshold must be between"),
                    e.getMessage());
        }
    }

    @Test
    void enumOptionValidation() {
        assertDoesNotThrow(() -> ParamsValidator.validateEnumOption("InputSampleRate", 0, 0, 8000));
        assertDoesNotThrow(() -> ParamsValidator.validateEnumOption("InputSampleRate", 8000, 0, 8000));
        ASRException err = assertThrows(ASRException.class,
                () -> ParamsValidator.validateEnumOption("InputSampleRate", 16000, 0, 8000));
        assertTrue(err.getMessage().contains("InputSampleRate must be one of"));
    }
}
