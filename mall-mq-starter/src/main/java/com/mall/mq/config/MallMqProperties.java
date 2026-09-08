package com.mall.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.mq")
public class MallMqProperties {

    private final Order order = new Order();
    private final Stock stock = new Stock();
    private final Seckill seckill = new Seckill();
    private final Connection connection = new Connection();
    private final Listener listener = new Listener();

    public Listener getListener() {
        return listener;
    }

    public Order getOrder() {
        return order;
    }

    public Stock getStock() {
        return stock;
    }

    public Seckill getSeckill() {
        return seckill;
    }

    public Connection getConnection() {
        return connection;
    }

    public static class Order {
        private boolean enabled = false;
        private long delayTtlMs = 30 * 60 * 1000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDelayTtlMs() {
            return delayTtlMs;
        }

        public void setDelayTtlMs(long delayTtlMs) {
            this.delayTtlMs = delayTtlMs;
        }
    }

    public static class Stock {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Seckill {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 消息监听容器的并发与预取。<b>两个都必须显式配，Spring AMQP 的默认组合在这里是错的。</b>
     *
     * <h3>默认是 concurrency=1 + prefetch=250（从字节码确认，不是凭记忆）</h3>
     * {@code SimpleMessageListenerContainer} 的 {@code concurrentConsumers} 初始化为 1，
     * {@code AbstractMessageListenerContainer.DEFAULT_PREFETCH_COUNT = 250}。
     * 也就是说：<b>每个队列每个 pod 只有一个消费者，但那一个消费者一次能抓走 250 条未确认消息。</b>
     *
     * <h3>这个组合的问题不是吞吐，是负载分布</h3>
     * 业务服务都是 2 副本。突发 300 条消息时，A pod 的单个消费者可以一次抓走 250 条，
     * B pod 只拿到 50 —— 而 A 仍然一条一条处理。结果是一个 pod 压着 250 条积压、
     * 另一个几乎空闲。而"突发"正是秒杀的形态，也正是这套 MQ 存在的理由。
     * <p>
     * 崩溃时那 250 条还要全部重投，放大了重复处理的量
     * （幂等表挡得住，但那是本不必发生的工作）。
     *
     * <h3>为什么慢消费者要用小 prefetch，而不是相反</h3>
     * prefetch 的作用是"别让消费者等着 broker 送下一条"。
     * 需要的量约等于 {@code 每个消费者 × ceil(broker 往返 / 单条处理耗时)}。
     * 这里单条处理是 DB 事务（毫秒级），broker 往返是集群内网（亚毫秒级），
     * 比值远小于 1 —— 也就是说 <b>1~2 条就够了</b>，消费者永远不会因为等 broker 而空转。
     * 大 prefetch 只在"处理比往返还快"（微秒级纯内存处理）时才有意义，这里不是。
     * 所以默认给 4：足够不空转，又小到能在副本间公平分配。
     *
     * <h3>concurrency 默认仍然是 1 —— 提高它需要压测数据，我没有</h3>
     * 五个监听器从<b>正确性</b>上说都可以并发（消息之间互相独立，
     * 而且都走 consumeOnce 幂等 + 库存那侧是原子 UPDATE 裁决），
     * 所以这纯粹是容量问题，不是安全问题。
     * <p>
     * 但天花板不在消费者数量上：{@code spring.datasource.hikari.maximum-pool-size=5}，
     * 而这 5 个连接是<b>和 HTTP 请求共用</b>的。把 concurrency 提到 4，
     * 可能只是把排队从 MQ 挪到 Hikari，还顺带饿着 HTTP。
     * 要定这个值，得先量出"单条消息处理耗时"和"HTTP 侧对连接池的占用"，
     * 见 mall-deploy/loadtest/。在有那个数之前不动它。
     */
    public static class Listener {
        private int concurrency = 1;
        private int prefetch = 4;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getPrefetch() {
            return prefetch;
        }

        public void setPrefetch(int prefetch) {
            this.prefetch = prefetch;
        }
    }

    public static class Connection {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}

