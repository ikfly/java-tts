# Java TTS
参考其他语言版本写的Java 版文字转语音，使用 EdgeApi

## Usage

Dependency:
```xml

<dependency>
    <groupId>io.github.ikfly</groupId>
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
                .synthesisText("自定义文件名测试文本，java 文本转语音")
                .outputFileName("自定义文件名")
                .voice(VoiceEnum.zh_CN_XiaoxiaoNeural)
                .build();
        ts.sendText(ssml);

        ts.sendText(SSML.builder()
                .synthesisText("文件名自动生成测试文本")
                .usePlayer(true) // 语音播放
                .build());

        ts.close();
    }
}
```

## Thanks
- [https://github.com/ag2s20150909/TTS](https://github.com/ag2s20150909/TTS)
- [https://github.com/rany2/edge-tts](https://github.com/rany2/edge-tts)
- [https://github.com/Migushthe2nd/MsEdgeTTS](https://github.com/Migushthe2nd/MsEdgeTTS)
- [https://learn.microsoft.com/zh-cn/azure/cognitive-services/speech-service/index-text-to-speech](https://learn.microsoft.com/zh-cn/azure/cognitive-services/speech-service/index-text-to-speech)
