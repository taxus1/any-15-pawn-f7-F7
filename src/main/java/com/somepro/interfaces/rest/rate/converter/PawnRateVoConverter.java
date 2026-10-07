package com.somepro.interfaces.rest.rate.converter;

import com.somepro.domain.rate.model.PawnRate;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.rate.vo.PawnRateVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 费率配置领域对象 → VO 转换器（用户接口层）。Controller 不直接把领域对象塞进 Result。
 */
public final class PawnRateVoConverter {

    private PawnRateVoConverter() {
    }

    public static PawnRateVO toVo(PawnRate domain) {
        return new PawnRateVO(
                domain.getId(),
                domain.getCategory() == null ? null : domain.getCategory().code(),
                domain.getMonthlyRate(),
                domain.getServiceRate(),
                domain.getMaxLoanRatio(),
                domain.getStatus() == null ? null : domain.getStatus().code(),
                domain.getCreateTime(),
                domain.getUpdateTime());
    }

    public static PageVO<PawnRateVO> toPageVo(PageResult<PawnRate> page) {
        List<PawnRateVO> content = page.content().stream()
                .map(PawnRateVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
