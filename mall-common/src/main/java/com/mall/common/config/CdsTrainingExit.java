package com.mall.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * CDS 训练运行专用：上下文 refresh 完成后立刻退出 JVM。
 *
 * <h3>为什么要自己实现这个（而不是用 Spring 的现成属性）</h3>
 * 生成 CDS 归档的标准做法是「空跑一次应用，让它加载完所有类，然后退出」，
 * Spring Boot 3.2/3.3 为此提供了 {@code spring.context.exit=onRefresh}。
 * <p>
 * <b>那个属性在 Spring Boot 4.1.1 里已经不存在了。</b>不是改名，是整个字符串在
 * spring-boot-4.1.1.jar 的字节码里都搜不到，配置元数据里也没有。
 * 而它的失效方式是<b>完全静默</b>的：配了等于没配，应用照常完整启动、
 * 照常跑 ApplicationRunner、然后<b>永远不退出</b>。
 * <p>
 * 实际后果：12 个服务的镜像构建全部卡在训练运行上，直到 Docker 构建超时。
 * 而 CI 的矩阵里任何一个服务失败都会挡住最后那步「把镜像 tag 更新到 mall-deploy」，
 * 所以一个卡住的服务阻塞了全部部署 —— 表现是「CI 跑了 40 分钟没有任何产出」，
 * 完全不指向「某个配置属性被移除了」。
 * <p>
 * 这是本仓库反复出现的同一类问题（见 ConfigMetadataTest 的存在理由）：
 * <b>配置属性的失效是静默的</b>。而命令行参数不在 ConfigMetadataTest 的检查范围内，
 * 所以这里改成自己实现 —— 依赖一个自己控制的开关，就不会再被上游的移除打中。
 *
 * <h3>为什么挂在 ContextRefreshedEvent 上而不是 ApplicationRunner</h3>
 * 必须在 ApplicationRunner <b>之前</b>退出。否则 {@link EagerConnectionWarmup} 和
 * {@link RequestPathWarmup} 会先跑一遍 —— 后者要打 200 次自请求，
 * 在构建机上纯属浪费，而且训练运行本来就没有可用的数据库和 Redis，
 * 那些预热只会一路失败、拖长构建。
 * <p>
 * refresh 完成时所有单例已经实例化、绝大多数类已经加载，
 * 这正是 CDS 需要的状态 —— 再往后跑对归档内容的贡献很小。
 *
 * <h3>为什么用 System.exit 而不是 context.close()</h3>
 * {@code -XX:ArchiveClassesAtExit} 只在 <b>JVM 退出</b>时写归档，关闭上下文不够。
 * {@code System.exit(0)} 会触发正常的 JVM 退出路径（跑完 shutdown hook 再退出），
 * 归档就在这一步落盘。
 * <p>
 * 顺带说一个实测结论：<b>靠外部 {@code timeout} 命令发 SIGTERM 来结束训练运行是
 * 不可靠的</b> —— 本地试过，SIGTERM 之后归档没有生成。所以必须让进程自己主动退出。
 *
 * <h3>只在构建期开启</h3>
 * 默认关闭。只有各服务 Dockerfile 的训练运行那一行会传
 * {@code --mall.cds.training-exit=true}。
 * 这个类<b>绝不能在真实运行时被打开</b>：那会让服务一启动就退出，
 * 表现为无限 CrashLoopBackOff。名字里带 cds.training 就是为了让人一眼看出它的用途。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mall.cds.training-exit", havingValue = "true")
public class CdsTrainingExit {

    private static final Logger log = LoggerFactory.getLogger(CdsTrainingExit.class);

    /**
     * 真正的退出动作，抽成一个可替换的 bean。
     * <p>
     * 为什么要抽出来：这个监听器挂在 {@code ContextRefreshedEvent} 上，而任何刷新上下文
     * 的测试（包括 {@code ApplicationContextRunner.run()}）都会触发它 —— 直接写死
     * {@code System.exit(0)} 的话，<b>测试一跑就把测试 JVM 打死</b>
     * （surefire 报 "forked VM terminated without properly saying goodbye"）。
     * 这不是假想：第一版就是这么写的，测试立刻炸了。
     * <p>
     * 抽成 bean 之后，测试可以注入一个无副作用的实现来验证整条接线，
     * 而生产行为不变。
     */
    @FunctionalInterface
    public interface CdsExitAction {
        void exit();
    }

    @Bean
    @ConditionalOnMissingBean(CdsExitAction.class)
    CdsExitAction mallCdsExitAction() {
        // 不能只 close 上下文：-XX:ArchiveClassesAtExit 只在 JVM 退出时写归档。
        return () -> System.exit(0);
    }

    @Bean
    ApplicationListener<ContextRefreshedEvent> mallCdsTrainingExitListener(CdsExitAction exitAction) {
        return event -> {
            log.info("CDS 训练运行: 上下文已 refresh 完成，立即退出以导出共享归档。"
                    + "（如果你在生产环境看到这行日志，说明 mall.cds.training-exit 被误开了）");
            exitAction.exit();
        };
    }
}
