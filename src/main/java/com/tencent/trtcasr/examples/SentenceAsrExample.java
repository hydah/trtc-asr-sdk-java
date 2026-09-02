package com.tencent.trtcasr.examples;

import java.nio.file.Files;
import java.nio.file.Path;

import com.tencent.trtcasr.asr.SentenceRecognizer;
import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.Credential;

/**
 * One-shot sentence recognition example (audio <= 60s / 3MB).
 *
 * <p>Credentials come from environment variables: TRTC_APP_ID,
 * TRTC_SDK_APP_ID, TRTC_SECRET_KEY.
 *
 * <p>Usage: SentenceAsrExample &lt;audio.pcm&gt; [format=pcm]
 * [engine=16k_zh_en]
 */
public class SentenceAsrExample {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: SentenceAsrExample <audio-file> [format=pcm] [engine=16k_zh_en]");
            System.exit(1);
        }
        String path = args[0];
        String format = args.length > 1 ? args[1] : "pcm";
        String engine = args.length > 2 ? args[2] : "16k_zh_en";

        Credential credential = new Credential(
                Long.parseLong(env("TRTC_APP_ID")),
                Long.parseLong(env("TRTC_SDK_APP_ID")),
                env("TRTC_SECRET_KEY"));

        byte[] data = Files.readAllBytes(Path.of(path));
        SentenceRecognizer recognizer = new SentenceRecognizer(credential);
        try {
            var result = recognizer.recognizeData(data, format, engine);
            System.out.println("识别结果: " + result.getResult());
            System.out.println("音频时长: " + result.getAudioDuration() + " ms");
        } catch (ASRException e) {
            System.err.println("识别失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null) {
            System.err.println("missing env var: " + name);
            System.exit(1);
        }
        return v;
    }
}
