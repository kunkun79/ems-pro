package com.ems.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果类
 *
 * 【学习要点】
 * 前后端分离/RESTful API 的最佳实践：所有接口返回统一格式
 * { "code": 200, "message": "操作成功", "data": {...} }
 *
 * 这样做的好处：
 * 1. 前端不需要为每个接口写不同的解析逻辑
 * 2. 可以统一处理错误
 * 3. 方便后续做接口监控和日志
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 状态码：200成功，其他为错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 返回数据（泛型，可以是任何类型） */
    private T data;

    // ========== 快捷静态方法 ==========

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
