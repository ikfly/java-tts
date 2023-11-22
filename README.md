# Java TTS
参考其他语言版本写的Java 版文字转语音，使用 EdgeApi 或者 Azure Api

## Usage

Dependency:
```xml

<dependency>
    <groupId>io.github.iifly</groupId>
    <artifactId>java-tts</artifactId>
    <version>1.0.2</version>
</dependency>

```

Example:
```java
public class App {
    public static void main(String[] args) {
        TTSService ts = new TTSService();
//        ts.setBaseSavePath("d:\\"); // 设置保存路径
        SSML ssml = SSML.builder()
                .outputFormat(OutputFormat.audio_24khz_48kbitrate_mono_mp3)
                .synthesisText("测试文本，java 文本转语音")
//                .outputFileName("文件名保存测试")
                .voice(VoiceEnum.zh_CN_XiaoxiaoNeural)
                .usePlayer(true)
                .build();
        ts.sendText(ssml);
    }
}
```

## Thanks
- [https://github.com/ag2s20150909/TTS](https://github.com/ag2s20150909/TTS)
- [https://github.com/rany2/edge-tts](https://github.com/rany2/edge-tts)
- [https://github.com/Migushthe2nd/MsEdgeTTS](https://github.com/Migushthe2nd/MsEdgeTTS)
- [https://learn.microsoft.com/zh-cn/azure/cognitive-services/speech-service/index-text-to-speech](https://learn.microsoft.com/zh-cn/azure/cognitive-services/speech-service/index-text-to-speech)
