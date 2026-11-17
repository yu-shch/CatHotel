// 声明当前类所在的包路径
package com.clover.cathotel.controller;

// 导入管理员登录表单实体模型类
import com.clover.cathotel.model.AdminLoginForm;
// 导入预订数据实体模型类
import com.clover.cathotel.model.Reservation;
// 导入邮件发送业务服务类
import com.clover.cathotel.service.MailService;
// 导入 HTTP Cookie 操作类
import jakarta.servlet.http.Cookie;
// 导入 HTTP Servlet 响应对象，用于向客户端写回 Cookie
import jakarta.servlet.http.HttpServletResponse;
// 导入 HTTP 会话管理接口，用于维护服务端登录凭证
import jakarta.servlet.http.HttpSession;
// 导入 Jakarta Validation 校验触发注解
import jakarta.validation.Valid;
// 导入 Spring 响应体封装实体，用于 Restful API 统一响应
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseBody;
// 导入跨重定向闪存属性传递接口
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 导入 Java 8 本地日期处理类 LocalDate
import java.time.LocalDate;
// 导入 List 集合接口
import java.util.List;
// 导入 Map 映射集合接口
import java.util.Map;

/**
 * 【后台管理员认证与预约管理控制器】
 * 负责管理员后台登录鉴权、Cookie 记住用户名、安全注销、
 * 后台预约总看板数据列表展示、超时弹窗异步重认证，以及管理员后台编辑与删除预约。
 */
@Controller
public class AdminAuthController {

    // 声明数据库操作模板对象（final 修饰，保证不可篡改）
    private final JdbcTemplate jdbcTemplate;
    // 声明邮件发送服务对象
    private final MailService mailService;

    // 控制器构造函数：通过 Spring 构造器依赖注入（DI）初始化 JdbcTemplate 和 MailService
    public AdminAuthController(JdbcTemplate jdbcTemplate, MailService mailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailService = mailService;
    }

    // 映射 GET /admin/login（管理员登录页面入口）
    @GetMapping("/admin/login")
    // 管理员登录页展示方法
    public String adminLogin(
            // 从 Cookie 中读取已记住的管理员用户名（非必填）
            @org.springframework.web.bind.annotation.CookieValue(value = "remembered_admin_username", required = false) String rememberedUsername,
            // 视图数据模型
            Model model
    ) {
        // 判断当前 Model 中是否已包含登录表单对象
        if (!model.containsAttribute("adminLoginForm")) {
            // 初始化一个空的管理员登录表单实体
            AdminLoginForm form = new AdminLoginForm();
            // 如果存在保存的用户名 Cookie，自动填入并勾选复选框
            if (rememberedUsername != null && !rememberedUsername.isBlank()) {
                // 将 Cookie 中的用户名写入表单实体用于前端回显
                form.setUsername(rememberedUsername);
                // 向视图传递布尔标记，令前端“记住用户名”复选框默认处于勾选状态
                model.addAttribute("rememberUsernameChecked", true);
            }
            // 将表单对象添加至视图模型
            model.addAttribute("adminLoginForm", form);
        }
        // 渲染并返回 login.html 模板页面
        return "login";
    }

    // 映射 POST /admin/login（处理管理员登录提交）
    @PostMapping("/admin/login")
    // 执行管理员认证主逻辑方法
    public String doAdminLogin(
            // 接收并验证管理员提交的登录表单
            @Valid AdminLoginForm adminLoginForm,
            // 接收字段格式校验结果
            BindingResult bindingResult,
            // 接收是否勾选“记住用户名”选项，默认 false
            @RequestParam(value = "rememberUsername", defaultValue = "false") boolean rememberUsername,
            // HTTP Servlet 响应对象，用于向客户端写回 Cookie
            HttpServletResponse response,
            // 跨重定向消息载体
            RedirectAttributes redirectAttributes,
            // HTTP 会话对象，用于保存服务端已登录状态
            HttpSession session
    ) {
        // 判断表单字段是否校验失败
        if (bindingResult.hasErrors()) {
            // Flash 传递已填写的登录表单用于回显
            redirectAttributes.addFlashAttribute("adminLoginForm", adminLoginForm);
            // Flash 传递表单校验错误详情
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.adminLoginForm",
                    bindingResult
            );
            // Flash 传递统一错误提示消息
            redirectAttributes.addFlashAttribute("loginError", "入力内容をご確認ください。");
            // 重定向刷新登录页面
            return "redirect:/admin/login";
        }

        // 1. 定义按用户名查询密码的 SQL
        String sql = "SELECT password FROM admins WHERE username = ?";
        // 根据用户名查询管理员表获取对应的密码
        List<String> passwords = jdbcTemplate.query(
                sql,
                // 将查询结果集单列映射为密码字符串
                (rs, rowNum) -> rs.getString("password"),
                // 绑定用户名参数
                adminLoginForm.getUsername()
        );

        // 如果用户不存在，或者数据库密码与输入的密码不匹配
        if (passwords.isEmpty() || !passwords.get(0).equals(adminLoginForm.getPassword())) {
            // 回显表单对象
            redirectAttributes.addFlashAttribute("adminLoginForm", adminLoginForm);
            // 提示用户名或密码错误
            redirectAttributes.addFlashAttribute("loginError", "ユーザー名またはパスワードが正しくありません。");
            // 重定向刷新登录页面
            return "redirect:/admin/login";
        }

        // 2. 写入或清除 Cookie
        if (rememberUsername) {
            // 勾选记住我：实例化名为 remembered_admin_username 的 Cookie 并存入用户名
            Cookie cookie = new Cookie("remembered_admin_username", adminLoginForm.getUsername());
            // 设置过期时间为 30 天（秒数换算）
            cookie.setMaxAge(30 * 24 * 60 * 60);
            // 设置 Cookie 作用域路径为全站根路径
            cookie.setPath("/");
            // 开启 HttpOnly 禁止前端 JavaScript 脚本读取，增强安全性
            cookie.setHttpOnly(true);
            // 将 Cookie 写入客户端响应头
            response.addCookie(cookie);
        } else {
            // 未勾选记住我：生成同名空 Cookie
            Cookie cookie = new Cookie("remembered_admin_username", "");
            // 将存活时间设为 0，指示浏览器立即清除该 Cookie
            cookie.setMaxAge(0);
            // 匹配作用路径
            cookie.setPath("/");
            // 写回响应头以注销 Cookie
            response.addCookie(cookie);
        }

        // 在服务端 Session 中写入已登录标记：adminLoggedIn = true
        session.setAttribute("adminLoggedIn", true);
        // 认证成功，重定向至后台预约管理看板
        return "redirect:/admin/reservations";
    }

    // 映射 POST /admin/logout（处理管理员登出请求）
    @PostMapping("/admin/logout")
    // 管理员安全注销方法
    public String adminLogout(HttpSession session) {
        // 销毁当前 HTTP 会话及绑定的所有登录属性
        session.invalidate();
        // 登出后重定向返回管理员登录页面
        return "redirect:/admin/login";
    }

    // 映射 GET /admin/reservations（后台预约总看板）
    @GetMapping("/admin/reservations")
    // 渲染后台预约看板列表的方法
    public String reservationList(Model model, HttpSession session) {
        // 从当前会话中获取管理员登录状态标记
        Boolean adminLoggedIn = (Boolean) session.getAttribute("adminLoggedIn");
        // 登录鉴权守卫：若未登录或标记为 false 则拦截
        if (adminLoggedIn == null || !adminLoggedIn) {
            // 拦截并强制重定向回登录页
            return "redirect:/admin/login";
        }

        // 查询 reservations 表全部预约记录（按自增主键 id 倒序展示最新订单）
        String sql = """
                SELECT id, reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text, created_at
                FROM reservations
                ORDER BY id DESC
                """;

        // 执行 SQL 查询并将每行数据映射为 Reservation 对象
        List<Reservation> reservations = jdbcTemplate.query(sql, (rs, rowNum) -> {
            // 创建 Reservation 实体实例
            Reservation reservation = new Reservation();
            // 映射预约主键 ID
            reservation.setId(rs.getLong("id"));
            // 映射预约编号
            reservation.setReservationCode(rs.getString("reservation_code"));
            // 映射宠主姓名
            reservation.setOwnerName(rs.getString("owner_name"));
            // 映射手机号
            reservation.setPhone(rs.getString("phone"));
            // 映射邮箱
            reservation.setEmail(rs.getString("email"));
            // 映射猫咪姓名
            reservation.setCatName(rs.getString("cat_name"));
            // 映射入住日期
            reservation.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
            // 映射退房日期
            reservation.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
            // 映射房型
            reservation.setRoomType(rs.getString("room_type"));
            // 映射备注内容
            reservation.setNoteText(rs.getString("note_text"));
            // 判断创建时间是否非空
            if (rs.getTimestamp("created_at") != null) {
                // 转换时间戳并赋值
                reservation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            // 返回组装好的实体对象
            return reservation;
        });

        // 将预约记录列表绑定至 Model 供后台模板渲染
        model.addAttribute("reservations", reservations);
        // 渲染并返回 reservations.html 视图模板
        return "reservations";
    }

    // 映射 POST /admin/reservations/delete（管理员在后台删除预约）
    @PostMapping("/admin/reservations/delete")
    // 管理员删除订单主方法
    public String deleteReservationByAdmin(
            // 接收要删除的订单主键 ID
            @RequestParam Long id,
            // HTTP 会话对象用于权限核验
            HttpSession session,
            // 跨重定向闪存属性传递接口
            RedirectAttributes redirectAttributes
    ) {
        // 读取管理员登录状态标记
        Boolean adminLoggedIn = (Boolean) session.getAttribute("adminLoggedIn");
        // 校验登录状态
        if (adminLoggedIn == null || !adminLoggedIn) {
            // 未登录重定向至登录页
            return "redirect:/admin/login";
        }

        // 根据 ID 检索预约记录的入住日期
        String checkSql = """
                SELECT check_in_date
                FROM reservations
                WHERE id = ?
                """;

        // 查询该订单的入住日期
        LocalDate checkInDate = jdbcTemplate.queryForObject(
                checkSql,
                LocalDate.class,
                id
        );

        // 检查订单是否存在
        if (checkInDate == null) {
            // 提示订单不存在
            redirectAttributes.addFlashAttribute("adminError", "対象の予約が見つかりませんでした。");
            // 重定向返回看板
            return "redirect:/admin/reservations";
        }

        // 业务约束：入住日期必须在今天之后，当天或过去的订单不允许删除
        if (!checkInDate.isAfter(LocalDate.now())) {
            // 提示入住日期为今天或以前的预约无法删除
            redirectAttributes.addFlashAttribute("adminError", "チェックイン日が今日以前の予約は削除できません。");
            // 重定向返回看板
            return "redirect:/admin/reservations";
        }

        // 执行物理删除预约记录的 SQL
        String sql = "DELETE FROM reservations WHERE id = ?";
        // 调用 JdbcTemplate 执行删除
        jdbcTemplate.update(sql, id);

        // 添加删除成功提示消息
        redirectAttributes.addFlashAttribute("adminMessage", "予約を削除しました。");
        // 重定向刷新后台列表
        return "redirect:/admin/reservations";
    }

    // 映射 GET /admin/reservations/edit/{id}（管理员进入预约编辑页面）
    @GetMapping("/admin/reservations/edit/{id}")
    public String editReservationPage(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        // 校验管理员登录状态，未登录则跳转回登录页
        Boolean adminLoggedIn = (Boolean) session.getAttribute("adminLoggedIn");
        if (adminLoggedIn == null || !adminLoggedIn) {
            return "redirect:/admin/login";
        }

        // 根据预约 ID 查询该条预约的完整信息
        String sql = """
                SELECT id, reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text
                FROM reservations
                WHERE id = ?
                """;

        List<Reservation> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Reservation r = new Reservation();
            r.setId(rs.getLong("id"));
            r.setReservationCode(rs.getString("reservation_code"));
            r.setOwnerName(rs.getString("owner_name"));
            r.setPhone(rs.getString("phone"));
            r.setEmail(rs.getString("email"));
            r.setCatName(rs.getString("cat_name"));
            r.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
            r.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
            r.setRoomType(rs.getString("room_type"));
            r.setNoteText(rs.getString("note_text"));
            return r;
        }, id);

        // 若未查到对应记录（如已被删除），携带提示信息返回预约一览表
        if (list.isEmpty()) {
            redirectAttributes.addFlashAttribute("adminMessage", "対象の予約データが見つかりませんでした。");
            return "redirect:/admin/reservations";
        }

        // 将预约对象放入 Model，渲染独立编辑页面 reservation-edit.html
        model.addAttribute("reservation", list.get(0));
        return "reservation-edit";
    }

    // 映射 POST /admin/reservations/update（管理员提交保存预约修改）
    @PostMapping("/admin/reservations/update")
    public String updateReservationByAdmin(
            @RequestParam Long id,
            @RequestParam String catName,
            @RequestParam String checkInDate,
            @RequestParam String checkOutDate,
            @RequestParam String roomType,
            @RequestParam(required = false) String noteText,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        // 校验管理员登录状态
        Boolean adminLoggedIn = (Boolean) session.getAttribute("adminLoggedIn");
        if (adminLoggedIn == null || !adminLoggedIn) {
            return "redirect:/admin/login";
        }

        // 查询原订单的客户身份凭证（用于校验失败时回显只读区域，同时防止修改主人姓名、电话、邮箱）
        String findSql = """
                SELECT id, reservation_code, owner_name, phone, email
                FROM reservations
                WHERE id = ?
                """;
        List<Reservation> existingList = jdbcTemplate.query(findSql, (rs, rowNum) -> {
            Reservation r = new Reservation();
            r.setId(rs.getLong("id"));
            r.setReservationCode(rs.getString("reservation_code"));
            r.setOwnerName(rs.getString("owner_name"));
            r.setPhone(rs.getString("phone"));
            r.setEmail(rs.getString("email"));
            return r;
        }, id);

        // 若原订单已不存在，直接返回列表页
        if (existingList.isEmpty()) {
            return "redirect:/admin/reservations";
        }

        // 将表单提交的日期字符串解析为 LocalDate 对象
        LocalDate inDate = LocalDate.parse(checkInDate);
        LocalDate outDate = LocalDate.parse(checkOutDate);

        // 组装回显对象（保留原有客户只读信息，填入管理员刚填写的修改内容）
        Reservation formBack = existingList.get(0);
        formBack.setCatName(catName);
        formBack.setCheckInDate(inDate);
        formBack.setCheckOutDate(outDate);
        formBack.setRoomType(roomType);
        formBack.setNoteText(noteText);

        // 校验 1：退房日期必须晚于入住日期
        if (!outDate.isAfter(inDate)) {
            model.addAttribute("editError", "チェックアウト日はチェックイン日より後の日付を指定してください。");
            model.addAttribute("reservation", formBack);
            return "reservation-edit";
        }

        // 校验 2：余房库存检查（需排除当前正在编辑的订单本身 id != ?，避免把自身算作冲突占用）
        String countSql = """
                SELECT COUNT(*)
                FROM reservations
                WHERE room_type = ?
                  AND check_in_date < ?
                  AND check_out_date > ?
                  AND id != ?
                """;
        Integer reservedCount = jdbcTemplate.queryForObject(countSql, Integer.class, roomType, outDate, inDate, id);
        int maxRooms = getRoomCapacity(roomType);
        if (reservedCount != null && reservedCount >= maxRooms) {
            model.addAttribute("editError", "選択された日程・お部屋タイプは満室のため変更できません。");
            model.addAttribute("reservation", formBack);
            return "reservation-edit";
        }

        // 执行数据库更新（仅更新猫咪名字、入住/退房日期、房型、备注）
        String updateSql = """
                UPDATE reservations
                SET cat_name = ?,
                    check_in_date = ?,
                    check_out_date = ?,
                    room_type = ?,
                    note_text = ?
                WHERE id = ?
                """;
        jdbcTemplate.update(updateSql, catName.trim(), inDate, outDate, roomType,
                noteText != null ? noteText.trim() : "", id);

        // 调用 MailService 向客户发送预约变更通知邮件
        mailService.sendReservationUpdatedMailToCustomer(formBack);

        // 添加操作成功提示并重定向回预约一览表
        redirectAttributes.addFlashAttribute("adminMessage",
                "予約内容（" + formBack.getReservationCode() + "）を更新し、お客様へ変更通知メールを送信しました。");
        return "redirect:/admin/reservations";
    }

    // 辅助方法：获取各房型的最大房间总数（需与 HomeController 中的房间上限保持一致）
    private int getRoomCapacity(String roomType) {
        return switch (roomType) {
            case "スタンダードルーム" -> 5;
            case "ファミリー2匹ルーム" -> 3;
            case "サンシャイン・ラグジュアリールーム" -> 2;
            default -> 1;
        };
    }

    // 映射 POST /admin/api/relogin（超时弹窗异步重新验证）
    @PostMapping("/admin/api/relogin")
    @ResponseBody
    public ResponseEntity<?> apiRelogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session
    ) {
        // 1. 查询数据库中的管理员密码
        String sql = "SELECT password FROM admins WHERE username = ?";
        List<String> passwords = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getString("password"),
                username
        );

        // 2. 账号不存在或密码不匹配，返回 401 错误
        if (passwords.isEmpty() || !passwords.get(0).equals(password)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "ユーザー名またはパスワードが正しくありません。"));
        }

        // 3. 校验通过，为当前 Session 续期登录标记
        session.setAttribute("adminLoggedIn", true);

        // 4. 返回 200 成功状态
        return ResponseEntity.ok(Map.of("success", true));
    }
}