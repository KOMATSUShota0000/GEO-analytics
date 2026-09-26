package com.geo.analytics.application.service;

import com.geo.analytics.infrastructure.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** ログインコードのメールを組み立てて送る。文面はオーナー確定（#146, 2026-09-26）。 */
@Component
public class LoginCodeMailer {

    static final String SUBJECT = "GEOアナリティクスのログインコード";
    static final String SENDER_NAME = "GEOアナリティクス";
    // Why: 利用者は国内の制作会社・代理店。期限の時刻はサーバーの場所に関係なく日本時間で書く。
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final String mailFrom;

    public LoginCodeMailer(ObjectProvider<JavaMailSender> javaMailSenderProvider, AppProperties appProperties) {
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.mailFrom = appProperties.getNotifications().getMailFrom();
    }

    public void send(String to, String code, Instant expiresAt, Duration ttl) throws MessagingException {
        // Why: 送信の設定は起動時に必須にしているが、自動テストのプロファイルだけは外している。黙って飛ばさず失敗させる。
        JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("メール送信の設定がないため、ログインコードを送れません");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        try {
            helper.setFrom(mailFrom, SENDER_NAME);
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("送信元の表示名を設定できません", e);
        }
        helper.setTo(to);
        helper.setSubject(SUBJECT);
        helper.setText(body(code, expiresAt, ttl), false);
        sender.send(message);
    }

    static String body(String code, Instant expiresAt, Duration ttl) {
        String until = CLOCK.format(expiresAt.atZone(DISPLAY_ZONE));
        return """
                GEOアナリティクスのログインコードをお送りします。

                    %s

                ログイン画面にこの6桁の数字を入力してください。
                コードの有効期限は%d分です（%sまで）。

                このコードは他の人に教えないでください。
                このメールに心当たりがない場合は、何もせず削除してください。
                他の方がメールアドレスを打ち間違えた可能性があります。
                """.formatted(code, ttl.toMinutes(), until);
    }
}
