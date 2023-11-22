package io.ikfly;

import io.ikfly.constant.OutputFormat;
import io.ikfly.constant.VoiceEnum;
import io.ikfly.model.SSML;
import io.ikfly.service.TTSService;

/**
 * @author zh-hq
 * @date 2023/3/23
 */
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
