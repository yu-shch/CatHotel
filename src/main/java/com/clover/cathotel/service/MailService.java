// Cloverブランドのパッケージに変更
// 更改为 Clover 品牌的包名
package com.clover.cathotel.service;

import com.clover.cathotel.model.ReservationForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

        // 送信元が設定されていない場合は中断
        // 如果未设置发件人地址，则中断
        if (!StringUtils.hasText(fromEmail)) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(form.getEmail());
        message.setSubject("【クローバー猫ホテル】ご予約を受け付けました");

        message.setText("""
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
        ));

        mailSender.send(message);
    }

    /**
     * 管理者へ予約通知メールを送信する
     * 向管理员发送预约通知邮件
     */
    public void sendReservationNoticeMailToAdmin(
            ReservationForm form,
            String reservationCode
    ) {
        if (!StringUtils.hasText(fromEmail) || !StringUtils.hasText(adminEmail)) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(adminEmail);
        message.setSubject("【クローバー猫ホテル】新しい予約が入りました");

        message.setText("""
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
        ));

        mailSender.send(message);
    }
}