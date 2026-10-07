package com.somepro.domain.rate.model;

import com.somepro.common.exception.BizException;

/**
 * 费率配置状态（纯领域枚举，不依赖任何框架）。
 * <ul>
 *   <li>{@link #ENABLED} 启用：新配的默认落这个状态，开票/改当金按它取数</li>
 *   <li>{@link #DISABLED} 停用：不再参与开票取数，该类别新开票被卡住；已在当的老票不受影响</li>
 * </ul>
 * 用枚举名落库（status 列直接存这些字符串）。
 */
public enum RateStatus {

    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "停用");

    private final String code;
    private final String label;

    RateStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 由外部传入值解析枚举：只认 ENABLED / DISABLED（大小写敏感，列里就是这么存的）。
     * 传 null 返回 null（翻配置时表示不按状态筛）；传空串或其它写法都算非法入参，直接挡回。
     */
    public static RateStatus ofCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (RateStatus status : values()) {
            if (status.code.equals(trimmed)) {
                return status;
            }
        }
        throw new BizException("状态只支持 ENABLED / DISABLED：" + code);
    }
}
