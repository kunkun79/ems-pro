package com.ems.bigdata.scheduler;

import com.ems.bigdata.etl.ETLPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 定时调度任务
 *
 * 替代人工手动点击"执行ETL"按钮，实现数仓的自动化同步。
 * 真实生产环境中，ETL 通常由 Azkaban / Airflow / DolphinScheduler 等调度系统管理，
 * 这里用 Spring @Scheduled 作为轻量级实现。
 */
@Component
public class ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);

    @Autowired
    private ETLPipeline etlPipeline;

    /** 是否启用定时ETL（可通过配置关闭） */
    @Value("${bigdata.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /**
     * 每天凌晨 2:00 自动执行 ETL
     *
     * cron 表达式：秒 分 时 日 月 周
     * "0 0 2 * * ?" = 每天凌晨 2:00:00
     *
     * 测试时可改为 "0 * /5 * * * ?"（每5分钟一次），去掉 * 和 / 之间的空格
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledETL() {
        if (!schedulerEnabled) {
            log.info("[调度器] 定时ETL已禁用（bigdata.scheduler.enabled=false）");
            return;
        }
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("========================================");
        log.info("[调度器] 开始执行定时ETL — {}", time);
        log.info("========================================");
        try {
            Map<String, Object> report = etlPipeline.runEmployeeETL();
            log.info("[调度器] ETL执行完成: 抽取{}条, 加载{}条, 状态={}",
                    report.get("extractCount"), report.get("loadCount"), report.get("status"));
        } catch (Exception e) {
            log.error("[调度器] ETL执行失败", e);
        }
    }
}
