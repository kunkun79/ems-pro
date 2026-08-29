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
}
