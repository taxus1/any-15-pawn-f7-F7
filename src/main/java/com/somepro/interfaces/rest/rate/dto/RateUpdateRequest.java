package com.somepro.interfaces.rest.rate.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改费率配置入参（用户接口层）。
 *
 * 费率三项全量重录（要改就整套给齐，三项是同一份配置的同一时点取值）；
 * status 传 ENABLED/DISABLED 顺带切状态，不传则不动状态。类别是配置的标识，不可改。
 */
@Getter
@Setter
public class RateUpdateRequest {

    private Long id;

    private String monthlyRate;

    private String serviceRate;

    private String maxLoanRatio;

    /** 可选：ENABLED 启用 / DISABLED 停用；不传不动状态。 */
    private String status;
}
