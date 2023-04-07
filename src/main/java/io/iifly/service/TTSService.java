package io.iifly.service;

import io.iifly.constant.OutputFormat;
import io.iifly.constant.TtsConstants;
import io.iifly.exceptions.TtsException;
import io.iifly.model.SSML;
import io.iifly.model.SpeechConfig;
import io.iifly.player.MyPlayer;
import io.iifly.util.Tools;
import okhttp3.*;
import okio.Buffer;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author zh-hq
 * @date 2023/3/27
 */

public class TTSService {

    public static final Logger log = LoggerFactory.getLogger(TTSService.class);

    /**
     * 保存音频文件的目录 默认工作目录
     */
    private String baseSavePath;

    /**
     * 正使用的音频输出格式
     */
    private OutputFormat outputFormat;

    /**
     * 合成语音后播放
     */
    private boolean usePlayer;

    /**
     * 是否正使用 Edge Api
     */
    private boolean usingAzureApi;


    //================================

    /**
     * 正在进行合成...
     */
    private volatile boolean synthesising;

    /**
     * 正在进行合成的文本
     */
    private String currentText;

    /**
     * 当前的音频流数据
     */
    private final Buffer audioBuffer = new Buffer();
    private OkHttpClient okHttpClient;
    private WebSocket ws;

    protected WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            log.debug("onClosed:" + reason);
            TTSService.this.ws = null;
            synthesising = false;
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            super.onClosing(webSocket, code, reason);
            log.debug("onClosing:" + reason);
            TTSService.this.ws = null;
            synthesising = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            log.debug("onFailure" + t.getMessage(), t);
            TTSService.this.ws = null;
            synthesising = false;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            log.debug("onMessage text\r\n:{}", text);
            if (text.contains(TtsConstants.TURN_START)) {
                // （新的）音频流开始传输开始，清空重置buffer
                audioBuffer.clear();
            } else if (text.contains(TtsConstants.TURN_END)) {
                // 音频流结束，写为文件
                String fileName = (currentText.length() < 6 ? currentText : currentText.substring(0, 5)).replaceAll("[</|*。?\" >\\\\]","") + Tools.localDateTime();
                String absolutePath = writeAudio(outputFormat, audioBuffer.readByteString(), fileName);
                if (usePlayer) {
                    try {
                        MyPlayer.getInstance(absolutePath).play(absolutePath);
                    } catch (IOException | UnsupportedAudioFileException e) {
                        log.error(absolutePath + ":音频播放失败," + e.getMessage(), e);
                    }
                }
                synthesising = false;
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
            super.onMessage(webSocket, bytes);
            log.debug("onMessage bytes\r\n:{}", bytes.utf8());
            int audioIndex = bytes.lastIndexOf(TtsConstants.AUDIO_START.getBytes(StandardCharsets.UTF_8)) + TtsConstants.AUDIO_START.length();
            boolean audioContentType = bytes.lastIndexOf(TtsConstants.AUDIO_CONTENT_TYPE.getBytes(StandardCharsets.UTF_8)) + TtsConstants.AUDIO_CONTENT_TYPE.length() != -1;
            if (audioIndex != -1 && audioContentType) {
                try {
                    audioBuffer.write(bytes.substring(audioIndex));
                } catch (Exception e) {
                    log.error("onMessage Error," + e.getMessage(), e);
                }
            }
        }
    };

    private TTSService(String baseSavePath, OutputFormat outputFormat, boolean usePlayer, boolean usingAzureApi) {
        this.baseSavePath = baseSavePath;
        this.outputFormat = outputFormat;
        this.usePlayer = usePlayer;
        this.usingAzureApi = usingAzureApi;
    }

    public static TTSServiceBuilder builder(){
        return new TTSServiceBuilder();
    }
    /**
     * 发送合成请求
     *
     * @param ssml
     */
    public void sendText(SSML ssml) {
        while (synthesising) {
            log.info("空转等待上一个语音合成");
            Tools.sleep(1);
        }
        synthesising = true;
        if (Objects.nonNull(ssml.getStyle()) && !usingAzureApi) {
            // voice style 仅使用 AzureApi 时可用
            ssml.setStyle(null);
        }
        if (Objects.nonNull(ssml.getOutputFormat()) && !outputFormat.equals(ssml.getOutputFormat())) {
            sendConfig(ssml.getOutputFormat());
        }
        log.info("ssml:{}", ssml);
        if (!getOrCreateWs().send(ssml.toString())) {
            throw TtsException.of("语音合成请求发送失败...");
        }
        currentText = ssml.getSynthesisText();
    }

    /**
     * 获取或创建 ws 连接
     *
     * @return
     */
    private synchronized WebSocket getOrCreateWs() {
        if (Objects.nonNull(ws)) {
            return ws;
        }
        String url;
        String origin;
        if (usingAzureApi) {
            url = TtsConstants.AZURE_SPEECH_WSS + "?Retry-After=200&TrafficType=AzureDemo&Authorization=bearer undefined&X-ConnectionId=" + Tools.getRandomId();
            origin = TtsConstants.AZURE_SPEECH_ORIGIN;
        } else {
            url = TtsConstants.EDGE_SPEECH_WSS + "?Retry-After=200&TrustedClientToken=" + TtsConstants.TRUSTED_CLIENT_TOKEN + "&ConnectionId=" + Tools.getRandomId();
            origin = TtsConstants.EDGE_SPEECH_ORIGIN;
        }
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", TtsConstants.UA)
                .addHeader("Origin", origin)
                .build();
        ws = getOkHttpClient().newWebSocket(request, webSocketListener);
        sendConfig(outputFormat);
        return ws;
    }

    private OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .pingInterval(20, TimeUnit.SECONDS) // 设置 PING 帧发送间隔
                    .build();
        }
        return okHttpClient;
    }


    /**
     * 发送下次音频输出配置
     *
     * @param outputFormat
     * @return
     */
    private void sendConfig(OutputFormat outputFormat) {
        SpeechConfig speechConfig = SpeechConfig.of(outputFormat);
        log.info("audio config:{}", speechConfig);
        if (!getOrCreateWs().send(speechConfig.toString())) {
            throw TtsException.of("语音输出格式配置失败...");
        }
        this.outputFormat = speechConfig.getOutputFormat();
    }


    /**
     * 写出音频
     *
     * @param format   音频输出格式
     * @param data     字节流
     * @param fileName 文件名
     * @return
     */
    private String writeAudio(OutputFormat format, ByteString data, String fileName) {
        try {
            byte[] audioBuffer = data.toByteArray();
            String[] split = format.getValue().split("-");
            String suffix = split[split.length - 1];
            // write  file
            String outputFileName = Optional.ofNullable(baseSavePath).orElse("") + fileName + "." + suffix;
            File outputAudioFile = new File(outputFileName);
            if (outputAudioFile.exists()) {
                outputAudioFile.delete();
            }
            FileOutputStream fstream = new FileOutputStream(outputAudioFile);
            fstream.write(audioBuffer);
            fstream.flush();
            fstream.close();
            return outputAudioFile.getAbsolutePath();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw TtsException.of("音频文件写出异常，" + e.getMessage());
        }
    }

    public static class TTSServiceBuilder{
        /**
         * 保存音频文件的目录 默认工作目录
         */
        private String baseSavePath;
        /**
         * 正使用的音频输出格式
         */
        private OutputFormat outputFormat;
        /**
         * 合成语音后播放
         */
        private boolean usePlayer;
        /**
         * 是否正使用 Edge Api
         */
        private boolean usingAzureApi;


        public TTSServiceBuilder baseSavePath(String baseSavePath) {
            this.baseSavePath = baseSavePath;
            return this;
        }

        public TTSServiceBuilder usingOutputFormat(OutputFormat usingOutputFormat) {
            this.outputFormat = usingOutputFormat;
            return this;
        }

        public TTSServiceBuilder usePlayer(boolean usePlayer) {
            this.usePlayer = usePlayer;
            return this;
        }

        public TTSServiceBuilder usingAzureApi(boolean usingAzureApi) {
            this.usingAzureApi = usingAzureApi;
            return this;
        }

        public TTSService build(){
            return new TTSService(baseSavePath, outputFormat, usePlayer, usingAzureApi);
        }
    }

}
