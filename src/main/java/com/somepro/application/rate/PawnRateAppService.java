package com.somepro.application.rate;

import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.rate.model.PawnRate;
import com.somepro.domain.rate.model.PawnRateQuery;
import com.somepro.domain.rate.model.RateStatus;
import com.somepro.domain.rate.repository.PawnRateRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 费率配置应用服务：编排录入、修改、查看、停用、翻配置清单五个用例，不写表映射。
 *
 * 出入参用领域对象/基础类型，不认识 PO 与 VO。
 * 「一个类别只准一条配置」的查重与并发控制在仓储里；单条配置自身的取值规则在 PawnRate 聚合里。
 *
 * 配置是开票的取数源头：开票按类别抄当时生效配置的月利率/月综合费率快照、按折当率上限卡当金。
 * 这里只管配置本身怎么录、怎么改 —— 改完只影响以后新开的票，已经在当的老票照旧按票上快照走，
 * 本服务没有任何回写老票的动作；停用某类别后该类别新开票自然被卡住（开票只取 ENABLED 配置）。
 */
@Service
public class PawnRateAppService {

    private final PawnRateRepository pawnRateRepository;

    public PawnRateAppService(PawnRateRepository pawnRateRepository) {
        this.pawnRateRepository = pawnRateRepository;
    }

    /**
     * 录入：类别只认当物五种，新配默认启用；同类别已有配置（不论启用停用）由仓储挡回。
     * 月利率/月综合费率/折当率上限都写成小数比例（如 0.005 / 0.7），取值区间由聚合校验。
     */
    public Mono<PawnRate> create(String category, String monthlyRate, String serviceRate, String maxLoanRatio) {
        PawnRate rate = PawnRate.create(
                requireCategory(category),
                parseRatio(monthlyRate, "月利率"),
                parseRatio(serviceRate, "月综合费率"),
                parseRatio(maxLoanRatio, "折当率上限"));
        return pawnRateRepository.insert(rate);
    }

    /**
     * 修改：费率三项全量重录（留空不收，要改就整套给齐 —— 三项是同一份配置的同一时点取值，
     * 不允许只换其中一项造成新旧掺半）；status 传 ENABLED/DISABLED 顺带切状态，不传不动状态。
     * 类别是配置的标识，不可改。
     */
    public Mono<PawnRate> update(Long id, String monthlyRate, String serviceRate,
                                 String maxLoanRatio, String status) {
        return requireRate(id).flatMap(rate -> {
            rate.revise(parseRatio(monthlyRate, "月利率"),
                    parseRatio(serviceRate, "月综合费率"),
                    parseRatio(maxLoanRatio, "折当率上限"));
            if (status != null && !status.isBlank()) {
                rate.changeStatus(RateStatus.ofCode(status));
            }
            return pawnRateRepository.update(rate);
        });
    }

    /** 查看：id 或 category 任一指定；按类别查时该类别还没配过就明确告知。 */
    public Mono<PawnRate> detail(Long id, String category) {
        if (id != null) {
            return requireRate(id);
        }
        Category cat = Category.ofCode(blankToNull(category));
        if (cat != null) {
            return pawnRateRepository.findByCategory(cat)
                    .switchIfEmpty(Mono.error(new BizException("该类别（" + cat.label() + "）还没有费率配置")));
        }
        return Mono.error(new BizException("请指定要查看的配置（id 或 category）"));
    }

    /**
     * 停用：该类别新开票随即卡住（开票只取 ENABLED 配置）；已开出的票不受影响。
     * 重复停用由聚合挡回。
     */
    public Mono<PawnRate> disable(Long id) {
        return requireRate(id).flatMap(rate -> {
            rate.disable();
            return pawnRateRepository.update(rate);
        });
    }

    /** 翻配置清单：类别/状态随意拼，都不填翻整份；每行带类别，分页稳定走。 */
    public Mono<PageResult<PawnRate>> page(int pageNum, int pageSize, String category, String status) {
        if (pageNum < 1 || pageSize < 1) {
            return Mono.error(new BizException("页码与每页条数必须为正整数"));
        }
        PawnRateQuery query = PawnRateQuery.of(
                Category.ofCode(blankToNull(category)),
                RateStatus.ofCode(blankToNull(status)));
        return pawnRateRepository.page(pageNum, pageSize, query);
    }

    private Mono<PawnRate> requireRate(Long id) {
        if (id == null) {
            return Mono.error(new BizException("必须指定配置 id"));
        }
        return pawnRateRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("费率配置不存在")));
    }

    private Category requireCategory(String category) {
        Category cat = Category.ofCode(blankToNull(category));
        if (cat == null) {
            throw new BizException("类别不能为空，只支持 JEWELRY / WATCH / ELECTRONICS / VEHICLE / OTHER");
        }
        return cat;
    }

    /** 费率入参解析：必须是数字；空串按「没填」挡回。取值区间（0~1）由聚合在校验阶段把关。 */
    private BigDecimal parseRatio(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(label + "不能为空");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(label + "必须是小数比例（如 0.005）：" + raw);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
