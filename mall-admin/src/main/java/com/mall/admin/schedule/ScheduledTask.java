package com.mall.admin.schedule;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 Spring bean 可以被后台的定时任务调用。
 *
 * <h3>为什么需要这个注解 —— 这是对 renren 原实现的一处安全修正</h3>
 * renren 的定时任务是「填一个 bean 名 + 一个字符串参数，到点用反射调它的 run 方法」。
 * 也就是说<b>任何能创建定时任务的人，都能调用容器里任意一个 bean 的方法</b>。
 * 后台账号被拿到、或者存在一个能创建任务的越权入口，就等于拿到了任意方法执行 ——
 * 而这一步不会留下任何异常，看起来就是一个正常的定时任务在跑。
 * <p>
 * 加上白名单之后，只有<b>明确标了这个注解</b>的 bean 才可能被调用。
 * 写任务的人多打一个注解，攻击面从「整个容器」缩到「几个明确列出来的类」。
 *
 * <h3>为什么不用「配置文件里列白名单」</h3>
 * 那样白名单和任务实现分处两地，新增任务时很容易忘了改配置 ——
 * 而忘了的表现是运行期报「不允许调用」，得回头查配置。
 * 注解和实现在同一个文件里，加不加一眼可见。
 *
 * <h3>被调用的方法约定</h3>
 * 必须有一个 {@code public void run(String params)} 方法。
 * 固定签名而不是让任务自己声明，是为了让「能被调用的方法」也是确定的 ——
 * 否则白名单只限制了类，类里的任意 public 方法仍然可达。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScheduledTask {

    /** 这个任务干什么，只作说明用。 */
    String value() default "";
}
