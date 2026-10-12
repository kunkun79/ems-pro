#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
EMS-Pro 员工效能业务数据生成器

生成三张事实表的一年期仿真数据：
  attendance  考勤事实表（投入侧）
  task        任务工单表（产出侧）
  performance 绩效考核表（结果侧）
并可选扩容 employee 员工表（--expand-employees）

数据分布遵循真实业务规律，避免纯随机导致分析结果无意义：
  1. 司龄越长，出勤率越高、迟到率越低
  2. 第四季度加班强度高于第二季度
  3. 技术类部门任务难度偏高、加班偏多
  4. 绩效得分与出勤率、任务完成率正相关（叠加噪声）
  5. 可按比例注入脏数据，用于演示 DQC 数据质量校验

用法：
  python generate_efficiency_data.py                     默认 500 员工 / 一年
  python generate_efficiency_data.py --employees 5000    扩容到 5000 员工（百万级）
  python generate_efficiency_data.py --start-emp-id 71   指定新增员工起始 ID
"""

import argparse
import datetime
import os
import random

# ==================== 基础配置 ====================

SURNAMES = list("王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘于蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤")
GIVEN_NAMES = ["伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "秀兰", "霞",
               "平", "刚", "桂英", "建华", "文博", "思远", "梓涵", "雨欣", "子豪", "嘉怡", "宇轩", "诗琪", "浩然", "若曦", "晨阳", "语嫣",
               "子墨", "欣怡", "博文", "佳怡", "泽宇", "紫萱", "俊熙", "一诺", "书瑶", "沐辰", "亦凡", "知微", "清和", "望舒"]

DEPARTMENTS = [
    (1, "技术部", 1.00, 1.00), (2, "市场部", 0.85, 0.75), (3, "财务部", 0.60, 0.70),
    (4, "人事部", 0.55, 0.65), (5, "运营部", 0.90, 0.80), (6, "产品部", 0.95, 0.85),
    (7, "客服部", 0.70, 0.60), (8, "研发一部", 1.15, 1.10), (9, "研发二部", 1.15, 1.05),
    (10, "测试部", 0.95, 0.90), (11, "运维部", 0.90, 1.00), (12, "法务部", 0.50, 0.65),
    (13, "行政部", 0.45, 0.55), (14, "战略发展部", 0.60, 0.75), (15, "国际业务部", 0.80, 0.85),
]
# 元组含义：(dept_id, dept_name, 任务难度系数, 加班强度系数)

TASK_TYPES = ["需求开发", "缺陷修复", "系统运维", "测试验证", "方案设计", "数据报表", "客户支持"]
LEAVE_TYPES = ["事假", "病假", "年假", "调休"]

# 一年期节假日（近似，用于剔除法定假日）
HOLIDAYS = []
def _add_holiday(start, days):
    d = datetime.date.fromisoformat(start)
    for i in range(days):
        HOLIDAYS.append(d + datetime.timedelta(days=i))

_add_holiday("2025-10-01", 7)   # 国庆
_add_holiday("2026-01-01", 3)   # 元旦
_add_holiday("2026-02-16", 7)   # 春节
_add_holiday("2026-04-04", 3)   # 清明
_add_holiday("2026-05-01", 5)   # 劳动节
_add_holiday("2026-06-19", 3)   # 端午
_add_holiday("2026-09-25", 3)   # 中秋
HOLIDAY_SET = set(HOLIDAYS)


# ==================== 工具函数 ====================

def workdays(start_date, end_date):
    """返回区间内的工作日列表（剔除周末与法定节假日）"""
    days = []
    cur = start_date
    while cur <= end_date:
        if cur.weekday() < 5 and cur not in HOLIDAY_SET:
            days.append(cur)
        cur += datetime.timedelta(days=1)
    return days


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def gauss(mean, std, lo, hi):
    return clamp(random.gauss(mean, std), lo, hi)


def sql_str(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("'", "''") + "'"


class BatchWriter:
    """按批写出 INSERT 语句，避免单条语句过大"""

    def __init__(self, path, table, columns, batch=1000):
        self.path = path
        self.table = table
        self.columns = columns
        self.batch = batch
        self.buf = []
        self.count = 0
        self.f = open(path, "w", encoding="utf-8")
        self.f.write("-- ==========================================\n")
        self.f.write(f"-- {table} 仿真数据（脚本生成，可直接导入 MySQL）\n")
        self.f.write("-- ==========================================\n\n")

    def add(self, values):
        self.buf.append("(" + ", ".join(values) + ")")
        self.count += 1
        if len(self.buf) >= self.batch:
            self.flush()

    def flush(self):
        if not self.buf:
            return
        self.f.write(f"INSERT INTO {self.table} ({', '.join(self.columns)}) VALUES\n")
        self.f.write(",\n".join(self.buf) + ";\n\n")
        self.buf = []

    def close(self):
        self.flush()
        self.f.close()
        return self.count


# ==================== 数据生成 ====================

def gen_existing_profiles(count):
    """为库中已有员工构造画像（仅用于生成事实数据，不写入 employee 表）"""
    profiles = {}
    for emp_id in range(1, count + 1):
        dept_id, _, diff_coef, ot_coef = random.choice(DEPARTMENTS)
        years = random.choices([0.5, 1, 2, 3, 5, 7, 9, 11], weights=[8, 14, 16, 15, 18, 12, 9, 8])[0]
        entry_date = END_DATE - datetime.timedelta(days=int(years * 365))
        profiles[emp_id] = (dept_id, entry_date, diff_coef, ot_coef, 0)
    return profiles


def gen_employees(writer, start_id, total):
    """扩容员工表。返回 emp_id -> (dept_id, entry_date) 映射"""
    profiles = {}
    used_names = set()
    for i in range(total):
        emp_id = start_id + i
        dept_id, dept_name, diff_coef, ot_coef = random.choice(DEPARTMENTS)
        gender = random.choice(["男", "女"])

        while True:
            name = random.choice(SURNAMES) + random.choice(GIVEN_NAMES)
            if name not in used_names:
                used_names.add(name)
                break

        age = int(clamp(random.gauss(31, 6), 22, 55))
        # 入职日期：分布覆盖 1~11 年司龄
        years = random.choices([0.5, 1, 2, 3, 5, 7, 9, 11], weights=[8, 14, 16, 15, 18, 12, 9, 8])[0]
        entry_date = END_DATE - datetime.timedelta(days=int(years * 365))
        base_salary = 9000 + 1500 * (age - 22) / 3 + 4000 * diff_coef
        salary = round(clamp(random.gauss(base_salary, base_salary * 0.22), 4500, 60000), 2)

        phone = "13" + str(random.randint(100000000, 999999999))
        email = f"emp{emp_id}@company.com"
        address = random.choice(["北京市朝阳区", "北京市海淀区", "上海市浦东新区", "上海市静安区", "广州市天河区",
                                 "广州市越秀区", "深圳市南山区", "深圳市福田区", "杭州市西湖区", "成都市武侯区",
                                 "武汉市洪山区", "南京市鼓楼区", "西安市雁塔区", "苏州市工业园区"])
        create_time = entry_date.strftime("%Y-%m-%d") + " 09:00:00"

        writer.add([
            str(emp_id), sql_str(name), str(dept_id), sql_str(gender), str(age),
            sql_str(phone), sql_str(email), sql_str(address), str(salary),
            sql_str(entry_date.strftime("%Y-%m-%d")), sql_str("在职"),
            sql_str(f"emp{emp_id}"), sql_str("123456"), sql_str(create_time), sql_str(create_time),
        ])
        profiles[emp_id] = (dept_id, entry_date, diff_coef, ot_coef, salary)
    return profiles


def gen_attendance(writer, profiles, dirty_rate):
    """生成一年期考勤数据，同时累计 (emp_id, 月份) -> (出勤率, 人均加班)"""
    days = workdays(START_DATE, END_DATE)
    agg = {}
    for emp_id, (dept_id, entry_date, diff_coef, ot_coef, salary) in profiles.items():
        seniority_years = (END_DATE - entry_date).days / 365.0
        # 司龄越长出勤越好
        base_attend = clamp(0.92 + 0.006 * seniority_years, 0.92, 0.995)
        late_prob = clamp(0.13 - 0.008 * seniority_years, 0.02, 0.13)

        effective_days = [d for d in days if d >= entry_date]
        for d in effective_days:
            r = random.random()
            # ---- 判定当日出勤状态 ----
            if r > base_attend + 0.025:
                leave_type = "旷工"
                is_leave, is_absent, is_late = 0, 1, 0
                late_minutes, work_hours, overtime = 0, 0.0, 0.0
                sign_in = sign_out = None
            elif r > base_attend:
                leave_type = random.choice(LEAVE_TYPES)
                is_leave, is_absent, is_late = 1, 0, 0
                late_minutes, work_hours, overtime = 0, 0.0, 0.0
                sign_in = sign_out = None
            else:
                leave_type = "正常"
                is_leave, is_absent = 0, 0
                if random.random() < late_prob:
                    is_late = 1
                    late_minutes = int(gauss(18, 12, 1, 120))
                else:
                    is_late, late_minutes = 0, 0
                # 季度影响：Q4 加班最重，Q2 最轻
                quarter_ot = {1: 1.15, 2: 0.75, 3: 1.0, 4: 1.35}[(d.month - 1) // 3 + 1]
                work_hours = round(clamp(gauss(8.2, 1.1, 4.0, 13.0), 4.0, 13.0), 2)
                ot_prob = clamp(0.28 * ot_coef * quarter_ot, 0.02, 0.75)
                overtime = round(gauss(2.0 * ot_coef * quarter_ot, 1.2, 0, 8.0), 2) if random.random() < ot_prob else 0.0
                sign_h = 8 if not is_late else 8 + late_minutes // 60 + 1
                sign_m = random.randint(0, 59) if not is_late else late_minutes % 60
                sign_in = f"{d.strftime('%Y-%m-%d')} {sign_h:02d}:{sign_m:02d}:00"
                out_h = int(sign_h + work_hours + overtime)
                sign_out = f"{d.strftime('%Y-%m-%d')} {min(out_h, 23):02d}:{random.randint(0, 59):02d}:00"

            # ---- 注入脏数据：异常工时 / 空签到（用于 DQC 演示）----
            if random.random() < dirty_rate:
                work_hours = round(random.uniform(25, 72), 2)
            if random.random() < dirty_rate / 2 and sign_in:
                sign_in = None

            period = d.strftime("%Y-%m")
            rec = agg.setdefault((emp_id, period), [0, 0, 0.0])
            rec[0] += 1
            if leave_type == "正常":
                rec[1] += 1
            rec[2] += overtime

            writer.add([
                str(emp_id),                 sql_str(d.strftime("%Y-%m-%d")), sql_str(sign_in), sql_str(sign_out),
                str(late_minutes), str(overtime), sql_str(leave_type), str(work_hours),
                sql_str(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
            ])

    return {k: (v[1] / v[0] if v[0] else 0.95, v[2] / v[0] if v[0] else 0.0) for k, v in agg.items()}


def gen_tasks(writer, profiles, dirty_rate):
    """生成一年期任务工单数据，同时累计 (emp_id, 月份) -> (完成率, 平均质量分)"""
    agg = {}
    months = []
    y, m = START_DATE.year, START_DATE.month
    while (y, m) <= (END_DATE.year, END_DATE.month):
        months.append((y, m))
        m += 1
        if m > 12:
            m, y = 1, y + 1

    tid = 0
    for emp_id, (dept_id, entry_date, diff_coef, ot_coef, salary) in profiles.items():
        seniority_years = (END_DATE - entry_date).days / 365.0
        # 熟练度：司龄越长完成率越高、质量分越高
        complete_base = clamp(0.72 + 0.02 * seniority_years, 0.72, 0.96)
        quality_base = clamp(7.2 + 0.06 * seniority_years, 7.2, 9.4)

        for (y, m) in months:
            if datetime.date(y, m, 28) < entry_date:
                continue
            per_month = int(gauss(9, 2.5, 3, 18))
            for _ in range(per_month):
                tid += 1
                task_type = random.choice(TASK_TYPES)
                difficulty = int(gauss(3.0 * diff_coef, 1.0, 1, 5))
                last_day = 28
                assign_day = random.randint(1, last_day)
                assign_date = datetime.date(y, m, assign_day)
                if assign_date < entry_date:
                    continue
                due_date = assign_date + datetime.timedelta(days=int(gauss(14, 5, 5, 35)))

                r = random.random()
                if r < complete_base:
                    status = "已完成"
                    # 大部分准时或提前，少数延期完成
                    delta = int(gauss(-1, 5, -10, 12))
                    finish_date = due_date + datetime.timedelta(days=delta)
                    if finish_date > END_DATE:
                        finish_date = END_DATE
                    quality = round(clamp(random.gauss(quality_base, 0.8), 3.0, 10.0), 2)
                elif r < complete_base + 0.06:
                    status = "已延期"
                    finish_date = due_date + datetime.timedelta(days=int(gauss(12, 6, 3, 40)))
                    if finish_date > END_DATE:
                        finish_date = None
                        status = "进行中"
                    quality = round(clamp(random.gauss(quality_base - 1.5, 0.9), 2.0, 10.0), 2)
                elif r < complete_base + 0.10:
                    status = "已取消"
                    finish_date = None
                    quality = 0
                else:
                    status = "进行中"
                    finish_date = None
                    quality = 0

                if random.random() < dirty_rate:
                    quality = round(random.uniform(50, 200), 2)

                period = f"{y}-{m:02d}"
                rec = agg.setdefault((emp_id, period), [0, 0, 0.0, 0])
                rec[0] += 1
                if status == "已完成":
                    rec[1] += 1
                    rec[2] += quality
                    rec[3] += 1

                writer.add([
                    str(emp_id), sql_str(f"T{y}{m:02d}{tid:07d}"), sql_str(task_type), str(difficulty),
                    sql_str(assign_date.strftime("%Y-%m-%d")), sql_str(due_date.strftime("%Y-%m-%d")),
                    sql_str(finish_date.strftime("%Y-%m-%d") if finish_date else None),
                    sql_str(status), str(quality),
                    sql_str(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
                ])

    return {k: (v[1] / v[0] if v[0] else 0.85, v[2] / v[3] if v[3] else 8.0) for k, v in agg.items()}


def gen_performance(writer, profiles, att_stat, task_stat):
    """生成月度绩效考核数据，得分与出勤率、任务完成率正相关"""
    months = []
    y, m = START_DATE.year, START_DATE.month
    while (y, m) <= (END_DATE.year, END_DATE.month):
        months.append((y, m))
        m += 1
        if m > 12:
            m, y = 1, y + 1

    for emp_id, (dept_id, entry_date, diff_coef, ot_coef, salary) in profiles.items():
        seniority_years = (END_DATE - entry_date).days / 365.0
        for (y, m) in months:
            period = f"{y}-{m:02d}"
            if datetime.date(y, m, 28) < entry_date:
                continue
            a = att_stat.get((emp_id, period), (0.95, 0.0))
            t = task_stat.get((emp_id, period), (0.85, 8.0))
            attendance_rate, avg_overtime = a
            complete_rate, avg_quality = t
            avg_quality = clamp(avg_quality, 0.0, 10.0)

            # KPI 得分：完成率 / 出勤率 / 质量分 / 司龄加权，叠加噪声
            score = (0.28 * complete_rate * 100
                     + 0.22 * attendance_rate * 100
                     + 0.25 * (avg_quality / 10.0) * 100
                     + 0.10 * clamp(seniority_years / 10.0, 0, 1) * 100
                     + 0.15 * 80)
            score = round(clamp(random.gauss(score, 4.5), 40, 100), 2)

            if score >= 90:
                grade = "S"
            elif score >= 85:
                grade = "A"
            elif score >= 75:
                grade = "B"
            elif score >= 60:
                grade = "C"
            else:
                grade = "D"

            writer.add([
                str(emp_id), sql_str(period), str(score), sql_str(grade),
                str(random.choice([1, 2, 3])),
                sql_str(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
            ])


# ==================== 主流程 ====================

START_DATE = datetime.date(2025, 10, 1)
END_DATE = datetime.date(2026, 9, 30)


def main():
    ap = argparse.ArgumentParser(description="EMS-Pro 员工效能数据生成器")
    ap.add_argument("--total-employees", type=int, default=500, help="员工总数（含新增）")
    ap.add_argument("--existing-employees", type=int, default=70, help="库中已有员工数")
    ap.add_argument("--start-emp-id", type=int, default=71, help="新增员工起始 ID")
    ap.add_argument("--dirty-rate", type=float, default=0.003, help="脏数据注入比例")
    ap.add_argument("--out-dir", type=str, default=None, help="SQL 输出目录")
    ap.add_argument("--seed", type=int, default=20261005, help="随机种子")
    args = ap.parse_args()

    random.seed(args.seed)

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = args.out_dir or os.path.join(base_dir, "src", "main", "resources", "sql")
    os.makedirs(out_dir, exist_ok=True)

    expand = max(0, args.total_employees - args.existing_employees)
    print(f"[生成] 时间范围 {START_DATE} ~ {END_DATE}")
    print(f"[生成] 新增员工 {expand} 人（起始 ID {args.start_emp_id}）")

    # 1. 员工（现有员工一并纳入造数范围，保证全员都有效能数据）
    profiles = gen_existing_profiles(args.existing_employees) if args.existing_employees > 0 else {}
    if expand > 0:
        w = BatchWriter(os.path.join(out_dir, "efficiency-employee.sql"), "employee",
                        ["id", "name", "dept_id", "gender", "age", "phone", "email", "address",
                         "salary", "entry_date", "status", "username", "password",
                         "create_time", "update_time"])
        profiles.update(gen_employees(w, args.start_emp_id, expand))
        n = w.close()
        print(f"[生成] employee     {n} 条")

    # 2. 考勤
    w = BatchWriter(os.path.join(out_dir, "efficiency-attendance.sql"), "attendance",
                    ["emp_id", "work_date", "sign_in_time", "sign_out_time", "late_minutes",
                     "overtime_hours", "leave_type", "work_hours", "create_time"])
    att_stat = gen_attendance(w, profiles, args.dirty_rate)
    n_att = w.close()
    print(f"[生成] attendance   {n_att} 条")

    # 3. 任务
    w = BatchWriter(os.path.join(out_dir, "efficiency-task.sql"), "task",
                    ["emp_id", "task_code", "task_type", "difficulty", "assign_date", "due_date",
                     "finish_date", "status", "quality_score", "create_time"])
    task_stat = gen_tasks(w, profiles, args.dirty_rate)
    n_task = w.close()
    print(f"[生成] task         {n_task} 条")

    # 4. 绩效（得分与出勤率、任务完成率正相关，叠加噪声）
    w = BatchWriter(os.path.join(out_dir, "efficiency-performance.sql"), "performance",
                    ["emp_id", "period", "kpi_score", "grade", "score_by", "create_time"])
    gen_performance(w, profiles, att_stat, task_stat)
    n_perf = w.close()
    print(f"[生成] performance  {n_perf} 条")
    print(f"[完成] 输出目录 {out_dir}")
    print(f"[完成] 合计 {expand + n_att + n_task + n_perf} 条记录")


if __name__ == "__main__":
    main()
