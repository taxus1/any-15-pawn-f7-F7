package com.somepro.domain.rate.repository;

import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.rate.model.PawnRate;
import com.somepro.domain.rate.model.PawnRateQuery;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 费率配置聚合的仓储端口（领域层定义，基础设施层实现）。
 *
 * 「一个类别只该有一条还启用的配置」由 {@link #insert} 的实现保证：
 * 写入临界区由 MySQL 命名锁在全实例串行化，锁内按类别查重再写入，
 * uk_category 唯一索引是最后防线 —— 同类别前后脚来两条也只落得了一条。
 */
public interface PawnRateRepository {

    /**
     * 新配费率：该类别已有配置（不论启用停用）时抛业务异常挡回，要调整请走修改。
     * 主键由应用侧雪花分配。
     */
    Mono<PawnRate> insert(PawnRate rate);

    /**
     * 修改配置（费率三项全量，可含状态切换）：单条 UPDATE 整行换值 ——
     * 与开票读配置（单行查询）配对，保证「改配置 vs 开票」前后脚时，
     * 票上抄到的月利率/月综合费率要么整套是改前的、要么整套是改后的，
     * 不会一半旧一半新。类别是配置的标识，不可改。
     */
    Mono<PawnRate> update(PawnRate rate);

    Mono<PawnRate> findById(Long id);

    /** 按类别查（不论启用停用）：一个类别只有一条，查到就是它那一条。 */
    Mono<PawnRate> findByCategory(Category category);

    /** 按类别/状态翻配置清单；条件全 null 翻整份。 */
    Mono<PageResult<PawnRate>> page(int pageNum, int pageSize, PawnRateQuery query);
}
