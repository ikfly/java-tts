package io.iifly.model;

import io.iifly.constant.OutputFormat;
import io.iifly.util.Tools;

import java.io.Serializable;
import java.util.Optional;

/**
 * @author zh-hq
 * @date 2023/3/30
 */
public class SpeechConfig implements Serializable {

    public static final String CONFIG_PATTERN = "X-Timestamp:%s\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n" +
            "\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"%s\"}}}}";

    private OutputFormat outputFormat;

    private SpeechConfig(OutputFormat outputFormat) {
        this.outputFormat = Optional
                .ofNullable(outputFormat)
                .orElse(OutputFormat.audio_24khz_48kbitrate_mono_mp3);
    }

    public static SpeechConfig of(OutputFormat outputFormat) {
        return new SpeechConfig(outputFormat);
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }


    /*
    X-Timestamp:Thu Jun 16 2022 19:13:55 GMT+0800 (中国标准时间)
    Content-Type:application/json; charset=utf-8
    Path:speech.config

    {"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"true"},"outputFormat":"webm-24khz-16bit-mono-opus"}}}}
    */
    @Override
    public String toString() {
        return String.format(CONFIG_PATTERN, Tools.date(), outputFormat.getValue());
    }
}
