package com.somepro.domain.rate.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 费率配置聚合根（纯领域对象，不带任何持久化注解）。
 *
 * 一条配置对着一个类别，是开当票时取数的源头：开票拿该类别的启用配置去卡折当率、
 * 抄月利率与月综合费率快照。核心不变量：
 * 1. 类别只认当物那五种（{@link Category}），进入本聚合前已由 {@code Category.ofCode} 挡过一轮；
 * 2. 月利率、月综合费率写成小数比例（0~1，如 0.005 表示月息千分之五），负数和大于 1 一律不收；
 * 3. 折当率上限写成 0~1 的小数（如 0.7 就是最多按估值的七成放当金），
 *    必须大于 0、不超过 1 —— 写成 0 等于这个类别一两也放不出去，等于没配；
 * 4. 新配的默认启用（ENABLED）；已停用的配置不能再改状态之外重复停用。
 *
 * 「一个类别只该有一条还启用的配置」是跨聚合的唯一性，由仓储在写锁内落库时保证
 * （uk_category 唯一索引兜底），本聚合只管单条配置自身的规则。
 *
 * 配置改完只影响以后新开的票：已在当的老票按票上快照走，本聚合的任何修改都不回写老票。
 */
@Getter
@Setter
public class PawnRate extends BaseEntity {

    private Long id;

    /** 适用类别：当物五类之一，落库存枚举名；配置的标识，一经建立不可改。 */
    private Category category;

    /** 月利率，小数比例（0~1）。 */
    private BigDecimal monthlyRate;

    /** 月综合费率，小数比例（0~1）。 */
    private BigDecimal serviceRate;

    /** 折当率上限（0~1）：当金不得超过估值 × 该值。 */
    private BigDecimal maxLoanRatio;

    private RateStatus status;

    /**
     * 工厂方法：新配费率。默认启用（ENABLED），状态不接受外部指定。
     * 「同类别只准一条」的查重不在这里（跨聚合查库），由仓储在写锁内挡回。
     */
    public static PawnRate create(Category category, BigDecimal monthlyRate,
                                  BigDecimal serviceRate, BigDecimal maxLoanRatio) {
        PawnRate rate = new PawnRate();
        rate.applyCategory(category);
        rate.applyRates(monthlyRate, serviceRate, maxLoanRatio);
        rate.status = RateStatus.ENABLED;
        return rate;
    }

    /**
     * 修改费率三项（全量）。类别是配置的标识，不可改；状态要走启用/停用语义。
     * 停用的配置也改得动：改完再启用，不影响「停用期间该类别开不了票」的口径。
     */
    public void revise(BigDecimal monthlyRate, BigDecimal serviceRate, BigDecimal maxLoanRatio) {
        applyRates(monthlyRate, serviceRate, maxLoanRatio);
    }

    /** 在 ENABLED / DISABLED 之间切换；仅应用层在明确「启用/停用」语义时使用。 */
    public void changeStatus(RateStatus target) {
        if (target == null) {
            throw new BizException("状态不能为空");
        }
        this.status = target;
    }

    /** 停用：该类别新开票随即卡住（开票只取 ENABLED 配置）；已在当的老票按票上快照照旧。 */
    public void disable() {
        if (this.status == RateStatus.DISABLED) {
            throw new BizException("该类别费率配置已停用，不能重复停用");
        }
        this.status = RateStatus.DISABLED;
    }

    public boolean isEnabled() {
        return status == RateStatus.ENABLED;
    }

    private void applyCategory(Category category) {
        if (category == null) {
            throw new BizException("类别不能为空，只支持 JEWELRY / WATCH / ELECTRONICS / VEHICLE / OTHER");
        }
        this.category = category;
    }

    /** 公共赋值逻辑：统一做必填/区间校验。 */
    private void applyRates(BigDecimal monthlyRate, BigDecimal serviceRate, BigDecimal maxLoanRatio) {
        requireRatio(monthlyRate, "月利率");
        requireRatio(serviceRate, "月综合费率");
        if (maxLoanRatio == null) {
            throw new BizException("折当率上限不能为空");
        }
        if (maxLoanRatio.compareTo(BigDecimal.ZERO) <= 0
                || maxLoanRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException("折当率上限必须是 0 到 1 之间的小数（如 0.7 表示最多按估值七成放当金）："
                    + maxLoanRatio.toPlainString());
        }
        this.monthlyRate = monthlyRate;
        this.serviceRate = serviceRate;
        this.maxLoanRatio = maxLoanRatio;
    }

    /** 利率/费率必须是小数比例：0~1，负数与大于 1 一律不收。 */
    private static void requireRatio(BigDecimal value, String label) {
        if (value == null) {
            throw new BizException(label + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(label + "必须是 0 到 1 之间的小数比例（如 0.005 表示千分之五）："
                    + value.toPlainString());
        }
    }
}
