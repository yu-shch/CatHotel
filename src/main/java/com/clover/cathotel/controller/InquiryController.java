// 声明当前控制器类所在的包路径
package com.clover.cathotel.controller;

// 导入预约数据实体模型类（用于封装数据库查询结果并传递给前端模板）
import com.clover.cathotel.model.Reservation;
// 导入 Spring JDBC 数据库操作核心模板类（用于执行参数化 SQL 查询与增删改）
import org.springframework.jdbc.core.JdbcTemplate;
// 导入 Spring MVC 控制器注解（将该类注册为 Web 请求处理组件）
import org.springframework.stereotype.Controller;
// 导入 Spring MVC 视图模型容器（用于向 Thymeleaf 模板传递数据）
import org.springframework.ui.Model;
// 导入 HTTP GET 请求路由映射注解
import org.springframework.web.bind.annotation.GetMapping;
// 导入 HTTP POST 请求路由映射注解
import org.springframework.web.bind.annotation.PostMapping;
// 导入请求参数绑定注解（用于接收 URL 查询参数或表单提交字段）
import org.springframework.web.bind.annotation.RequestParam;

// 导入 java.sql.Date（用于将 Java 8 的 LocalDate 转换为 JDBC 兼容的 SQL 日期类型）
import java.sql.Date;
// 导入 Java 8 本地日期类 LocalDate（用于日期比较、入离店时效判断）
import java.time.LocalDate;
// 导入日期格式解析异常类（用于捕获非法日期字符串转换异常）
import java.time.format.DateTimeParseException;
// 导入动态数组列表实现类 ArrayList
import java.util.ArrayList;
// 导入 List 集合接口
import java.util.List;

/**
 * 顾客预约照会控制器（InquiryController）
 * 核心职责：
 * 1. 照会页面展示（GET /inquiry）：支持接收预填参数并初始化查询模式
 * 2. 双模式预约检索（POST /inquiry）：
 *    - 模式 1（code）：凭「预约编号 + 邮箱」精准查询单条预约，并将同邮箱+同姓名的其他记录折叠至底部展开区
 *    - 模式 2（history）：凭「宠主姓名 + 邮箱」查询该本人的全部新旧预约记录
 *    - 数据隔离：强制按「邮箱 + 宠主姓名」双重匹配，防止同一邮箱下的不同姓名客户数据串号
 * 3. 顾客自主修改预约（POST /inquiry/update）：仅限入住日前一天为止，修改时校验日期合法性与房型余量
 * 4. 顾客自主取消预约（POST /inquiry/delete）：仅限入住日前一天为止，校验归属权限后执行删除
 */
@Controller
public class InquiryController {

    // 定义标准间（スタンダードルーム）的最大可预订房间数（用于修改预约时的空房校验）
    private static final int STANDARD_ROOM_CAPACITY = 5;
    // 定义阳光豪华间（サンシャイン・ラグジュアリールーム）的最大可预订房间数
    private static final int LUXURY_ROOM_CAPACITY = 3;
    // 定义家庭双猫间（ファミリー2匹ルーム）的最大可预订房间数
    private static final int FAMILY_ROOM_CAPACITY = 2;

    // 声明 JdbcTemplate 数据库操作对象（使用 final 修饰保证线程安全与不可变性）
    private final JdbcTemplate jdbcTemplate;

    /**
     * 控制器构造函数：通过 Spring 构造器依赖注入（DI）自动装配 JdbcTemplate
     *
     * @param jdbcTemplate Spring 容器管理的 JDBC 操作模板实例
     */
    public InquiryController(JdbcTemplate jdbcTemplate) {
        // 将注入的 JdbcTemplate 赋值给类成员变量
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 映射 GET /inquiry 请求：渲染顾客预约照会输入页面
     * 支持从 URL 查询参数接收预填信息（例如从预约完成页跳转过来时自动填入单号与邮箱）
     *
     * @param reservationCode URL 预填的预约编号（非必填）
     * @param email           URL 预填的电子邮箱（非必填）
     * @param ownerName       URL 预填的宠主姓名（非必填）
     * @param model           Spring MVC 视图模型对象
     * @return 逻辑视图名称 "inquiry"（对应 templates/inquiry.html）
     */
    @GetMapping("/inquiry")
    public String inquiryPage(
            // 接收 URL 中的预约编号参数
            @RequestParam(required = false) String reservationCode,
            // 接收 URL 中的电子邮箱参数
            @RequestParam(required = false) String email,
            // 接收 URL 中的宠主姓名参数
            @RequestParam(required = false) String ownerName,
            // 注入视图模型容器
            Model model
    ) {
        // 若 URL 携带了参数，则放入 Model 供输入框回显
        if (reservationCode != null) {
            model.addAttribute("reservationCode", reservationCode);
        }
        if (email != null) {
            model.addAttribute("email", email);
        }
        if (ownerName != null) {
            model.addAttribute("ownerName", ownerName);
        }
        // 返回照会输入页面模板
        return "inquiry";
    }

    /**
     * 映射 POST /inquiry 请求：邮箱必填 +（预约编号 / 宠主姓名 二选一或全填）统一查询
     */
    @PostMapping("/inquiry")
    public String inquirySearch(
            // 获取预约编号，未传时默认为空字符串防止空指针
            @RequestParam(required = false, defaultValue = "") String reservationCode,
            // 获取电子邮箱，未传时默认为空字符串
            @RequestParam(required = false, defaultValue = "") String email,
            // 获取宠主姓名，未传时默认为空字符串
            @RequestParam(required = false, defaultValue = "") String ownerName,
            // 视图模型容器
            Model model
    ) {
        // 去除用户输入内容首尾的空白字符
        String trimmedEmail = email.trim();
        String trimmedCode = reservationCode.trim();
        String trimmedOwnerName = ownerName.trim();

        // 根据是否填写了预约编号，自动推导兼容现有结果页的展示模式（有单号精准定位该单，无单号展示最新订单）
        String derivedMode = !trimmedCode.isEmpty() ? "code" : "history";

        // --------------------------------------------------------
        // 第 1 步：单条精简日语必填校验（邮箱必填 + 预约编号/姓名至少填一项）
        // --------------------------------------------------------
        String errorMessage = null;

        if (trimmedEmail.isEmpty() && trimmedCode.isEmpty() && trimmedOwnerName.isEmpty()) {
            errorMessage = "メールアドレスと、予約番号またはお名前を入力してください。";
        } else if (trimmedEmail.isEmpty()) {
            errorMessage = "メールアドレスを入力してください。";
        } else if (trimmedCode.isEmpty() && trimmedOwnerName.isEmpty()) {
            errorMessage = "予約番号または飼い主さまのお名前のいずれかを入力してください。";
        }

        // 若存在未填项，写入单条 inquiryError 并退回查询页
        if (errorMessage != null) {
            model.addAttribute("inquiryError", errorMessage);
            restoreFormModel(model, derivedMode, trimmedCode, trimmedEmail, trimmedOwnerName);
            return "inquiry";
        }

        // --------------------------------------------------------
        // 第 2 步：根据邮箱检索候选预约记录
        // --------------------------------------------------------
        List<Reservation> emailMatchedList = findReservationsByEmail(trimmedEmail);
        if (emailMatchedList.isEmpty()) {
            model.addAttribute("inquiryError", "入力した情報に一致する予約が見つかりませんでした。");
            restoreFormModel(model, derivedMode, trimmedCode, trimmedEmail, trimmedOwnerName);
            return "inquiry";
        }

        // --------------------------------------------------------
        // 第 3 步：身份核验并锁定当前查询本人的姓名（支持只填编号、只填姓名、或两者都填）
        // --------------------------------------------------------
        String resolvedOwnerName = null;
        String targetCode = trimmedCode.toUpperCase();
        String targetNormName = normalizeText(trimmedOwnerName);

        for (Reservation r : emailMatchedList) {
            boolean codeMatches = !trimmedCode.isEmpty()
                    && r.getReservationCode() != null
                    && r.getReservationCode().trim().toUpperCase().equals(targetCode);
            boolean nameMatches = !trimmedOwnerName.isEmpty()
                    && normalizeText(r.getOwnerName()).equals(targetNormName);

            if (!trimmedCode.isEmpty() && !trimmedOwnerName.isEmpty()) {
                // 情况 A：编号与姓名都填了 -> 必须同一条记录同时满足
                if (codeMatches && nameMatches) {
                    resolvedOwnerName = r.getOwnerName();
                    break;
                }
            } else if (!trimmedCode.isEmpty()) {
                // 情况 B：只填了编号 -> 匹配编号并提取本人姓名
                if (codeMatches) {
                    resolvedOwnerName = r.getOwnerName();
                    break;
                }
            } else {
                // 情况 C：只填了姓名 -> 匹配姓名并提取本人姓名
                if (nameMatches) {
                    resolvedOwnerName = r.getOwnerName();
                    break;
                }
            }
        }

        // 若未找到匹配的编号或姓名，返回未找到提示并保留输入内容
        if (resolvedOwnerName == null) {
            model.addAttribute("inquiryError", "入力した情報に一致する予約が見つかりませんでした。");
            restoreFormModel(model, derivedMode, trimmedCode, trimmedEmail, trimmedOwnerName);
            return "inquiry";
        }

        // --------------------------------------------------------
        // 第 4 步：严格过滤出“同邮箱 + 同姓名”的本人所有预约记录
        // --------------------------------------------------------
        List<Reservation> personReservations = filterByOwnerName(emailMatchedList, resolvedOwnerName);

        // --------------------------------------------------------
        // 第 5 步：组装结果页所需数据模型并跳转至 inquiry-result.html
        // --------------------------------------------------------
        populateResultModel(model, derivedMode, trimmedCode, trimmedEmail, resolvedOwnerName, personReservations);
        return "inquiry-result";
    }
    /**
     * 映射 POST /inquiry/update 请求：处理顾客在弹窗中提交的预约内容修改
     * 支持修改字段：猫咪姓名（catName）、入住日期（checkInDate）、退房日期（checkOutDate）、房型（roomType）、备注（noteText）
     *
     * @param id              被修改预约的主键 ID
     * @param searchMode      当前查询模式上下文（用于修改后刷新结果页保持原视图状态）
     * @param reservationCode 当前查询的预约编号上下文
     * @param email           当前查询的电子邮箱（用于权限归属校验）
     * @param ownerName       当前查询的宠主姓名（用于权限归属校验）
     * @param catName         修改后的猫咪姓名
     * @param checkInDate     修改后的入住日期字符串（yyyy-MM-dd）
     * @param checkOutDate    修改后的退房日期字符串（yyyy-MM-dd）
     * @param roomType        修改后的房间类型
     * @param noteText        修改后的备注内容
     * @param model           Spring MVC 视图模型对象
     * @return 处理完成后返回结果页 "inquiry-result"（越权非法请求退回 "inquiry"）
     */
    @PostMapping("/inquiry/update")
    public String updateReservationByCustomer(
            // 接收预约主键 ID
            @RequestParam Long id,
            // 维持当前查询模式上下文
            @RequestParam(defaultValue = "code") String searchMode,
            // 维持当前预约编号上下文
            @RequestParam(required = false, defaultValue = "") String reservationCode,
            // 接收电子邮箱作为归属校验凭证
            @RequestParam String email,
            // 接收宠主姓名作为归属校验凭证
            @RequestParam String ownerName,
            // 接收编辑弹窗提交的猫咪姓名
            @RequestParam(defaultValue = "") String catName,
            // 接收编辑弹窗提交的入住日期
            @RequestParam(defaultValue = "") String checkInDate,
            // 接收编辑弹窗提交的退房日期
            @RequestParam(defaultValue = "") String checkOutDate,
            // 接收编辑弹窗提交的房型名称
            @RequestParam(defaultValue = "") String roomType,
            // 接收编辑弹窗提交的备注内容（允许为空）
            @RequestParam(required = false, defaultValue = "") String noteText,
            // 视图模型容器
            Model model
    ) {
        // 规范化查询模式与邮箱、姓名参数
        String mode = "history".equals(searchMode) ? "history" : "code";
        String trimmedEmail = email.trim();
        String trimmedOwnerName = ownerName.trim();

        // --------------------------------------------------------
        // 第 1 步：联合校验主键 ID、邮箱与宠主姓名归属（防止篡改 ID 越权修改他人订单）
        // --------------------------------------------------------
        String verifySql = """
                SELECT check_in_date, owner_name
                FROM reservations
                WHERE id = ?
                  AND LOWER(TRIM(email)) = ?
                """;

        // 查询数据库中对应 ID 且邮箱一致的记录
        List<Object[]> rows = jdbcTemplate.query(
                verifySql,
                (rs, rowNum) -> new Object[]{
                        rs.getDate("check_in_date").toLocalDate(),
                        rs.getString("owner_name")
                },
                id,
                trimmedEmail.toLowerCase()
        );

        // 若记录不存在，或数据库中的姓名与当前本人姓名不一致，拒绝操作并退回查询页
        if (rows.isEmpty() || !normalizeText((String) rows.get(0)[1]).equals(normalizeText(trimmedOwnerName))) {
            model.addAttribute("inquiryError", "入力情報が一致しないため、予約を変更できませんでした。");
            restoreFormModel(model, mode, reservationCode, trimmedEmail, trimmedOwnerName);
            return "inquiry";
        }

        // 提取原预约的入住日期与数据库真实姓名
        LocalDate currentCheckIn = (LocalDate) rows.get(0)[0];
        String dbOwnerName = (String) rows.get(0)[1];

        // --------------------------------------------------------
        // 第 2 步：业务时效校验（入住日当天及以前的预约不允许修改）
        // --------------------------------------------------------
        if (!currentCheckIn.isAfter(LocalDate.now())) {
            model.addAttribute("inquiryError", "チェックイン日が今日以前の予約は変更できません。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // --------------------------------------------------------
        // 第 3 步：校验提交的表单内容（非空、房型合法性、日期合法性）
        // --------------------------------------------------------
        String trimmedCatName = catName.trim();
        String trimmedRoomType = roomType.trim();
        String trimmedNote = noteText == null ? "" : noteText.trim();

        // 校验猫咪姓名是否为空
        if (trimmedCatName.isEmpty()) {
            model.addAttribute("inquiryError", "猫ちゃんのお名前を入力してください。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // 根据房型名称获取总房间数，若返回 <= 0 说明房型名称非法
        int roomCapacity = getRoomCapacity(trimmedRoomType);
        if (roomCapacity <= 0) {
            model.addAttribute("inquiryError", "お部屋タイプを正しく選択してください。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // 解析前端传入的入住日期与退房日期
        LocalDate newCheckIn;
        LocalDate newCheckOut;
        try {
            newCheckIn = LocalDate.parse(checkInDate.trim());
            newCheckOut = LocalDate.parse(checkOutDate.trim());
        } catch (DateTimeParseException e) {
            // 日期格式错误或未填写时提示错误
            model.addAttribute("inquiryError", "チェックイン日とチェックアウト日を正しく入力してください。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // 校验新入住日期必须是明天及以后（不能修改为今天或过去日期）
        if (!newCheckIn.isAfter(LocalDate.now())) {
            model.addAttribute("inquiryError", "チェックイン日は明日以降の日付を指定してください。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // 校验退房日期必须晚于入住日期（至少住宿 1 晚）
        if (!newCheckOut.isAfter(newCheckIn)) {
            model.addAttribute("inquiryError", "チェックアウト日はチェックイン日より後の日付を指定してください。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // --------------------------------------------------------
        // 第 4 步：空房余量校验（排除当前正在编辑的订单本身后统计日期重叠订单数）
        // 日期区间重叠条件：已有入住日 < 新退房日 AND 已有退房日 > 新入住日
        // --------------------------------------------------------
        String overlapSql = """
                SELECT COUNT(*)
                FROM reservations
                WHERE room_type = ?
                  AND id <> ?
                  AND check_in_date < ?
                  AND check_out_date > ?
                """;
        // 查询该房型在目标时间段内的其他已占用房间数
        Integer overlappingCount = jdbcTemplate.queryForObject(
                overlapSql,
                Integer.class,
                trimmedRoomType,
                id,
                Date.valueOf(newCheckOut),
                Date.valueOf(newCheckIn)
        );

        // 若已占用数大于等于该房型总容量，说明满房，拒绝修改
        if (overlappingCount != null && overlappingCount >= roomCapacity) {
            model.addAttribute("inquiryError", "ご指定の日程・お部屋タイプは満室のため変更できません。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // --------------------------------------------------------
        // 第 5 步：校验全部通过，执行数据库 UPDATE 更新预约记录
        // --------------------------------------------------------
        String updateSql = """
                UPDATE reservations
                SET cat_name = ?,
                    check_in_date = ?,
                    check_out_date = ?,
                    room_type = ?,
                    note_text = ?
                WHERE id = ?
                  AND LOWER(TRIM(email)) = ?
                """;
        // 执行参数化更新 SQL
        jdbcTemplate.update(
                updateSql,
                trimmedCatName,
                Date.valueOf(newCheckIn),
                Date.valueOf(newCheckOut),
                trimmedRoomType,
                trimmedNote,
                id,
                trimmedEmail.toLowerCase()
        );

        // --------------------------------------------------------
        // 第 6 步：重新查询更新后的本人预约列表，并携带成功提示返回结果页
        // --------------------------------------------------------
        List<Reservation> updatedReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
        model.addAttribute("inquirySuccess", "予約内容を変更しました。");
        populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, updatedReservations);

        return "inquiry-result";
    }

    /**
     * 映射 POST /inquiry/delete 请求：处理顾客自主取消预约
     * 业务规则：仅允许取消入住日在今天之后的未来预约，当天及过去记录不可取消
     *
     * @param id              要取消的预约主键 ID
     * @param searchMode      当前查询模式上下文
     * @param reservationCode 当前预约编号上下文
     * @param email           当前查询的电子邮箱（归属校验凭证）
     * @param ownerName       当前查询的宠主姓名（归属校验凭证）
     * @param model           Spring MVC 视图模型对象
     * @return 取消完成后返回结果页 "inquiry-result"（越权非法请求退回 "inquiry"）
     */
    @PostMapping("/inquiry/delete")
    public String deleteReservationByCustomer(
            // 接收要删除的预约 ID
            @RequestParam Long id,
            // 维持当前查询模式上下文
            @RequestParam(defaultValue = "code") String searchMode,
            // 维持当前预约编号上下文
            @RequestParam(required = false, defaultValue = "") String reservationCode,
            // 接收电子邮箱用于归属校验
            @RequestParam String email,
            // 接收宠主姓名用于归属校验
            @RequestParam String ownerName,
            // 视图模型容器
            Model model
    ) {
        // 规范化查询模式与邮箱、姓名
        String mode = "history".equals(searchMode) ? "history" : "code";
        String trimmedEmail = email.trim();
        String trimmedOwnerName = ownerName.trim();

        // --------------------------------------------------------
        // 第 1 步：联合校验 ID、邮箱与姓名归属（防止越权删除他人订单）
        // --------------------------------------------------------
        String verifySql = """
                SELECT check_in_date, owner_name
                FROM reservations
                WHERE id = ?
                  AND LOWER(TRIM(email)) = ?
                """;

        // 查询目标订单的入住日期与真实姓名
        List<Object[]> rows = jdbcTemplate.query(
                verifySql,
                (rs, rowNum) -> new Object[]{
                        rs.getDate("check_in_date").toLocalDate(),
                        rs.getString("owner_name")
                },
                id,
                trimmedEmail.toLowerCase()
        );

        // 若未查到记录或姓名不匹配，拒绝删除并退回查询输入页
        if (rows.isEmpty() || !normalizeText((String) rows.get(0)[1]).equals(normalizeText(trimmedOwnerName))) {
            model.addAttribute("inquiryError", "入力情報が一致しないため、予約を削除できませんでした。");
            restoreFormModel(model, mode, reservationCode, trimmedEmail, trimmedOwnerName);
            return "inquiry";
        }

        // 提取入住日期与数据库真实姓名
        LocalDate checkInDate = (LocalDate) rows.get(0)[0];
        String dbOwnerName = (String) rows.get(0)[1];

        // --------------------------------------------------------
        // 第 2 步：校验入住日期时效（当天及以前的预约不可取消）
        // --------------------------------------------------------
        if (!checkInDate.isAfter(LocalDate.now())) {
            model.addAttribute("inquiryError", "チェックイン日が今日以前の予約は削除できません。");
            List<Reservation> personReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
            populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, personReservations);
            return "inquiry-result";
        }

        // --------------------------------------------------------
        // 第 3 步：执行数据库物理删除
        // --------------------------------------------------------
        String deleteSql = """
                DELETE FROM reservations
                WHERE id = ?
                  AND LOWER(TRIM(email)) = ?
                """;
        // 执行删除操作
        jdbcTemplate.update(deleteSql, id, trimmedEmail.toLowerCase());

        // --------------------------------------------------------
        // 第 4 步：重新查询该本人的剩余预约记录并返回结果页
        // --------------------------------------------------------
        List<Reservation> remainingReservations = filterByOwnerName(findReservationsByEmail(trimmedEmail), dbOwnerName);
        model.addAttribute("inquirySuccess", "予約を取り消しました。");
        populateResultModel(model, mode, reservationCode.trim(), trimmedEmail, dbOwnerName, remainingReservations);

        return "inquiry-result";
    }

    // ========================================================
    // 内部私有辅助方法区
    // ========================================================

    /**
     * 根据房型名称返回对应的总房间数量（若房型名称不合法则返回 0）
     *
     * @param roomType 房间类型名称
     * @return 该房型的最大可预订房间数
     */
    private int getRoomCapacity(String roomType) {
        return switch (roomType) {
            case "スタンダードルーム" -> STANDARD_ROOM_CAPACITY;
            case "サンシャイン・ラグジュアリールーム" -> LUXURY_ROOM_CAPACITY;
            case "ファミリー2匹ルーム" -> FAMILY_ROOM_CAPACITY;
            default -> 0;
        };
    }

    /**
     * 根据电子邮箱（忽略大小写与首尾空格）查询数据库中的所有候选预约记录
     * 排序规则：按入住日期倒序排列，最新或未来的预约排在最前面
     *
     * @param email 电子邮箱地址
     * @return 匹配该邮箱的预约实体列表
     */
    private List<Reservation> findReservationsByEmail(String email) {
        String sql = """
                SELECT id, reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text, created_at
                FROM reservations
                WHERE LOWER(TRIM(email)) = ?
                ORDER BY check_in_date DESC, created_at DESC
                """;

        // 执行查询并将结果集每一行映射为 Reservation 实体对象
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Reservation reservation = new Reservation();
            // 映射主键 ID
            reservation.setId(rs.getLong("id"));
            // 映射预约流水编号
            reservation.setReservationCode(rs.getString("reservation_code"));
            // 映射宠主姓名
            reservation.setOwnerName(rs.getString("owner_name"));
            // 映射联系电话
            reservation.setPhone(rs.getString("phone"));
            // 映射电子邮箱
            reservation.setEmail(rs.getString("email"));
            // 映射猫咪姓名
            reservation.setCatName(rs.getString("cat_name"));
            // 映射入住日期（java.sql.Date -> java.time.LocalDate）
            reservation.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
            // 映射退房日期（java.sql.Date -> java.time.LocalDate）
            reservation.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
            // 映射房间类型
            reservation.setRoomType(rs.getString("room_type"));
            // 映射备注信息
            reservation.setNoteText(rs.getString("note_text"));
            // 若创建时间字段非空，转换为 LocalDateTime 并赋值
            if (rs.getTimestamp("created_at") != null) {
                reservation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            return reservation;
        }, email.trim().toLowerCase());
    }

    /**
     * 从候选列表中过滤出与目标宠主姓名一致的预约记录（自动忽略全半角空格与大小写差异）
     * 作用：防止同一邮箱下登记的不同姓名顾客数据互相混入
     *
     * @param list            同邮箱候选预约列表
     * @param targetOwnerName 目标本人姓名
     * @return 仅属于该本人的预约记录列表
     */
    private List<Reservation> filterByOwnerName(List<Reservation> list, String targetOwnerName) {
        // 清洗目标姓名中的空格并转为小写
        String targetNorm = normalizeText(targetOwnerName);
        List<Reservation> filtered = new ArrayList<>();
        // 遍历候选记录，仅保留姓名规范化后完全一致的记录
        for (Reservation r : list) {
            if (normalizeText(r.getOwnerName()).equals(targetNorm)) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    /**
     * 组装结果页（inquiry-result.html）所需的全部 Model 属性
     * - 当 mode 为 "code"（按预约编号查询）时：将目标单号放入 mainReservations（主表格），
     *   其余本人记录放入 otherReservations（表格下方的“過去の履歴を見る”折叠区）
     * - 当 mode 为 "history"（按姓名+邮箱查询）时：将本人全部记录直接放入 mainReservations
     *
     * @param model              视图模型容器
     * @param mode               当前查询模式（"code" 或 "history"）
     * @param reservationCode    查询使用的预约编号
     * @param email              查询使用的电子邮箱
     * @param ownerName          核验通过的本人姓名
     * @param personReservations 该本人的全部预约记录
     */
    private void populateResultModel(
            Model model,
            String mode,
            String reservationCode,
            String email,
            String ownerName,
            List<Reservation> personReservations
    ) {
        // 主表格展示列表
        List<Reservation> mainReservations = new ArrayList<>();
        // 底部“过去履历”折叠区展示列表
        List<Reservation> otherReservations = new ArrayList<>();

        if ("code".equals(mode) && !reservationCode.isEmpty()) {
            // 按预约编号查询时，拆分出命中的主订单与其余历史/关联订单
            String targetCode = reservationCode.toUpperCase();
            for (Reservation r : personReservations) {
                if (mainReservations.isEmpty()
                        && r.getReservationCode() != null
                        && r.getReservationCode().trim().toUpperCase().equals(targetCode)) {
                    mainReservations.add(r);
                } else {
                    otherReservations.add(r);
                }
            }
            // 若该单号已被顾客取消，主表格自动回退展示该本人剩余的全部记录
            if (mainReservations.isEmpty()) {
                mainReservations.addAll(personReservations);
                otherReservations.clear();
            }
        } else {
            // 按姓名与邮箱查询时，主表格直接展示该本人的全部新旧预约
            mainReservations.addAll(personReservations);
        }

        // 传入当前系统日期 today，供 Thymeleaf 判断是否允许编辑/取消（避免模板直接调用静态方法报错）
        model.addAttribute("today", LocalDate.now());
        // 回传查询模式与条件上下文（用于页面文案切换及编辑/删除表单隐藏域回传）
        model.addAttribute("searchMode", mode);
        model.addAttribute("searchedReservationCode", reservationCode);
        model.addAttribute("searchedEmail", email);
        model.addAttribute("searchedOwnerName", ownerName);
        // 注入主表格列表与折叠历史列表
        model.addAttribute("reservations", mainReservations);
        model.addAttribute("otherReservations", otherReservations);
    }

    /**
     * 当校验失败或未找到记录退回 inquiry.html 时，恢复用户刚才输入的表单内容与模式状态
     *
     * @param model           视图模型容器
     * @param mode            查询模式
     * @param reservationCode 预约编号
     * @param email           电子邮箱
     * @param ownerName       宠主姓名
     */
    private void restoreFormModel(Model model, String mode, String reservationCode, String email, String ownerName) {
        model.addAttribute("searchMode", mode);
        model.addAttribute("reservationCode", reservationCode);
        model.addAttribute("email", email);
        model.addAttribute("ownerName", ownerName);
    }

    /**
     * 文本规范化工具方法：移除字符串中的所有半角空格与全角空格（\u3000），并统一转为小写
     * 作用：兼容日本人输入姓名时姓与名之间带空格或不带空格的差异（例如 "加藤 愛美" 与 "加藤愛美"）
     *
     * @param val 原始字符串
     * @return 清洗后的规范化字符串
     */
    private String normalizeText(String val) {
        if (val == null) return "";
        return val.replaceAll("[\\s\\u3000]+", "").toLowerCase();
    }
}