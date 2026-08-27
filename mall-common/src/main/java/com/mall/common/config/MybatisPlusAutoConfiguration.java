package com.mall.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 统一注册 MyBatis-Plus 的分页拦截器。
 *
 * <h3>为什么这件事必须放在 mall-common 里，而不是各服务自己配</h3>
 * MyBatis-Plus 的 {@code selectPage} <b>只有在注册了 PaginationInnerInterceptor 之后才真正分页</b>。
 * 没注册的话它不会报错，而是：
 * <ul>
 *   <li>把<b>全表</b>记录都查出来（LIMIT 根本没拼进 SQL），</li>
 *   <li>并且把 <b>total 固定返回 0</b>。</li>
 * </ul>
 * 前端的表现就是「列表里明明有数据，底下却写着共 0 条」，翻页按钮也全是坏的。
 * 数据量小的时候几乎看不出来，数据量一大就变成一次全表扫描 —— 属于会随数据增长
 * 突然恶化的那种问题。
 * <p>
 * 实际情况是：本仓库 6 个用 MyBatis-Plus 的服务里，只有 mall-product 配了这个拦截器，
 * 另外 5 个（mall-ware / mall-order / mall-member / mall-coupon / mall-admin）全都漏了。
 * 这说明「让每个服务各自记得配」这个方案本身是不可靠的 —— 写新服务的人不会知道有这回事，
 * 而漏配又没有任何提示。所以改成在 mall-common 里做成自动配置，
 * <b>只要依赖了 mall-common 就自动生效，没有忘记的余地</b>。
 *
 * <h3>两个条件注解都是必需的</h3>
 * <ul>
 *   <li>{@code @ConditionalOnClass}：mall-search / mall-thirdparty 这类不连关系型数据库的服务
 *       把 MyBatis-Plus 从 mall-common 的传递依赖里排掉了，classpath 上没有这个类。
 *       不加这个条件，那两个服务会在启动时抛 ClassNotFoundError。</li>
 *   <li>{@code @ConditionalOnMissingBean}：mall-product 已经有自己的
 *       {@code MybatisPlusConfig}（它同时还做 @MapperScan），保留它、让它优先。
 *       这样这次改动对 mall-product 是零影响，不需要去动一个本来就正确的服务。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(PaginationInnerInterceptor.class)
public class MybatisPlusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mallMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 显式指定 MySQL：不指定的话 MyBatis-Plus 会在运行时按连接去猜数据库类型，
        // 多一次不必要的探测，而本项目的数据库是确定的。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
