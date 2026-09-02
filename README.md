# TRTC-ASR Java SDK

基于 TRTC 鉴权体系的语音识别（ASR）Java SDK，与 [Go SDK](../trtc-asr-sdk-go) 行为对齐，支持实时语音识别（WebSocket）、一句话识别（HTTP）和录音文件识别（异步 HTTP）三种模式。

## 前提条件

1. **获取腾讯云 APPID** — 在 [CAM API 密钥管理](https://console.cloud.tencent.com/cam/capi) 页面查看
2. **创建 TRTC 应用** — 在 [实时音视频控制台](https://console.cloud.tencent.com/trtc/app) 创建应用，获取 `SDKAppID`
3. **获取 SDK 密钥** — 在应用概览页点击「SDK密钥」查看，即用于计算 UserSig 的加密密钥

协议细节（WebSocket 参数、响应字段、说话人分离）与 Go SDK 完全一致，参见 [Go SDK README](../trtc-asr-sdk-go/README.md)。

## 安装

要求 **JDK 17+**（使用内置的 `java.net.http.HttpClient` / `WebSocket`，无需第三方 WebSocket 库；JSON 用 Jackson）。

```xml
<dependency>
    <groupId>com.tencent</groupId>
    <artifactId>trtc-asr-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

本地构建安装：

```bash
mvn install
```

## 快速开始

### 实时语音识别

```java
import com.tencent.trtcasr.asr.*;
import com.tencent.trtcasr.common.*;

class Printer implements SpeechRecognitionListener {
    @Override
    public void onSentenceEnd(SpeechRecognitionResponse r) {
        System.out.println("[end] " + r.getResult().getVoiceTextStr());
        for (var seg : r.getResult().getSpeakerSegments()) {
            String name = seg.getSpeakerName().isEmpty()
                    ? "spk" + seg.getSpeakerId() : seg.getSpeakerName();
            System.out.println("  [" + name + "] " + seg.getText());
        }
    }

    @Override
    public void onFail(SpeechRecognitionResponse r, ASRException e) {
        System.err.println("[fail] " + e.getMessage());
    }
}

Credential credential = new Credential(appId, sdkAppId, "your-sdk-secret-key");
// credential.setSite(Credential.SITE_INTL); // 国际站；不调用则走国内站
SpeechRecognizer recognizer = new SpeechRecognizer(credential, "16k_zh", new Printer());

// 可选配置（全部在 start 前调用）：
// recognizer.setHotwordList("词1|5,词2|11");
// recognizer.setSpeakerDiarization(SignatureParams.SPEAKER_DIARIZATION_CLUSTER);
// recognizer.setWordInfo(1);
// recognizer.setNoiseThreshold(1.5);   // VAD 噪声微调（0.0-4.0）

recognizer.start();                    // 连接 WebSocket
recognizer.write(pcmChunk);            // 发送音频（PCM）
recognizer.stop();                     // 发送结束信号并等待最终结果
```

### 一句话识别

```java
SentenceRecognizer recognizer = new SentenceRecognizer(credential);
var result = recognizer.recognizeData(pcmBytes, "pcm", "16k_zh_en");
System.out.println("识别结果: " + result.getResult() + " (" + result.getAudioDuration() + " ms)");
// 或从 URL：recognizer.recognizeUrl("https://example.com/a.wav", "wav", "16k_zh_en");
```

### 录音文件识别

```java
FileRecognizer recognizer = new FileRecognizer(credential);
String taskId = recognizer.createTaskFromData(pcmBytes, "pcm", "16k_zh_en");
var status = recognizer.waitForResult(taskId);   // 默认 1s 轮询，10min 超时
System.out.println("识别结果: " + status.getResult());
// 或从 URL（≤1GB / ≤12h）：recognizer.createTaskFromUrl("https://example.com/a.wav", "16k_zh_en");
```

## 设计说明

- **错误模型**：所有 API 抛出 `ASRException`，`getCode()` 与 Go SDK 错误码一致（1001-1010，服务端错误码原样透传）。
- **生命周期**：`SpeechRecognizer` 单例使用——stopped 后不可重启；回调在 SDK 内部单线程执行器上顺序派发；`stop()` 可在回调中安全调用（通过 `ThreadLocal` 标记检测回调重入，非终态回调里发送 end 后立即返回，看门狗线程兜底超时强关）。
- **三态参数**：`vadLevel` / `noiseThreshold` / `filterEmptyResult` 用 `Integer`/`Double`（null = 不配置）区分「显式传 0」与「不配置」。
- **UserSig**：SDK 内置 TLS sig API v2 兼容实现（`javax.crypto` HMAC-SHA256 + `java.util.zip` zlib + 腾讯 base64url 变体）。
- **HTTP/WS 传输**：全部使用 JDK 内置 `java.net.http`，无额外传输层依赖。

## 测试

```bash
mvn test
```

69 个测试全部在本机回环 mock 服务器上运行（无需真实凭证/网络；HTTP mock 用 JDK 内置 `com.sun.net.httpserver`，WebSocket mock 为测试内置的最小 RFC 6455 服务器）：

- `UserSigTest` — UserSig 结构/HMAC/zlib/base64url 往返
- `SignatureParamsTest` — URL query 构建（说话人分离、VAD 三态、转义、排序）
- `ParamsValidatorTest` — 参数校验
- `SentenceRecognizerTest` / `FileRecognizerTest` — mock HTTP：请求断言、错误路径、轮询、超时
- `SpeechRecognizerTest` — mock WebSocket：握手鉴权参数、ack 帧不误派、终态状态机、回调重入 stop、监听器异常恢复、并发写+stop 不死锁

## 示例

```bash
export TRTC_ASR_APP_ID=13xxxxxxxx
export TRTC_ASR_SDK_APP_ID=14xxxxxxxx
export TRTC_ASR_SECRET_KEY=your-sdk-secret-key

mvn -q compile
java -cp target/classes com.tencent.trtcasr.examples.RealtimeAsrExample path/to/audio.pcm
java -cp target/classes com.tencent.trtcasr.examples.SentenceAsrExample path/to/audio.pcm pcm 16k_zh_en
java -cp target/classes com.tencent.trtcasr.examples.FileAsrExample path/to/audio.pcm
```

## License

MIT License
