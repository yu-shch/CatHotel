// 声明当前类所在的包路径
package com.clover.cathotel.controller;

// 导入前台在线预约表单接收模型类
import com.clover.cathotel.model.ReservationForm;
// 导入 Jakarta Validation 校验触发注解
import jakarta.validation.Valid;
// 导入 Spring 响应体封装实体，用于 Restful API 统一响应
import org.springframework.http.ResponseEntity;
// 导入 Spring JDBC 数据库操作核心模板
import org.springframework.jdbc.core.JdbcTemplate;
// 声明此类为 Spring MVC 控制器组件
import org.springframework.stereotype.Controller;
// 导入 Spring MVC Model 视图模型容器
import org.springframework.ui.Model;
// 导入表单绑定及字段数据校验结果集接口
import org.springframework.validation.BindingResult;
// 导入 HTTP GET 请求路由映射注解
import org.springframework.web.bind.annotation.GetMapping;
// 导入 HTTP POST 请求路由映射注解
import org.springframework.web.bind.annotation.PostMapping;
// 导入请求 URL 查询参数与表单字段绑定注解
import org.springframework.web.bind.annotation.RequestParam;
// 导入跨重定向闪存属性传递接口
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 导入 ArrayList 动态数组列表
import java.util.ArrayList;
// 导入 HashMap 哈希映射集合
import java.util.HashMap;
// 导入 List 集合接口
// 导入 Map 映射集合接口
import java.util.Map;

// 导入邮件发送业务服务类
import com.clover.cathotel.service.MailService;

/**
 * 官网前台控制器
 * - 官网主页渲染与客房展示
 * - 客户在线提交预订申请、表单验证与邮件发送
 * - 预订完成确认页
 * - 前端日历可用余房查询接口
 */
@Controller
public class HomeController {

    // 声明数据库操作模板对象（final 修饰，保证不可篡改）
    private final JdbcTemplate jdbcTemplate;
    // 声明邮件发送服务对象（final 修饰，保证不可篡改）
    private final MailService mailService;

    // 控制器构造函数：通过 Spring 构造器依赖注入（DI）初始化 JdbcTemplate 和 MailService
    public HomeController(JdbcTemplate jdbcTemplate, MailService mailService) {
        // 将注入的 JdbcTemplate 实例赋值给类成员变量
        this.jdbcTemplate = jdbcTemplate;
        // 将注入的 MailService 实例赋值给类成员变量
        this.mailService = mailService;
    }

    // 定义全局静态常量：客房物理容量配置映射（每种房型每日最大承接 3 间）
    private static final Map<String, Integer> ROOM_CAPACITY = Map.of(
            // 标准房最大容量配置为 3 间
            "スタンダードルーム", 3,
            // 阳光奢华房最大容量配置为 3 间
            "サンシャイン・ラグジュアリールーム", 3,
            // 双猫家庭房最大容量配置为 3 间
            "ファミリー2匹ルーム", 3
    );

    // 映射根路径 GET 请求（官网主页）
    @GetMapping("/")
    // 首页渲染方法，传入 Model 视图容器
    public String home(Model model) {
        // 检查 Model 中是否已存在 reservationForm 属性，防止覆盖提交校验失败回显的数据
        if (!model.containsAttribute("reservationForm")) {
            // 若不存在则压入一个新的空表单对象，供前端 Thymeleaf 标签安全绑定
            model.addAttribute("reservationForm", new ReservationForm());
        }
        // 渲染并返回首页模板 index.html
        return "index";
    }

    // 映射 /reserve POST 请求（前台客户提交预约表单）
    @PostMapping("/reserve")
    // 处理客户预订提交的主逻辑方法
    public String reserve(
            // 接收并自动触发表单实体的 JSR 基础注解校验
            @Valid ReservationForm reservationForm,
            // 接收校验结果错误集（必须紧随 @Valid 表单参数之后）
            BindingResult bindingResult,
            // 重定向跨请求传参容器
            RedirectAttributes redirectAttributes
    ) {
        // 校验：入住日期与离店日期均非空且非空白字符
        if (reservationForm.getCheckInDate() != null
                && reservationForm.getCheckOutDate() != null
                && !reservationForm.getCheckInDate().isBlank()
                && !reservationForm.getCheckOutDate().isBlank()
                // 校验：入住日期如果在离店日期之后或当天（非法时间区间）
                && reservationForm.getCheckInDate().compareTo(reservationForm.getCheckOutDate()) >= 0) {

            // 向 checkOutDate 字段绑定自定义错误提示：离店日期必须在入住日期之后
            bindingResult.rejectValue("checkOutDate", "invalid", "チェックアウト日はチェックイン日より後の日付を選択してください。");
        }

        // 判断表单字段是否存在任何校验错误
        if (bindingResult.hasErrors()) {
            // 注入 Flash 提示：请确认输入内容
            redirectAttributes.addFlashAttribute("saveError", "入力内容をご確認ください。");
            // 将用户填写的原始表单压入 Flash 属性以回显数据
            redirectAttributes.addFlashAttribute("reservationForm", reservationForm);
            // 将表单的校验错误集绑定至 Flash 作用域供页面展示红色错误提示
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.reservationForm",
                    bindingResult
            );
            // 重定向回首页的预订锚点区域
            return "redirect:/#booking";
        }

        // 定义查询房态冲突数量的 SQL（经典区间重叠算法：已订入住 < 申请离店 且 已订离店 > 申请入住）
        String countSql = """
                SELECT COUNT(*)
                FROM reservations
                WHERE room_type = ?
                  AND check_in_date < ?
                  AND check_out_date > ?
                """;

        // 执行聚合查询，统计目标区间内该房型已被占用的房间数
        Integer reservedCount = jdbcTemplate.queryForObject(
                // 传入 SQL 语句
                countSql,
                // 指定统计结果映射类型为 Integer
                Integer.class,
                // 占位符 1：目标客房房型
                reservationForm.getRoomType(),
                // 占位符 2：申请离店日期
                reservationForm.getCheckOutDate(),
                // 占位符 3：申请入住日期
                reservationForm.getCheckInDate()
        );

        // 获取当前房型的最大物理承载量（未配置则默认 0）
        int capacity = ROOM_CAPACITY.getOrDefault(reservationForm.getRoomType(), 0);

        // 校验库存：若查询失败或已占用数达到/超过容量上限（已满房）
        if (reservedCount == null || reservedCount >= capacity) {
            // 注入 Flash 错误信息：该日程下所选客房已满员，请选择其他房型
            redirectAttributes.addFlashAttribute("saveError", "この日程では選択したお部屋は満室です。別のお部屋をお選びください。");
            // 回显已填写的表单数据
            redirectAttributes.addFlashAttribute("reservationForm", reservationForm);
            // 重定向返回首页预订区域
            return "redirect:/#booking";
        }

        // 生成全局唯一的预约订单流水编号（RES-时间戳毫秒数）
        String reservationCode = "RES-" + System.currentTimeMillis();

        // 定义向 reservations 数据表插入订单记录的 SQL 语句
        String insertSql = """
                INSERT INTO reservations
                (reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        // 将订单数据持久化至数据库，加入异常捕获
        try {
            // 执行 SQL 插入并绑定全部表单字段参数
            jdbcTemplate.update(
                    insertSql,
                    // 绑定预订流水号
                    reservationCode,
                    // 绑定宠主姓名
                    reservationForm.getOwnerName(),
                    // 绑定联系电话
                    reservationForm.getPhone(),
                    // 绑定电子邮箱
                    reservationForm.getEmail(),
                    // 绑定猫咪名字
                    reservationForm.getCatName(),
                    // 绑定入住日期
                    reservationForm.getCheckInDate(),
                    // 绑定退房日期
                    reservationForm.getCheckOutDate(),
                    // 绑定预订房型
                    reservationForm.getRoomType(),
                    // 绑定备注事项
                    reservationForm.getNote()
            );
            // 捕获可能产生的数据库写入异常
        } catch (Exception e) {
            // 存入保存失败的提示信息
            redirectAttributes.addFlashAttribute("saveError", "予約の保存に失敗しました。時間をおいて再度お試しください。");
            // 保留表单输入
            redirectAttributes.addFlashAttribute("reservationForm", reservationForm);
            // 重定向回预订页面
            return "redirect:/#booking";
        }

        // 发送确认邮件通知（采用独立 try-catch，确保邮件发送异常不会阻断预约成功流程）
        try {
            // 控制台打印邮件发送起始标记
            System.out.println("=== mail send start ===");

            // 向客户发送预订完成确认邮件
            mailService.sendReservationCompleteMailToCustomer(reservationForm, reservationCode);
            // 向管理员发送新预约提醒邮件
            mailService.sendReservationNoticeMailToAdmin(reservationForm, reservationCode);

            // 控制台打印邮件发送结束标记
            System.out.println("=== mail send end ===");
            // 捕获邮件发送异常
        } catch (Exception e) {
            // 控制台打印邮件异常标记
            System.out.println("=== mail send error ===");
            // 打印错误堆栈方便排查 SMTP 协议或网络故障
            e.printStackTrace();

            // 提示用户预约已成功，但确认信发送失败
            redirectAttributes.addFlashAttribute("mailWarning", "予約は完了しましたが、確認メールの送信に失敗しました。");
        }

        // 存入 Flash 属性：预约流水号（供完成页展示）
        redirectAttributes.addFlashAttribute("reservationCode", reservationCode);
        // 存入 Flash 属性：宠主姓名
        redirectAttributes.addFlashAttribute("completedOwnerName", reservationForm.getOwnerName());
        // 存入 Flash 属性：电子邮箱
        redirectAttributes.addFlashAttribute("completedEmail", reservationForm.getEmail());
        // 存入 Flash 属性：猫咪名字
        redirectAttributes.addFlashAttribute("completedCatName", reservationForm.getCatName());
        // 存入 Flash 属性：入住日期
        redirectAttributes.addFlashAttribute("completedCheckInDate", reservationForm.getCheckInDate());
        // 存入 Flash 属性：退房日期
        redirectAttributes.addFlashAttribute("completedCheckOutDate", reservationForm.getCheckOutDate());
        // 存入 Flash 属性：所选房型
        redirectAttributes.addFlashAttribute("completedRoomType", reservationForm.getRoomType());

        // 重定向跳转至预约完成结果页（防止用户刷新重复提交）
        return "redirect:/reservation/complete";
    }

    // 映射 GET /reservation/complete（预约完成结果提示页）
    @GetMapping("/reservation/complete")
    // 展示预约成功确认页面的方法
    public String reservationComplete() {
        // 渲染并返回 reservation-complete.html 模板页面
        return "reservation-complete";
    }

    // 映射 GET /api/available-rooms（供前端日历动态异步查询余房库存）
    @GetMapping("/api/available-rooms")
    // 余房查询 API 接口主方法
    public ResponseEntity<?> getAvailableRooms(
            // 接收查询入住日期参数
            @RequestParam String checkInDate,
            // 接收查询离店日期参数
            @RequestParam String checkOutDate
    ) {
        // 校验输入参数非空及非纯空白字符
        if (checkInDate == null || checkInDate.isBlank()
                || checkOutDate == null || checkOutDate.isBlank()) {
            // 校验失败返回 HTTP 400 Bad Request
            return ResponseEntity.badRequest().body("チェックイン日とチェックアウト日を入力してください。");
        }

        // 校验日期逻辑：入住日期必须早于退房日期
        if (checkInDate.compareTo(checkOutDate) >= 0) {
            // 逻辑不符合返回 HTTP 400 Bad Request
            return ResponseEntity.badRequest().body("チェックアウト日はチェックイン日より後の日付を選択してください。");
        }

        // 聚合分组统计各房型在目标日期区间内的重叠被预约间数
        String sql = """
                SELECT room_type, COUNT(*) AS reserved_count
                FROM reservations
                WHERE check_in_date < ?
                  AND check_out_date > ?
                GROUP BY room_type
                """;

        // 创建临时映射表保存各房型已占用的数量
        Map<String, Integer> reservedCountMap = new HashMap<>();
        // 执行查询并利用回调提取每个房型分组的占用计数
        jdbcTemplate.query(sql, rs -> {
            // 将房型名称和对应的占用计数存入 Map
            reservedCountMap.put(
                    rs.getString("room_type"),
                    rs.getInt("reserved_count")
            );
        }, checkOutDate, checkInDate);

        // 声明并实例化装载可用房型数据的动态列表
        ArrayList< Map< String, Object > > result = new ArrayList<>();

        // 遍历物理房型容量全局配置常量
        for (Map.Entry<String, Integer> entry : ROOM_CAPACITY.entrySet()) {
            // 获取房型名称
            String roomType = entry.getKey();
            // 获取该房型总承载容量上限
            int capacity = entry.getValue();
            // 从统计结果中获取该房型的已占用数量（未被预订则为 0）
            int reserved = reservedCountMap.getOrDefault(roomType, 0);
            // 计算当前剩余可预订的空房数量（总容量 - 已占用）
            int remaining = capacity - reserved;

            // 若仍有剩余可用空房
            if (remaining > 0) {
                // 构建单个房型的 JSON 数据对象
                Map<String, Object> room = new HashMap<>();
                // 存入房型名
                room.put("roomType", roomType);
                // 存入剩余房间数
                room.put("remaining", remaining);
                // 将该房型对象添加至返回列表中
                result.add(room);
            }
        }

        // 响应 HTTP 200 OK 并将结果集合序列化为 JSON 数组返回前端
        return ResponseEntity.ok(result);
    }
}