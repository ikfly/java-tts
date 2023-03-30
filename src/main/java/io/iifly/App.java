package io.iifly;

import io.iifly.constant.OutputFormat;
import io.iifly.constant.TtsStyleEnum;
import io.iifly.constant.VoiceEnum;
import io.iifly.model.SSML;
import io.iifly.service.TTSService;

/**
 * @author zh-hq
 * @date 2023/3/23
 */
public class App {
    public static void main(String[] args) {
        TTSService ts = TTSService.builder()
                // .baseSavePath("d:\\") // 音频保存基础路径
                .usePlayer(true) // 合成之后播放试听
                .usingAzureApi(true) // 使用 azure api 功能更多点
                // .usingOutputFormat(OutputFormat.audio_24khz_48kbitrate_mono_mp3) // 音频输出格式，默认或使用 mp3的,其他的不太清楚怎么解码
                .build();
        SSML ssml = SSML.builder()
                .synthesisText("测试文本，java 文本转语音")
                .voice(VoiceEnum.zh_CN_XiaoxiaoNeural)
                .style(TtsStyleEnum.chat)
                .build();
        ts.sendText(ssml);
    }
}
