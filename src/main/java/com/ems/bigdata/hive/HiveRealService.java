package com.ems.bigdata.hive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * 真实 Hive 数据仓库服务（通过 JDBC 连接 HiveServer2）
 *
 * 与 HiveSimulator 的区别：
 * - HiveSimulator 用本地文件 JSON 存储模拟
 * - HiveRealService 通过 JDBC 协议连接真实 HiveServer2，
 *   执行真实的 HiveQL 语句，数据存储在 HDFS 上
 *
 * 连接方式：jdbc:hive2://<host>:<port>/<database>
 * 默认：jdbc:hive2://localhost:10000/default
 */
@Service
public class HiveRealService {

    private static final Logger log = LoggerFactory.getLogger(HiveRealService.class);

    @Value("${bigdata.hive.jdbc-url:jdbc:hive2://localhost:10000/default}")
    private String jdbcUrl;

    @Value("${bigdata.hive.username:hive}")
    private String username;

    @Value("${bigdata.hive.password:}")
    private String password;

    /** 获取 JDBC 连接 */
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("org.apache.hive.jdbc.HiveDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Hive JDBC 驱动未找到", e);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * 测试连接是否正常
     */
    public Map<String, Object> checkConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", jdbcUrl);
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT 1");
            rs.next();
            result.put("connected", true);
            result.put("message", "HiveServer2 连接正常");
            log.info("[Hive] 连接测试成功 — {}", jdbcUrl);
        } catch (Exception | NoClassDefFoundError e) {
            result.put("connected", false);
            result.put("message", "连接失败: " + e.getMessage());
            log.error("[Hive] 连接测试失败 — {}", jdbcUrl, e);
        }
        return result;
    }

    /**
     * 创建员工维度表（如果不存在）
     *
     * 对应 HiveQL：
     * CREATE TABLE IF NOT EXISTS dim_employee (
     *     id INT, name STRING, gender STRING, phone STRING,
     *     age INT, salary DOUBLE, status STRING, work_years INT
     * ) PARTITIONED BY (dt STRING)
     * STORED AS PARQUET;
     */
    public void createEmployeeTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS dim_employee (
                id INT,
                name STRING,
                gender STRING,
                phone STRING,
                age INT,
                salary DOUBLE,
                status STRING,
                work_years INT
            )
            PARTITIONED BY (dt STRING)
            STORED AS PARQUET
            """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[Hive] 表 dim_employee 已就绪");
        } catch (SQLException e) {
            log.error("[Hive] 创建表失败", e);
        }
    }

    /**
     * 向 dim_employee 加载数据
     *
     * 真实 Hive 中，INSERT 数据时需要通过临时表或直接写文件的方式。
     * 这里使用 INSERT INTO ... VALUES 逐行插入（适合少量数据演示）。
     */
    public int loadEmployeeData(List<Map<String, Object>> rows, String partition) {
        createEmployeeTable();
        int count = 0;
        String sql = "INSERT INTO dim_employee PARTITION (dt='" + partition + "') VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                pstmt.setObject(1, row.get("id"));
                pstmt.setString(2, (String) row.get("name"));
                pstmt.setString(3, (String) row.get("gender"));
                pstmt.setString(4, (String) row.get("phone"));
                pstmt.setObject(5, row.get("age"));
                pstmt.setObject(6, row.get("salary"));
                pstmt.setString(7, (String) row.get("status"));
                pstmt.setObject(8, row.get("work_years"));
                pstmt.addBatch();
                count++;
            }
            pstmt.executeBatch();
            log.info("[Hive] 向 dim_employee (分区 dt={}) 加载 {} 条数据", partition, count);
        } catch (SQLException e) {
            log.error("[Hive] 数据加载失败", e);
        }
        return count;
    }

    /**
     * 全表扫描查询
     *
     * 对应 HiveQL：SELECT * FROM dim_employee
     */
    public List<Map<String, Object>> queryAll(String tableName) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
            log.info("[Hive] SELECT * FROM {} → 返回 {} 条", tableName, results.size());
        } catch (SQLException e) {
            log.error("[Hive] 查询失败: {}", sql, e);
        }
        return results;
    }

    /**
     * 聚合查询——按列分组统计
     *
     * 对应 HiveQL：SELECT col, COUNT(*) FROM table GROUP BY col
     */
    public Map<String, Long> groupByCount(String tableName, String groupByColumn) {
        Map<String, Long> result = new LinkedHashMap<>();
        String sql = "SELECT " + groupByColumn + ", COUNT(*) as cnt FROM " + tableName
                + " GROUP BY " + groupByColumn;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(String.valueOf(rs.getObject(1)), rs.getLong(2));
            }
            log.info("[Hive] {} → 返回 {} 组", sql, result.size());
        } catch (SQLException e) {
            log.error("[Hive] 聚合查询失败: {}", sql, e);
        }
        return result;
    }

    /**
     * 获取所有表名（对应 SHOW TABLES）
     */
    public List<String> showTables() {
        List<String> tables = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("[Hive] SHOW TABLES 失败", e);
        }
        return tables;
    }

    /**
     * 获取表结构（对应 DESCRIBE tableName）
     */
    public List<Map<String, String>> describeTable(String tableName) {
        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("DESCRIBE " + tableName)) {
            while (rs.next()) {
                Map<String, String> col = new LinkedHashMap<>();
                col.put("name", rs.getString(1));
                col.put("type", rs.getString(2));
                columns.add(col);
            }
        } catch (SQLException e) {
            log.error("[Hive] DESCRIBE {} 失败", tableName, e);
        }
        return columns;
    }

    // ==================== 以下为 P2 阶段新增：分层建模支撑能力 ====================

    /**
     * 将一段脚本切分为单条语句
     *
     * Hive 2.x 的 JDBC 不支持一次执行多条语句，需要自行拆分。
     * 切分时需跳过注释行与空行，且不能把字符串字面量内的分号当作分隔符。
     */
    public List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean inString = false;
        char quote = 0;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (inString) {
                buffer.append(c);
                if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inString = true;
                quote = c;
                buffer.append(c);
                continue;
            }
            if (c == ';') {
                collectStatement(buffer, statements);
                continue;
            }
            buffer.append(c);
        }
        collectStatement(buffer, statements);
        return statements;
    }

    /** 收尾单条语句：去掉注释行与空行后加入列表 */
    private void collectStatement(StringBuilder buffer, List<String> statements) {
        String raw = buffer.toString().trim();
        buffer.setLength(0);
        if (raw.isEmpty()) {
            return;
        }
        StringBuilder kept = new StringBuilder();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            kept.append(line).append("\n");
        }
        String sql = kept.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
    }

    /** 替换脚本中的 ${xxx} 占位符 */
    private String applyVars(String sql, Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) {
            return sql;
        }
        String result = sql;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** 日志输出时截断超长 SQL */
    private String abbreviate(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + " ..." : oneLine;
    }

    /**
     * 在同一个连接内顺序执行整段脚本
     *
     * @param script 脚本内容（可含多条语句、注释、${dt} 占位符）
     * @param vars   占位符替换表，通常为 {"dt": "2026-10-05"}
     * @return 执行报告：语句总数、成功数、失败明细、耗时
     */
    public Map<String, Object> executeScript(String script, Map<String, String> vars) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> statements = splitStatements(script);
        report.put("totalStatements", statements.size());

        List<String> failed = new ArrayList<>();
        int success = 0;
        long start = System.currentTimeMillis();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            for (String raw : statements) {
                String sql = applyVars(raw, vars);
                try {
                    stmt.execute(sql);
                    success++;
                    log.info("[Hive] 执行成功 → {}", abbreviate(sql));
                } catch (SQLException ex) {
                    String msg = abbreviate(sql) + " → " + ex.getMessage();
                    failed.add(msg);
                    log.error("[Hive] 执行失败 → {}", msg);
                }
            }
        } catch (Exception e) {
            report.put("connectionError", e.getMessage());
            log.error("[Hive] 脚本执行中断，连接异常", e);
        }

        report.put("successCount", success);
        report.put("failedCount", failed.size());
        report.put("failedStatements", failed);
        report.put("costMs", System.currentTimeMillis() - start);
        report.put("status", failed.isEmpty() ? "SUCCESS" : "PARTIAL_FAILED");
        return report;
    }

    /**
     * 通用查询：执行任意 HiveQL 并返回结果集
     */
    public List<Map<String, Object>> query(String sql, Map<String, String> vars) {
        List<Map<String, Object>> results = new ArrayList<>();
        String finalSql = applyVars(sql, vars);
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(finalSql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
            log.info("[Hive] 查询返回 {} 条 → {}", results.size(), abbreviate(finalSql));
        } catch (SQLException e) {
            log.error("[Hive] 查询失败: {}", abbreviate(finalSql), e);
        }
        return results;
    }

    /**
     * 通用数据加载：向指定分区批量写入行数据
     *
     * 使用 INSERT INTO ... VALUES 配合 JDBC 批处理，适合小批量数据。
     * 百万级数据应改用「生成文本文件 → LOAD DATA INPATH」方式，避免产生大量小文件。
     *
     * @param table     目标表名
     * @param columns   列顺序（不含分区列）
     * @param rows      行数据，每行元素个数需与 columns 一致
     * @param partition 分区值（dt）
     * @param batchSize 每批提交行数
     */
    public int loadRows(String table, List<String> columns, List<List<Object>> rows,
                        String partition, int batchSize) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + table + " PARTITION (dt='" + partition + "') VALUES ("
                + placeholders + ")";

        int loaded = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int pending = 0;
            for (List<Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    pstmt.setObject(i + 1, i < row.size() ? row.get(i) : null);
                }
                pstmt.addBatch();
                pending++;
                if (pending >= batchSize) {
                    pstmt.executeBatch();
                    loaded += pending;
                    pending = 0;
                }
            }
            if (pending > 0) {
                pstmt.executeBatch();
                loaded += pending;
            }
            log.info("[Hive] 向 {} (分区 dt={}) 加载 {} 条数据", table, partition, loaded);
        } catch (SQLException e) {
            log.error("[Hive] 向 {} 加载数据失败", table, e);
        }
        return loaded;
    }

    /**
     * 清空指定分区（支持重跑）
     */
    public boolean dropPartition(String table, String partition) {
        String sql = "ALTER TABLE " + table + " DROP IF EXISTS PARTITION (dt='" + partition + "')";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[Hive] 已清理分区 {}/dt={}", table, partition);
            return true;
        } catch (SQLException e) {
            log.error("[Hive] 清理分区失败: {}", sql, e);
            return false;
        }
    }
}
