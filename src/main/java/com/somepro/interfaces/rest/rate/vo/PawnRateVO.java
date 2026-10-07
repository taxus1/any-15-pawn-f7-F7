package com.somepro.interfaces.rest.rate.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费率配置对外对象（不可变 record）：录入/修改/查看/停用/翻清单共用。
 *
 * 每行都带类别，方便柜台按当物类别对号；updateTime 一并放出，费率什么时候改的看得见。
 * 刻意不暴露 delFlag / createBy / updateBy 等内部字段。
 */
public record PawnRateVO(Long id,
                         String category,
                         BigDecimal monthlyRate,
                         BigDecimal serviceRate,
                         BigDecimal maxLoanRatio,
                         String status,
                         LocalDateTime createTime,
                         LocalDateTime updateTime) implements Serializable {
}
