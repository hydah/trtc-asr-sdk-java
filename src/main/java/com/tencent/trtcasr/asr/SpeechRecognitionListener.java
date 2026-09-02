package com.tencent.trtcasr.asr;

import com.tencent.trtcasr.common.ASRException;

/**
 * Callback interface for speech recognition events. All methods are default
 * no-ops so callers can implement only the events they care about.
 *
 * <p>Callbacks are delivered sequentially on an SDK-owned thread. A callback
 * that throws does not crash the SDK: the session is terminated and the
 * failure is surfaced via {@link #onFail}.
 */
public interface SpeechRecognitionListener {
    /** Called when the recognition session starts successfully. */
    default void onRecognitionStart(SpeechRecognitionResponse response) {
    }

    /** Called when a new sentence begins. */
    default void onSentenceBegin(SpeechRecognitionResponse response) {
    }

    /** Called when intermediate recognition results are available. */
    default void onRecognitionResultChange(SpeechRecognitionResponse response) {
    }

    /** Called when a sentence ends with the final result. */
    default void onSentenceEnd(SpeechRecognitionResponse response) {
    }

    /** Called when the entire recognition session completes. */
    default void onRecognitionComplete(SpeechRecognitionResponse response) {
    }

    /** Called when an error occurs during recognition. */
    default void onFail(SpeechRecognitionResponse response, ASRException error) {
    }
}
