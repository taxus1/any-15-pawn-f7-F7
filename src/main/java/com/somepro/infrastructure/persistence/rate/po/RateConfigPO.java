package com.somepro.infrastructure.persistence.rate.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * t_pawn_rate 表的持久化对象（PO，基础设施层）：费率配置模块管这张表的录入/修改/停用，
 * 全列读写。与当票模块只读的 PawnRatePO 各管各的视角（那边只查「该类别生效的那一行」抄快照；
 * 类名错开成 RateConfigPO 也是为避免两个同名 Mapper 在 Spring 里撞 bean 名）。
 * 本类不做任何建表/改表动作。
 */
@Getter
@Setter
@TableName("t_pawn_rate")
public class RateConfigPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("category")
    private String category;

    @TableField("monthly_rate")
    private BigDecimal monthlyRate;

    @TableField("service_rate")
    private BigDecimal serviceRate;

    @TableField("max_loan_ratio")
    private BigDecimal maxLoanRatio;

    @TableField("status")
    private String status;
}
