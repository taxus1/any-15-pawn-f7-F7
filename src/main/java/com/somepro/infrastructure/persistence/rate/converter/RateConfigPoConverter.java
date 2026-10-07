package com.somepro.infrastructure.persistence.rate.converter;

import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.rate.model.PawnRate;
import com.somepro.domain.rate.model.RateStatus;
import com.somepro.infrastructure.persistence.rate.po.RateConfigPO;

/**
 * RateConfigPO（表）↔ PawnRate（领域）转换器（基础设施层），PO 不外泄。
 * category / status 列存枚举名，读出时用 valueOf 还原；库里的值受写入端约束，必为合法值之一。
 */
public final class RateConfigPoConverter {

    private RateConfigPoConverter() {
    }

    public static RateConfigPO toPo(PawnRate domain) {
        RateConfigPO po = new RateConfigPO();
        po.setId(domain.getId());
        po.setCategory(domain.getCategory() == null ? null : domain.getCategory().code());
        po.setMonthlyRate(domain.getMonthlyRate());
        po.setServiceRate(domain.getServiceRate());
        po.setMaxLoanRatio(domain.getMaxLoanRatio());
        po.setStatus(domain.getStatus() == null ? null : domain.getStatus().code());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PawnRate toDomain(RateConfigPO po) {
        PawnRate domain = new PawnRate();
        domain.setId(po.getId());
        domain.setCategory(po.getCategory() == null ? null : Category.valueOf(po.getCategory()));
        domain.setMonthlyRate(po.getMonthlyRate());
        domain.setServiceRate(po.getServiceRate());
        domain.setMaxLoanRatio(po.getMaxLoanRatio());
        domain.setStatus(po.getStatus() == null ? null : RateStatus.valueOf(po.getStatus()));
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
