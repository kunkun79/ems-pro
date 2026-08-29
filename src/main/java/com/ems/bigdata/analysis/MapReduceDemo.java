package com.ems.bigdata.analysis;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MapReduce 演示 —— 教你理解大数据计算模型
 *
 * 【学习要点——MapReduce是什么？】
 * MapReduce 是 Hadoop 的核心计算模型，分两个阶段：
 *
 * Map（映射）：把输入数据分解成 (key, value) 对
 *   例如：["张三 技术部", "李四 市场部", "王五 技术部"]
 *   → ("技术部", 1), ("市场部", 1), ("技术部", 1)
 *
 * Reduce（归约）：把相同key的value聚合
 *   → ("技术部", 2), ("市场部", 1)
 *
 * 在大数据中，Map和Reduce在多台机器上并行执行。
 * 这个Demo在单机上模拟这个过程，让你理解核心思想。
 */
public class MapReduceDemo {

    /**
     * WordCount — MapReduce的"Hello World"
     *
     * 统计一段文本中每个单词出现的次数。
     * 这是大数据入门的经典案例。
     */
    public static Map<String, Integer> wordCount(List<String> lines) {
        // ====== Phase 1: MAP（映射） ======
        // 把每行文本拆成单词，输出 (word, 1)
        List<Map.Entry<String, Integer>> mapped = new ArrayList<>();
        for (String line : lines) {
            String[] words = line.split("[\\s,，。.!！?？;；:：]+");
            for (String word : words) {
                if (!word.isEmpty()) {
                    mapped.add(new AbstractMap.SimpleEntry<>(word, 1));
                }
            }
        }
        System.out.println("[MapReduce] Map阶段完成：生成了 " + mapped.size() + " 个键值对");

        // ====== Shuffle（洗牌）—— 按key分组 ======
        Map<String, List<Integer>> grouped = new HashMap<>();
        for (Map.Entry<String, Integer> entry : mapped) {
            grouped.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }
        System.out.println("[MapReduce] Shuffle阶段完成：分组为 " + grouped.size() + " 个key");

        // ====== Phase 2: REDUCE（归约） ======
        // 对每个key的value列表求和
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            int sum = entry.getValue().stream().mapToInt(Integer::intValue).sum();
            result.put(entry.getKey(), sum);
        }
        System.out.println("[MapReduce] Reduce阶段完成：得到 " + result.size() + " 个结果");

        return result;
    }

    /**
     * 员工薪资按部门汇总 — 贴合实际业务的MapReduce例子
     */
    public static Map<String, Double> salarySumByDept(List<Map<String, Object>> employees) {
        // Map: (dept, salary)
        List<AbstractMap.SimpleEntry<String, Double>> mapped = new ArrayList<>();
        for (Map<String, Object> emp : employees) {
            String dept = String.valueOf(emp.getOrDefault("dept", "未知"));
            Double salary = toDouble(emp.get("salary"));
            mapped.add(new AbstractMap.SimpleEntry<>(dept, salary));
        }

        // Shuffle + Reduce: 按部门求和
        Map<String, Double> result = new LinkedHashMap<>();
        for (AbstractMap.SimpleEntry<String, Double> entry : mapped) {
            result.merge(entry.getKey(), entry.getValue(), Double::sum);
        }

        System.out.println("[MapReduce] 薪资按部门汇总完成：处理了 " + employees.size() + " 名员工");
        return result;
    }

    /**
     * 各部门平均年龄 — 稍微复杂一点的MapReduce
     * 输出两个值 (总和, 计数)，Reduce阶段计算平均值
     */
    public static Map<String, Double> avgAgeByDept(List<Map<String, Object>> employees) {
        // Map: (dept, age)
        List<AbstractMap.SimpleEntry<String, Integer>> mapped = new ArrayList<>();
        for (Map<String, Object> emp : employees) {
            String dept = String.valueOf(emp.getOrDefault("dept", "未知"));
            Integer age = toInt(emp.get("age"));
            mapped.add(new AbstractMap.SimpleEntry<>(dept, age));
        }

        // Shuffle + Reduce: 按部门求平均值
        Map<String, List<Integer>> grouped = new HashMap<>();
        for (AbstractMap.SimpleEntry<String, Integer> entry : mapped) {
            grouped.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
            result.put(entry.getKey(), Math.round(avg * 10.0) / 10.0); // 保留1位小数
        }

        System.out.println("[MapReduce] 平均年龄计算完成");
        return result;
    }

    // ========== 辅助方法 ==========
    private static double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return 0;
    }

    private static int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        return 0;
    }
}
