package com.somepro.infrastructure.persistence.rate;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.rate.model.PawnRate;
import com.somepro.domain.rate.model.PawnRateQuery;
import com.somepro.domain.rate.repository.PawnRateRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.rate.converter.RateConfigPoConverter;
import com.somepro.infrastructure.persistence.rate.po.RateConfigPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 费率配置仓储适配器（基础设施层）：MyBatis-Plus 阻塞 JDBC 经 blocking(...) 桥接进响应式链路。
 *
 * 本类两处关键业务语义：
 *
 * 1. 一个类别只准一条配置（含并发）
 *    录入先抢 MySQL 命名锁 GET_LOCK('pawn_rate:write')（全实例互斥），锁内按类别查重再写入 ——
 *    同类别前后脚来两条也只落得了一条，后到者收到的是业务提示而不是底层错。
 *    uk_category 唯一索引是最后防线：撞上 DuplicateKeyException 一律翻译成业务提示，不甩底层错。
 *
 * 2. 修改是单条 UPDATE 整行换值（快照一致性）
 *    开票抄配置读的是单行（RateConfigAdapter 一次 selectOne），所以这里改配置也必须是一条
 *    UPDATE 把月利率/月综合费率/折当率上限/状态整行换值：InnoDB 单行更新是原子的，
 *    开票那一下读到的要么整套是改前的、要么整套是改后的，绝不会一半旧一半新。
 *    绝不能拆成多条 UPDATE、也不能「删了再插」——那会开出「读到半套新配置」的窗口。
 *
 * 锁的连接与时序同当户/当票模块：用一条【独立于事务的原始连接】在事务开启前 GET_LOCK、
 * 在事务【提交之后】才 RELEASE_LOCK，避免「锁已放、事务未提交」导致后到者查重漏看。
 */
@Repository
public class PawnRateRepositoryImpl implements PawnRateRepository {

    /** 费率配置写入临界区命名锁（MySQL 全实例同名互斥）。 */
    private static final String WRITE_LOCK = "pawn_rate:write";
    private static final int LOCK_WAIT_SECONDS = 10;

    private final RateConfigMapper pawnRateMapper;
    private final TransactionTemplate transactionTemplate;
    private final DataSource dataSource;

    public PawnRateRepositoryImpl(RateConfigMapper pawnRateMapper,
                                  PlatformTransactionManager transactionManager,
                                  DataSource dataSource) {
        this.pawnRateMapper = pawnRateMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dataSource = dataSource;
    }

    @Override
    public Mono<PawnRate> insert(PawnRate rate) {
        return blocking(() -> {
            try {
                return inWriteLock(() -> transactionTemplate.execute(status -> {
                    // 一个类别只准一条：锁内查重，同类别前后脚来两条也只落得了一条
                    if (pawnRateMapper.countByCategory(rate.getCategory().code()) > 0) {
                        throw new BizException("该类别（" + rate.getCategory().label()
                                + "）已有费率配置，一个类别只准有一条；要调整请走修改");
                    }
                    RateConfigPO po = RateConfigPoConverter.toPo(rate);
                    po.setId(IdUtil.getSnowflakeNextId());
                    pawnRateMapper.insert(po);
                    return RateConfigPoConverter.toDomain(po);
                }));
            } catch (DuplicateKeyException e) {
                // uk_category 最后防线：锁内已先查，撞唯一键只可能是极端并发或历史脏数据
                throw new BizException("该类别（" + rate.getCategory().label()
                        + "）已有费率配置，一个类别只准有一条；要调整请走修改");
            }
        });
    }

    @Override
    public Mono<PawnRate> update(PawnRate rate) {
        return blocking(() -> {
            RateConfigPO existing = pawnRateMapper.selectById(rate.getId());
            if (existing == null) {
                throw new BizException("费率配置不存在");
            }
            // 类别是配置的标识，永不改；把原值带回，避免被覆盖
            rate.setCategory(Category.valueOf(existing.getCategory()));
            RateConfigPO po = RateConfigPoConverter.toPo(rate);
            // 单条 UPDATE 整行换值：开票读到的费率快照要么整套是改前的、要么整套是改后的
            int rows = pawnRateMapper.updateById(po);
            if (rows == 0) {
                throw new BizException("费率配置不存在");
            }
            RateConfigPO refreshed = pawnRateMapper.selectById(rate.getId());
            return RateConfigPoConverter.toDomain(Objects.requireNonNullElse(refreshed, po));
        });
    }

    @Override
    public Mono<PawnRate> findById(Long id) {
        return blocking(() -> {
            RateConfigPO po = pawnRateMapper.selectById(id);
            return po == null ? null : RateConfigPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PawnRate> findByCategory(Category category) {
        return blocking(() -> {
            RateConfigPO po = pawnRateMapper.selectOne(Wrappers.<RateConfigPO>lambdaQuery()
                    .eq(RateConfigPO::getCategory, category.code()));
            return po == null ? null : RateConfigPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<PawnRate>> page(int pageNum, int pageSize, PawnRateQuery query) {
        return this.<PageResult<PawnRate>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<RateConfigPO> wrapper = Wrappers.<RateConfigPO>lambdaQuery();
                if (query.category() != null) {
                    wrapper.eq(RateConfigPO::getCategory, query.category().code());
                }
                if (query.status() != null) {
                    wrapper.eq(RateConfigPO::getStatus, query.status().code());
                }
                // 稳定排序：两页之间不会出现同一条配置，分页结果可重复对号
                wrapper.orderByAsc(RateConfigPO::getId);
                List<RateConfigPO> rows = pawnRateMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<PawnRate> content = rows.stream()
                        .map(RateConfigPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                // PageHelper 靠 ThreadLocal 传分页参数，必须清，避免污染线程池下一次调用
                PageHelper.clearPage();
            }
        });
    }

    /**
     * 在全局命名锁保护下执行一段【含事务】的写入：锁由一条独立原始连接持有，
     * 在事务开始前 GET_LOCK、在事务提交/回滚之后才 RELEASE_LOCK（顺序不能颠倒）。
     *
     * 为什么锁要走独立连接而不是 MyBatis 连接：GET_LOCK 绑定连接；
     * 若用事务所在连接，Spring 提交时归还连接会立刻放锁，存在「锁已放、事务未提交」的窗口，
     * 后到的事务查重时读不到未提交插入，唯一性会漏。独立连接持锁可把锁保到提交之后。
     */
    private <T> T inWriteLock(Supplier<T> action) {
        Connection lockConn;
        try {
            lockConn = dataSource.getConnection();
        } catch (SQLException e) {
            throw new BizException("系统繁忙，请稍后重试");
        }
        try {
            if (!namedLock(lockConn, true)) {
                throw new BizException("系统繁忙，请稍后重试");
            }
            try {
                return action.get();
            } finally {
                // 此时 action 内的事务已提交（或回滚），放锁后后到者必能看到本次写入
                namedLock(lockConn, false);
            }
        } finally {
            try {
                lockConn.close();
            } catch (SQLException ignored) {
                // 连接关闭会自动释放其上的命名锁，不影响主流程
            }
        }
    }

    /** GET_LOCK / RELEASE_LOCK；返回 MySQL 结果（1 成功）。 */
    private boolean namedLock(Connection conn, boolean get) {
        String sql = get ? "SELECT GET_LOCK(?, ?)" : "SELECT RELEASE_LOCK(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, WRITE_LOCK);
            if (get) {
                ps.setInt(2, LOCK_WAIT_SECONDS);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int r = rs.getInt(1);
                    return !rs.wasNull() && r == 1;
                }
                return false;
            }
        } catch (SQLException e) {
            if (get) {
                throw new BizException("系统繁忙，请稍后重试");
            }
            return false;
        }
    }

    /**
     * 阻塞 DB 调用 → 响应式链路桥接器：先从 Reactor Context 取操作人，再切到 boundedElastic，
     * 操作人放进 AuditContextHolder 供审计填充（与当户/当票模块同一套约定，顺序不能颠倒）。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
