package io.ikfly.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author zh-hq
 */
public class Tools {

    public static final Pattern NO_VOICE_PATTERN = Pattern.compile("[\\s\\p{C}\\p{P}\\p{Z}\\p{S}]");
    public static final String SDF = "EEE MMM dd yyyy HH:mm:ss 'GMT'Z";
    public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public static Logger log = LoggerFactory.getLogger(Tools.class);
    private static OkHttpClient client = new OkHttpClient();

    public static String httpGet(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();
            log.info("response.toString():{}", response.toString());
            log.info("response.isSuccessful():{}", response.isSuccessful());
            if (response.isSuccessful()) {
                String body = response.body().string();
                // log.info("response.body:{}", body);
                return body;
            }
            throw new RuntimeException(String.format("request：%s fail, message:%s", url, response.code()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isNoVoice(CharSequence charSequence) {
        return NO_VOICE_PATTERN.matcher(charSequence).replaceAll("").isEmpty();
    }

    public static void sleep(int second) {
        try {
            Thread.sleep(second * 1000);
        } catch (InterruptedException ignore) {

        }
    }

    /**
     * 获取时间戳
     *
     * @return String time
     */
    public static String date() {
        return new SimpleDateFormat(SDF).format(new Date());
    }

    public static String localDateTime() {
        return LocalDateTime.now().format(DTF);
    }


    public static String localeToEmoji(Locale locale) {
        String countryCode = locale.getCountry();
        if ("TW".equals(countryCode) && Locale.getDefault().getCountry().equals("CN")) {
            return "";
        }
        int firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6;
        int secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6;
        return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
    }

    public static String getRandomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateSecMsGecToken(String TRUSTED_CLIENT_TOKEN) {
        // 获取当前时间的 Windows 文件时间格式（自 1601-01-01 起的 100ns 间隔）
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        long ticks = (now.toInstant().getEpochSecond() + 11644473600L) * 10_000_000L;
        ticks -= ticks % 3_000_000_000L; // 四舍五入到最近的 5 分钟

        // 创建要哈希的字符串
        String strToHash = ticks + TRUSTED_CLIENT_TOKEN;

        // 计算 SHA256 哈希
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(strToHash.getBytes(StandardCharsets.US_ASCII));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
