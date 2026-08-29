package com.ems.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询参数（工具类）
 *
 * 【学习要点】
 * 分页是最常见的查询需求。当前页码从1开始，每页默认10条。
 *
 * 当前控制器中直接在方法参数上用 @RequestParam 接收分页参数（更简洁），
 * 但如果你想让前端传一个 JSON 对象过来，可以用这个类接收：
 *
 *   @PostMapping("/search")
 *   public Result<?> search(@RequestBody PageQuery query) { ... }
 *
 * 两种方式都行，看你习惯。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageQuery {

    /** 当前页码（从1开始） */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;

    /** 搜索关键词 */
    private String keyword;
}
