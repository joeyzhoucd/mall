package com.mall.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.mq")
public class MallMqProperties {

    private final Order order = new Order();
    private final Stock stock = new Stock();
    private final Seckill seckill = new Seckill();
    private final Connection connection = new Connection();

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

