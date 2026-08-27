package com.mall.coupon.config;

import com.mall.coupon.interceptor.CouponInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配。
 *
 * <h3>这里原来有一整套异步 servlet 的机制，阶段 8 换成虚拟线程之后全部删掉了</h3>
 * 删掉的三样东西和它们当初存在的理由：
 * <ol>
 *   <li><b>有界线程池 seckillAsyncExecutor</b>（core 50 / max 200 / queue 2000）——
 *       秒杀 grab()/submitAddress() 内部要等 RabbitMQ 的 publisher confirm（最多 3 秒），
 *       当时不想让 Tomcat 那 200 个平台线程被这种等待占满。</li>
 *   <li><b>Callable 返回值 + configureAsyncSupport</b> —— 让 Spring MVC 走异步 servlet 处理，
 *       把等待挪到上面那个池子里。</li>
 *   <li><b>UserContextTaskDecorator</b> —— 因为业务真正执行在池子的线程上而不是请求线程上，
 *       ThreadLocal 里的登录态需要被显式"搬"过去。</li>
 * </ol>
 * 注意这三样是【一条因果链】：为了不阻塞平台线程而引入线程池，为了用上线程池而引入
 * 异步 servlet，为了跨线程还能读到登录态而引入 TaskDecorator。
 * 开启虚拟线程（spring.threads.virtual.enabled，见 application.yml）之后，
 * 链条的第一环就不成立了 —— 每个请求跑在自己的虚拟线程上，阻塞时它会从载体线程上卸载，
 * 不占用任何稀缺资源。第一环消失，后两环也就没有存在的理由，三个机制一起收敛成
 * 「直接写同步代码」。
 * <p>
 * 这是虚拟线程真正的价值：不是"更快"，而是让"为了不阻塞而做的复杂设计"变得不必要。
 * 少了一层线程切换，栈跟踪也重新变成完整的一条，排查问题容易得多。
 *
 * <h3>但有一件事不能跟着一起删：限流</h3>
 * 那个有界线程池除了"别占着 Tomcat 线程"之外，还在【隐式承担并发限流】—— 在途超过
 * 2200 个就拒绝。虚拟线程把这个天花板拿掉了，如果只是删掉线程池而不补上限流，
 * 瓶颈会下移到数据库连接池，失败模式从"干净地拒绝"退化成"集体卡在获取连接然后一起超时"。
 * 所以补了一个显式的 {@link SeckillBulkhead}，把原本藏在线程池尺寸里的限流变成
 * 一个有名字、可单独调整的东西。详见那个类的注释。
 */
@Configuration
public class CouponWebConfig implements WebMvcConfigurer {

    @Autowired
    @NonNull
    private CouponInterceptor couponInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(couponInterceptor).addPathPatterns("/**");
    }
}
