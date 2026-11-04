package com.clover.cathotel.service;

import com.clover.cathotel.model.ReservationForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * メールの送信を担当するサービス
 * 负责发送邮件的服务类
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;

    // 送信元メールアドレスを properties から取得
    // 从 properties 中获取发件人邮箱
    @Value("${spring.mail.username:}")
    private String fromEmail;

    // 管理者のメールアドレスを properties から取得
    // 从 properties 中获取管理员邮箱
    @Value("${app.admin-email:}")
    private String adminEmail;

    // Render 线上部署兜底用的 Google Apps Script URL
    @Value("${google.script.url:${GOOGLE_SCRIPT_URL:}}")
    private String googleScriptUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * お客さまへ予約完了メールを送信する
     * 向客户发送预约完成邮件
     */
    public void sendReservationCompleteMailToCustomer(
            ReservationForm form,
            String reservationCode
    ) {
        // ログ出力 (デバッグ用)
        // 日志输出 (用于调试)
        System.out.println("MAIL FROM = " + fromEmail);
        System.out.println("CUSTOMER TO = " + form.getEmail());

        String subject = "【クローバー猫ホテル】ご予約を受け付けました";
        String content = """
                %s 様

                クローバー猫ホテルへのご予約ありがとうございます。
                以下の内容でご予約を受け付けました。

                予約番号：%s
                飼い主さまのお名前：%s
                猫ちゃんのお名前：%s
                チェックイン日：%s
                チェックアウト日：%s
                お部屋：%s

                ご予約内容の確認・取り消しには、予約番号とメールアドレスをご利用ください。

                クローバー猫ホテル
                """.formatted(
                form.getOwnerName(),
                reservationCode,
                form.getOwnerName(),
                form.getCatName(),
                form.getCheckInDate(),
                form.getCheckOutDate(),
                form.getRoomType()
        );

        sendWithFallback(form.getEmail(), subject, content);
    }

    /**
     * 管理者へ予約通知メールを送信する
     * 向管理员发送预约通知邮件
     */
    public void sendReservationNoticeMailToAdmin(
            ReservationForm form,
            String reservationCode
    ) {
        if (!StringUtils.hasText(adminEmail)) {
            return;
        }

        String subject = "【クローバー猫ホテル】新しい予約が入りました";
        String content = """
                新しい予約が入りました。

                予約番号：%s
                飼い主さまのお名前：%s
                電話番号：%s
                メールアドレス：%s
                猫ちゃんのお名前：%s
                チェックイン日：%s
                チェックアウト日：%s
                お部屋：%s
                備考：
                %s
                """.formatted(
                reservationCode,
                form.getOwnerName(),
                form.getPhone(),
                form.getEmail(),
                form.getCatName(),
                form.getCheckInDate(),
                form.getCheckOutDate(),
                form.getRoomType(),
                form.getNote()
        );

        sendWithFallback(adminEmail, subject, content);
    }

    // =========================================================================
    // 核心发送逻辑：本地原版 SMTP 优先，失败自动转 Google 脚本兜底
    // =========================================================================
    private void sendWithFallback(String toEmail, String subject, String body) {
        boolean sent = false;

        // 1. 云端优先：配置了 Google 脚本时直接走 HTTP 发信，跳过被封锁的 587 端口
        if (StringUtils.hasText(googleScriptUrl)) {
            sendViaGoogleScript(toEmail, subject, body);
            return;
        }

        // 2. 本地开发（未配置 Google 脚本时）：走原生 Gmail SMTP
        if (mailSender != null && StringUtils.hasText(fromEmail)) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(body);

                mailSender.send(message);
                sent = true;
                System.out.println("=== 原生 SMTP 送信成功 ===");
            } catch (Exception e) {
                System.err.println("=== 原生 SMTP 失败（Render 拦截或连接异常）: " + e.getMessage() + " ===");
            }
        }

        // 2. 线上兜底：在 Render 线上或未配置本地 SMTP 时，走 Google Apps Script
        if (!sent && StringUtils.hasText(googleScriptUrl)) {
            sendViaGoogleScript(toEmail, subject, body);
        }
    }

    // =========================================================================
    // Google Apps Script HTTP 发信接口
    // =========================================================================
    private void sendViaGoogleScript(String toEmail, String subject, String textBody) {
        try {
            String jsonPayload = String.format(
                    "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}",
                    toEmail,
                    escapeJson(subject),
                    escapeJson(textBody)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(googleScriptUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 302) {
                System.out.println("=== Google 脚本中继送信成功 ===");
            } else {
                System.err.println("=== Google 脚本返回异常状态码: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("=== Google 脚本发信异常 ===");
            e.printStackTrace();
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}