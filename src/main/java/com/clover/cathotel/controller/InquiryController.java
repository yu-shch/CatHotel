// 声明当前类所在的包路径
package com.clover.cathotel.controller;

// 导入预订数据实体模型类
import com.clover.cathotel.model.Reservation;
// 导入 Spring JDBC 数据库操作核心模板
import org.springframework.jdbc.core.JdbcTemplate;
// 声明此类为 Spring MVC 控制器组件
import org.springframework.stereotype.Controller;
// 导入 Spring MVC Model 视图模型容器
import org.springframework.ui.Model;
// 导入 HTTP GET 请求路由映射注解
import org.springframework.web.bind.annotation.GetMapping;
// 导入 HTTP POST 请求路由映射注解
import org.springframework.web.bind.annotation.PostMapping;
// 导入请求 URL 查询参数与表单字段绑定注解
import org.springframework.web.bind.annotation.RequestParam;
// 导入 Java 8 本地日期处理类 LocalDate
import java.time.LocalDate;

// 导入 List 集合接口
import java.util.List;

/**
 * 顾客预约照会控制器
 * - 顾客凭“预约单号 + 邮箱 + 宠主姓名”核验并查询本人历史订单（/inquiry）
 * - 顾客在规定时效内（入住日之前）自主取消预约（/inquiry/delete）
 */
@Controller
public class InquiryController {

    // 声明数据库操作模板对象（final 修饰，保证不可篡改）
    private final JdbcTemplate jdbcTemplate;

    // 控制器构造函数：通过 Spring 构造器依赖注入（DI）初始化 JdbcTemplate
    public InquiryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 映射 GET /inquiry（顾客自查订单页面入口）
    @GetMapping("/inquiry")
    // 渲染查询页，支持接收 URL 中的预填参数并回显
    public String inquiryPage(
            // 接收 URL 传递的预约单号（非必填）
            @RequestParam(required = false) String reservationCode,
            // 接收 URL 传递的邮箱（非必填）
            @RequestParam(required = false) String email,
            // 接收 URL 传递的宠主姓名（非必填）
            @RequestParam(required = false) String ownerName,
            // 视图数据模型容器
            Model model
    ) {
        // 若传入了预约单号，将其回显至 Model 中
        if (reservationCode != null) {
            model.addAttribute("reservationCode", reservationCode);
        }
        // 若传入了电子邮箱，将其回显至 Model 中
        if (email != null) {
            model.addAttribute("email", email);
        }
        // 若传入了宠主姓名，将其回显至 Model 中
        if (ownerName != null) {
            model.addAttribute("ownerName", ownerName);
        }
        // 渲染并返回 inquiry.html 查询页
        return "inquiry";
    }

    // 映射 POST /inquiry（执行三要素预约查询）
    @PostMapping("/inquiry")
    // 处理客户自主订单查询的核心逻辑
    public String inquirySearch(
            // 必填参数：预约单号
            @RequestParam String reservationCode,
            // 必填参数：电子邮箱
            @RequestParam String email,
            // 必填参数：宠主姓名
            @RequestParam String ownerName,
            // 视图数据模型
            Model model
    ) {
        // 编写三要素核验 SQL：校验单号、邮箱与姓名是否同时完全匹配
        String verifySql = """
                SELECT COUNT(*)
                FROM reservations
                WHERE reservation_code = ?
                  AND email = ?
                  AND owner_name = ?
                """;

        // 执行核验 SQL，统计匹配的记录数
        Integer matched = jdbcTemplate.queryForObject(
                verifySql,
                Integer.class,
                reservationCode,
                email,
                ownerName
        );

        // 如果未检索到匹配的记录（防止暴力撞库及越权查看）
        if (matched == null || matched == 0) {
            // 向视图模型注入未找到匹配预约的错误提示
            model.addAttribute("inquiryError", "入力した予約番号・メールアドレス・お名前に一致する予約が見つかりませんでした。");
            // 保留已输入的预约单号回显
            model.addAttribute("reservationCode", reservationCode);
            // 保留已输入的邮箱回显
            model.addAttribute("email", email);
            // 保留已输入的姓名回显
            model.addAttribute("ownerName", ownerName);
            // 退回查询表单页面
            return "inquiry";
        }

        // 核验通过后，根据邮箱查询该顾客名下的所有历史预约记录（按创建时间倒序）
        String sql = """
                SELECT id, reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text, created_at
                FROM reservations
                WHERE email = ?
                ORDER BY created_at DESC
                """;

        // 执行查询并将结果集映射为 Reservation 实体对象列表
        List<Reservation> reservations = jdbcTemplate.query(sql, (rs, rowNum) -> {
            // 实例化预约实体对象
            Reservation reservation = new Reservation();
            // 映射主键 ID
            reservation.setId(rs.getLong("id"));
            // 映射订单流水编号
            reservation.setReservationCode(rs.getString("reservation_code"));
            // 映射宠主姓名
            reservation.setOwnerName(rs.getString("owner_name"));
            // 映射联系电话
            reservation.setPhone(rs.getString("phone"));
            // 映射电子邮箱
            reservation.setEmail(rs.getString("email"));
            // 映射猫咪昵称
            reservation.setCatName(rs.getString("cat_name"));
            // 映射入住日期并转为 LocalDate
            reservation.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
            // 映射退房日期并转为 LocalDate
            reservation.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
            // 映射预订房型
            reservation.setRoomType(rs.getString("room_type"));
            // 映射备注信息
            reservation.setNoteText(rs.getString("note_text"));
            // 检查创建时间戳是否非空
            if (rs.getTimestamp("created_at") != null) {
                // 将创建时间戳转换为 LocalDateTime 并赋值
                reservation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            // 返回映射完毕的实体对象
            return reservation;
        }, email);

        // 向视图模型绑定本次核验成功的预约单号
        model.addAttribute("searchedReservationCode", reservationCode);
        // 向视图模型绑定本次核验成功的邮箱
        model.addAttribute("searchedEmail", email);
        // 向视图模型绑定本次核验成功的姓名
        model.addAttribute("searchedOwnerName", ownerName);
        // 向视图模型绑定该邮箱名下的全部预约记录列表
        model.addAttribute("reservations", reservations);

        // 渲染并返回 inquiry-result.html 结果列表页
        return "inquiry-result";
    }

    // 映射 POST /inquiry/delete（顾客在自查页自主撤销预约）
    @PostMapping("/inquiry/delete")
    // 客户退订的主逻辑方法
    public String deleteReservationByCustomer(
            // 必填参数：订单主键 ID
            @RequestParam Long id,
            // 必填参数：预约单号
            @RequestParam String reservationCode,
            // 必填参数：预留电子邮箱
            @RequestParam String email,
            // 必填参数：宠主姓名
            @RequestParam String ownerName,
            // 视图模型
            Model model
    ) {
        // 四要素严格防越权校验 SQL：联合验证 ID、单号、邮箱、姓名
        String verifySql = """
                SELECT check_in_date
                FROM reservations
                WHERE id = ?
                AND reservation_code = ?
                AND email = ?
                AND owner_name = ?
                """;

        // 查询符合条件的入住日期列表
        List<LocalDate> checkInDates = jdbcTemplate.query(
                verifySql,
                (rs, rowNum) -> rs.getDate("check_in_date").toLocalDate(),
                id,
                reservationCode,
                email,
                ownerName
        );

        // 如果四要素比对失败（可能试图非法越权删除他人订单）
        if (checkInDates.isEmpty()) {
            // 提示输入信息不一致，无法执行删除
            model.addAttribute("inquiryError", "入力情報が一致しないため、予約を削除できませんでした。");
            // 保留单号回显
            model.addAttribute("reservationCode", reservationCode);
            // 保留邮箱回显
            model.addAttribute("email", email);
            // 保留姓名回显
            model.addAttribute("ownerName", ownerName);
            // 退回查询页面
            return "inquiry";
        }

        // 获取匹配到的订单入住日期
        LocalDate checkInDate = checkInDates.get(0);

        // 业务限制：入住日是今天或今天以前的订单，顾客无权自主撤销
        if (!checkInDate.isAfter(LocalDate.now())) {
            // 提示时效超期不可退订
            model.addAttribute("inquiryError", "チェックイン日が今日以前の予約は削除できません。");
            // 回显查询凭证信息
            model.addAttribute("searchedReservationCode", reservationCode);
            model.addAttribute("searchedEmail", email);
            model.addAttribute("searchedOwnerName", ownerName);

            // 重新查询该邮箱名下的全部预约记录用于重新渲染
            String sql = """
                    SELECT id, reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text, created_at
                    FROM reservations
                    WHERE email = ?
                    ORDER BY created_at DESC
                    """;

            // 执行列表检索与实体封装
            List<Reservation> reservations = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Reservation reservation = new Reservation();
                reservation.setId(rs.getLong("id"));
                reservation.setReservationCode(rs.getString("reservation_code"));
                reservation.setOwnerName(rs.getString("owner_name"));
                reservation.setPhone(rs.getString("phone"));
                reservation.setEmail(rs.getString("email"));
                reservation.setCatName(rs.getString("cat_name"));
                reservation.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
                reservation.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
                reservation.setRoomType(rs.getString("room_type"));
                reservation.setNoteText(rs.getString("note_text"));
                if (rs.getTimestamp("created_at") != null) {
                    reservation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                return reservation;
            }, email);

            // 将预约数据回显至视图
            model.addAttribute("reservations", reservations);

            // 停留在查询结果页
            return "inquiry-result";
        }

        // 定义四要素联合锁定的删除 SQL（防止并发越权）
        String deleteSql = """
                DELETE FROM reservations
                WHERE id = ?
                AND email = ?
                AND reservation_code = ?
                AND owner_name = ?
                """;

        // 执行物理删除操作
        jdbcTemplate.update(deleteSql, id, email, reservationCode, ownerName);

        // 删除成功后，再次查出该邮箱名下剩余的有效预约列表
        String sql = """
                SELECT id, reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text, created_at
                FROM reservations
                WHERE email = ?
                ORDER BY created_at DESC
                """;

        // 重新查询剩余订单列表
        List<Reservation> reservations = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Reservation reservation = new Reservation();
            reservation.setId(rs.getLong("id"));
            reservation.setReservationCode(rs.getString("reservation_code"));
            reservation.setOwnerName(rs.getString("owner_name"));
            reservation.setPhone(rs.getString("phone"));
            reservation.setEmail(rs.getString("email"));
            reservation.setCatName(rs.getString("cat_name"));
            reservation.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
            reservation.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
            reservation.setRoomType(rs.getString("room_type"));
            reservation.setNoteText(rs.getString("note_text"));
            if (rs.getTimestamp("created_at") != null) {
                reservation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            return reservation;
        }, email);

        // 注入成功取消预约的提示消息
        model.addAttribute("inquirySuccess", "予約を取り消しました。");
        // 回显搜索的预约单号
        model.addAttribute("searchedReservationCode", reservationCode);
        // 回显搜索的电子邮箱
        model.addAttribute("searchedEmail", email);
        // 回显搜索的宠主姓名
        model.addAttribute("searchedOwnerName", ownerName);
        // 绑定最新的剩余预约列表
        model.addAttribute("reservations", reservations);

        // 重新渲染查询结果列表页
        return "inquiry-result";
    }
}