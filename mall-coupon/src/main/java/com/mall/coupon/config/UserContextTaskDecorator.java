package com.mall.coupon.config;

import com.mall.coupon.interceptor.CouponInterceptor;
import com.mall.coupon.to.UserInfoTo;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 把 CouponInterceptor 那个 ThreadLocal 里的登录态"搬"到线程池线程上去。
 * ThreadLocal 天生不跨线程——秒杀 grab()/submitAddress() 现在靠 Callable 做异步
 * servlet 处理，真正执行业务逻辑的是 CouponWebConfig.seckillAsyncExecutor 这个池子
 * 里的某个线程，不是发起请求的那个 Tomcat 线程，直接在 Callable 里读 ThreadLocal
 * 只会读到 null。
 * <p>
 * 挂在 ThreadPoolTaskExecutor.setTaskDecorator 上之后，Spring 在真正提交任务
 * （execute/submit 都会走到这里，已经反编译确认过）之前，先在"当前线程"（也就是
 * 发起异步请求的那个 Tomcat 线程）上调用 decorate() 把登录态拍个快照，
 * 再包一层：线程池线程真正跑任务之前先把这份快照 set 回它自己的 ThreadLocal，
 * 跑完/异常都要 remove 掉，不然这个线程池线程以后处理别的用户的任务时会带着
 * 上一个人的登录态。
 * <p>
 * 好处是业务代码（包括以后新增的、需要读登录态的其他异步接口）完全不用关心
 * "隔着一层线程池"这件事，跟同步请求里一样直接读 CouponInterceptor.threadLocal
 * 就行——不用像最开始那版一样，把 memberId/username 一个个手动从 controller
 * 提前拿出来再传进 Callable：以后这里如果要读的字段变多，也不用每加一个字段
 * 就在每个异步接口里多写一行"提前搬运"的代码。
 */
public class UserContextTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        UserInfoTo snapshot = CouponInterceptor.threadLocal.get();
        return () -> {
            try {
                if (snapshot != null) {
                    CouponInterceptor.threadLocal.set(snapshot);
                }
                runnable.run();
            } finally {
                CouponInterceptor.threadLocal.remove();
            }
        };
    }
}
