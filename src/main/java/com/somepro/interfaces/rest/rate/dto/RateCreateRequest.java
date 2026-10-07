package com.somepro.interfaces.rest.rate.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 录入费率配置入参（用户接口层）。
 *
 * 用可变 bean + @ModelAttribute：WebFlux 下 application/x-www-form-urlencoded 表单、
 * query string 都能直接绑定。三个费率值用字符串接收，由应用层解析（非数字给明确业务提示）。
 * 状态不接受外部传入，新配固定 ENABLED。
 */
@Getter
@Setter
public class RateCreateRequest {

    /** 适用类别：JEWELRY / WATCH / ELECTRONICS / VEHICLE / OTHER，别的写法不收。 */
    private String category;

    /** 月利率，小数比例（如 0.005）。 */
    private String monthlyRate;

    /** 月综合费率，小数比例（如 0.02）。 */
    private String serviceRate;

    /** 折当率上限，0~1 小数（如 0.7 表示最多按估值七成放当金）。 */
    private String maxLoanRatio;
}
