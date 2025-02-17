package io.ikfly.service;

import io.ikfly.constant.OutputFormat;
import io.ikfly.constant.TtsConstants;
import io.ikfly.exceptions.TtsException;
import io.ikfly.model.SSML;
import io.ikfly.model.SpeechConfig;
import io.ikfly.player.MyPlayer;
import io.ikfly.util.Tools;
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

    public String getBaseSavePath() {
        return baseSavePath;
    }

    public void setBaseSavePath(String baseSavePath) {
        this.baseSavePath = baseSavePath;
    }

    public TTSService(){}
    public TTSService(String baseSavePath) {
        this.baseSavePath = baseSavePath;
    }

    /**
     * 正使用的音频输出格式
     */
    private volatile OutputFormat outputFormat;
    /**
     * 本次
     */
    private volatile String outputFileName;
    /**
     * 合成语音后播放
     */
    private volatile boolean usePlayer;

    //================================

    /**
     * 正在进行合成...
     */
    private volatile boolean synthesising;

    private volatile boolean isClose = false;

    /**
     * 正在进行合成的文本
     */
    private volatile String currentText;

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
            log.debug("onClosed:{} - {}", code, reason);
            TTSService.this.ws = null;
            synthesising = false;
            isClose = true;
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            super.onClosing(webSocket, code, reason);
            log.debug("onClosing:{} - {}", code, reason);
            TTSService.this.ws = null;
            synthesising = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            log.debug("onFailure:{} - {}", t.getMessage(), response,t);
            TTSService.this.ws = null;
            synthesising = false;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
//            log.debug("onMessage text\r\n:{}", text);
            if (text.contains(TtsConstants.TURN_START)) {
                // （新的）音频流开始传输开始，清空重置buffer
                audioBuffer.clear();
            } else if (text.contains(TtsConstants.TURN_END)) {
                // 音频流结束，写为文件
                if(outputFileName == null || "".equals(outputFileName)){
                    outputFileName = (currentText.length() < 6 ? currentText : currentText.substring(0, 5)).replaceAll("[</|*。?\" >\\\\]","") + Tools.localDateTime();
                }
                String absolutePath = writeAudio(outputFormat, audioBuffer.readByteString(), outputFileName);
                if (usePlayer) {
                    try {
                        MyPlayer.getInstance(absolutePath).play(absolutePath);
                    } catch (IOException | UnsupportedAudioFileException e) {
                        log.error(absolutePath + ":音频播放失败," + e.getMessage(), e);
                    }
                }
                synthesising = false;
                usePlayer = false;
                outputFileName = null;
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
            super.onMessage(webSocket, bytes);
//            log.debug("onMessage bytes\r\n:{}", bytes.utf8());
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
    /**
     * 发送合成请求
     *
     * @param ssml
     */
    public void sendText(SSML ssml) {
        if (isClose) throw TtsException.of("ws 已关闭！");
        while (synthesising) {
            log.info("空转等待上一个语音合成");
            Tools.sleep(1);
        }
        synthesising = true;
        if (Objects.nonNull(ssml.getOutputFormat()) && !ssml.getOutputFormat().equals(outputFormat)) {
            sendConfig(ssml.getOutputFormat());
        }
        log.debug("ssml:{}", ssml);
        if (!getOrCreateWs().send(ssml.toString())) {
            throw TtsException.of("语音合成请求发送失败...");
        }
        currentText = ssml.getSynthesisText();
        usePlayer = ssml.getUsePlayer();
        outputFileName = ssml.getOutputFileName();
    }

    public void close(){
        while (synthesising) {
            log.info("空转等待语音合成...");
            Tools.sleep(1);
        }
        if (Objects.nonNull(ws)) {
            ws.close(1000, "bye");
            log.info("ws closing...");
        }
        if(Objects.nonNull(okHttpClient)){
            okHttpClient.dispatcher().executorService().shutdown();   //清除并关闭线程池
            okHttpClient.connectionPool().evictAll();                 //清除并关闭连接池
        }
        while (!isClose){
            // waiting close...
        };
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

        String url = TtsConstants.EDGE_SPEECH_WSS +
                "?Retry-After=200&TrustedClientToken=" + TtsConstants.TRUSTED_CLIENT_TOKEN +
                "&ConnectionId=" + Tools.getRandomId()
                +"&Sec-MS-GEC=" + Tools.generateSecMsGecToken(TtsConstants.TRUSTED_CLIENT_TOKEN)
                +"&Sec-MS-GEC-Version=" + TtsConstants.SEC_MS_GEC_VERSION;
        String origin = TtsConstants.EDGE_SPEECH_ORIGIN;

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
        log.debug("audio config:{}", speechConfig);
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
}
