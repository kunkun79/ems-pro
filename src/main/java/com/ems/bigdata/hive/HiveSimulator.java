package com.ems.bigdata.hive;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Hive 数据仓库模拟器
 *
 * 【学习要点——Hive是什么？】
 * Hive 是建立在 Hadoop 上的数据仓库工具，提供类SQL的查询语言（HiveQL），
 * 底层将SQL转换为MapReduce/Spark任务执行。
 *
 * 这个类模拟了Hive的核心概念：
 * 1. 数据以文件形式存储在本地磁盘（对应Hive的外部表）
 * 2. 提供 createTable / insert / select 等类SQL操作
 * 3. 数据分区（partition）按日期存储
 *
 * 真实Hive架构：
 * ┌──────────────┐
 * │  HiveQL (SQL) │  ← 用户写SQL
 * ├──────────────┤
 * │  Hive 引擎    │  ← 解析/优化/执行
 * ├──────────────┤
 * │  MapReduce    │  ← 分布式计算
 * ├──────────────┤
 * │  HDFS         │  ← 分布式存储
 * └──────────────┘
 */
@Component
public class HiveSimulator {

    /** 数据仓库根目录（模拟HDFS） */
    private static final String WAREHOUSE = "data/hive/warehouse";

    /** 表元数据（表名 → 列定义） */
    private final Map<String, List<String>> tableSchema = new LinkedHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建表（模拟 CREATE TABLE 语句）
     *
     * @param tableName 表名
     * @param columns   列名列表，如 ["id", "name", "age", "salary"]
     * @param partitionBy 分区列（如 "dt"，按日期分区）
     */
    public void createTable(String tableName, List<String> columns, String partitionBy) {
        tableSchema.put(tableName, columns);
        // 创建表目录（模拟HDFS上的目录结构）
        File tableDir = new File(WAREHOUSE + "/" + tableName);
        if (!tableDir.exists()) {
            tableDir.mkdirs();
        }
        System.out.println("[Hive] 创建表 " + tableName + "(" + String.join(", ", columns) + ")");
        System.out.println("[Hive] 分区列：" + partitionBy);
        System.out.println("[Hive] 存储路径：hdfs://localhost:9000/user/hive/warehouse/" + tableName);
    }

    /**
     * 插入数据（模拟 INSERT INTO 语句）
     *
     * 数据以JSON行格式存储到分区文件中
     */
    public void insert(String tableName, List<Map<String, Object>> rows, String partition) {
        List<String> columns = tableSchema.get(tableName);
        if (columns == null) {
            System.out.println("[Hive] 表 " + tableName + " 不存在！");
            return;
        }

        // 分区目录：tableName/dt=2024-01-01/
        String partitionDir = WAREHOUSE + "/" + tableName + "/dt=" + partition;
        File dir = new File(partitionDir);
        if (!dir.exists()) dir.mkdirs();

        // 写入数据文件（模拟HDFS上的文件块）
        try {
            String filePath = partitionDir + "/data_" + UUID.randomUUID().toString().substring(0, 8) + ".json";
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
                for (Map<String, Object> row : rows) {
                    writer.write(objectMapper.writeValueAsString(row));
                    writer.newLine();
                }
            }
            System.out.println("[Hive] 向 " + tableName + " (分区 dt=" + partition + ") 插入 " + rows.size() + " 条数据");
            System.out.println("[Hive] 写入文件：" + filePath);
        } catch (IOException e) {
            System.err.println("[Hive] 写入失败：" + e.getMessage());
        }
    }

    /**
     * 全表扫描查询（模拟 SELECT * FROM table）
     */
    public List<Map<String, Object>> selectAll(String tableName) {
        List<Map<String, Object>> results = new ArrayList<>();
        File tableDir = new File(WAREHOUSE + "/" + tableName);
        if (!tableDir.exists()) return results;

        // 扫描所有分区
        File[] partitions = tableDir.listFiles(File::isDirectory);
        if (partitions == null) return results;

        for (File partition : partitions) {
            File[] dataFiles = partition.listFiles(f -> f.getName().endsWith(".json"));
            if (dataFiles == null) continue;
            for (File file : dataFiles) {
                try {
                    List<String> lines = FileUtil.readLines(file, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        if (StrUtil.isNotBlank(line)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> row = objectMapper.readValue(line, Map.class);
                            results.add(row);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[Hive] 读取文件失败：" + file.getPath());
                }
            }
        }
        System.out.println("[Hive] SELECT * FROM " + tableName + " → 返回 " + results.size() + " 条");
        return results;
    }

    /**
     * 聚合查询（模拟 SELECT dept, COUNT(*) FROM emp GROUP BY dept）
     */
    public Map<String, Long> groupByCount(String tableName, String groupByColumn) {
        List<Map<String, Object>> allRows = selectAll(tableName);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : allRows) {
            String key = String.valueOf(row.getOrDefault(groupByColumn, "未知"));
            result.merge(key, 1L, Long::sum);
        }
        System.out.println("[Hive] SELECT " + groupByColumn + ", COUNT(*) FROM " + tableName
                + " GROUP BY " + groupByColumn);
        return result;
    }

    /** 显示所有表 */
    public Set<String> showTables() {
        return tableSchema.keySet();
    }

    /** 显示表结构 */
    public List<String> describeTable(String tableName) {
        return tableSchema.get(tableName);
    }
}
