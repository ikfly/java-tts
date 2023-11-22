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
                .synthesisText("测试文本，java 文本转语音")
//                .outputFileName("文件名保存测试")
                .voice(VoiceEnum.zh_CN_XiaoxiaoNeural)
                .usePlayer(true)
                .build();
        ts.sendText(ssml);
    }
}
