package com.tencent.trtcasr.examples;

import java.nio.file.Files;
import java.nio.file.Path;

import com.tencent.trtcasr.asr.FileRecognizer;
import com.tencent.trtcasr.common.ASRException;
import com.tencent.trtcasr.common.Credential;

/**
 * Async file recognition example (long audio, up to 12h).
 *
 * <p>Credentials come from environment variables: TRTC_APP_ID,
 * TRTC_SDK_APP_ID, TRTC_SECRET_KEY.
 *
 * <p>Usage: FileAsrExample &lt;audio.pcm&gt; | FileAsrExample -u &lt;https-url&gt;
 */
public class FileAsrExample {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: FileAsrExample <audio-file> | -u <url>");
            System.exit(1);
        }

        Credential credential = new Credential(
                Long.parseLong(env("TRTC_APP_ID")),
                Long.parseLong(env("TRTC_SDK_APP_ID")),
                env("TRTC_SECRET_KEY"));
        FileRecognizer recognizer = new FileRecognizer(credential);

        try {
            String taskId;
            if (args[0].equals("-u")) {
                if (args.length < 2) {
                    System.err.println("missing url after -u");
                    System.exit(1);
                }
                taskId = recognizer.createTaskFromUrl(args[1], "16k_zh_en");
            } else {
                byte[] data = Files.readAllBytes(Path.of(args[0]));
                taskId = recognizer.createTaskFromData(data, "pcm", "16k_zh_en");
            }
            System.out.println("task submitted: " + taskId);

            var status = recognizer.waitForResult(taskId);
            System.out.println("result: " + status.getResult());
            System.out.println("audio duration: " + status.getAudioDuration() + " s");
            for (var d : status.getResultDetail()) {
                String speaker;
                if (!d.getSpeakerRoleName().isEmpty()) {
                    speaker = d.getSpeakerRoleName();
                } else if (d.getChannelId() > 0) {
                    speaker = "ch" + d.getChannelId();
                } else {
                    speaker = "spk" + d.getSpeakerId();
                }
                System.out.println("  [" + speaker + "] (" + d.getStartMs() + "-"
                        + d.getEndMs() + "ms) " + d.getFinalSentence());
            }
        } catch (ASRException e) {
            System.err.println("recognition failed: " + e.getMessage());
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
