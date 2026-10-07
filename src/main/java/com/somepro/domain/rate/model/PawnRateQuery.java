package com.somepro.domain.rate.model;

import com.somepro.domain.collateral.model.Category;

/**
 * 费率配置翻清单条件（不可变值对象）。
 *
 * 类别、状态随意拼，任一项为 null 即不参与过滤；全 null 翻整份清单。
 * 类别/状态在进入本对象前由各自的 {@code ofCode} 解析过，非法写法已在解析阶段挡回。
 */
public record PawnRateQuery(Category category,
                            RateStatus status) {

    public static PawnRateQuery of(Category category, RateStatus status) {
        return new PawnRateQuery(category, status);
    }
}
