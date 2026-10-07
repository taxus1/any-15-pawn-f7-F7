package com.somepro.infrastructure.persistence.rate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.rate.po.RateConfigPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 费率配置表 Mapper（基础设施层）：费率配置模块的录入/修改/停用/翻清单都走它。
 *
 * BaseMapper 覆盖常规 CRUD；「同类别查重」是业务语义检查，用注解 SQL 写死，不建 XML。
 * 写临界区的命名锁不走 MyBatis（要用独立于事务的连接持锁），见 PawnRateRepositoryImpl#inWriteLock。
 *
 * 阻塞 JDBC API，只能在仓储适配器的 blocking(...) 桥接里调用。
 */
@Mapper
public interface RateConfigMapper extends BaseMapper<RateConfigPO> {

    /**
     * 该类别名下未删除的配置条数（业务唯一性检查：一个类别只准有一条配置）。
     * 只在写锁内调用：> 0 就挡回。uk_category 唯一索引是最后防线。
     */
    @Select("SELECT COUNT(*) FROM t_pawn_rate WHERE category = #{category} AND del_flag = 0")
    long countByCategory(@Param("category") String category);
}
