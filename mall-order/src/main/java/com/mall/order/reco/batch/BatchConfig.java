package com.mall.order.reco.batch;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 批任务参数，全部从环境变量读（CronJob 的 env，和 mall-order 共用同一组 MYSQL_* 变量与 Secret）。
 *
 * <p>默认值就是生产口径；评估口径（和离线评估逐条比对）用：
 * RECO_ORDER_STATUSES=all、RECO_MEMBER_FROM/TO=批次 B 区间、RECO_EXCLUDE_SAME_CATEGORY=false、RECO_ACTIVATE=false。
 *
 * @param orderStatuses        只用这些状态的订单；空列表 = 不按状态过滤。默认 1,2,3,5：付过款且没退款
 *                             （已支付、已发货、已收货、售后中），排除待付款 0、已关闭 4、已退款 6
 * @param memberFrom           可选的会员区间（评估用），null = 全部会员
 * @param activate             写完且过了质量闸门后是否切指针；false 只留一个 READY 批次（评估用）
 * @param minCoverageRatio     新批次覆盖的 SPU 数低于「当前生效批次 × 这个比例」就拒绝
 * @param keepBatches          保留最近几批（ACTIVE/SUPERSEDED/READY）的明细
 */
public record BatchConfig(
        String jdbcUrl,
        String username,
        String password,
        List<Integer> orderStatuses,
        Long memberFrom,
        Long memberTo,
        int topK,
        boolean excludeSameCategory,
        boolean activate,
        double minCoverageRatio,
        int keepBatches) {

    public static final String KIND = "complement";

    public BatchConfig {
        if ((memberFrom == null) != (memberTo == null)) {
            throw new IllegalArgumentException("RECO_MEMBER_FROM 和 RECO_MEMBER_TO 要么都给要么都不给");
        }
        if (topK <= 0 || topK > 100) {
            throw new IllegalArgumentException("RECO_TOP_K 应在 1~100：" + topK);
        }
        if (minCoverageRatio < 0 || minCoverageRatio > 1) {
            throw new IllegalArgumentException("RECO_MIN_COVERAGE_RATIO 应在 0~1：" + minCoverageRatio);
        }
        if (keepBatches < 1) {
            throw new IllegalArgumentException("RECO_KEEP_BATCHES 至少 1");
        }
        orderStatuses = List.copyOf(orderStatuses);
    }

    public static BatchConfig fromEnv(Map<String, String> env) {
        String host = env.getOrDefault("MYSQL_HOST", "192.168.77.100");
        String port = env.getOrDefault("MYSQL_PORT", "3306");
        String ssl = env.getOrDefault("MYSQL_SSL_MODE", "REQUIRED");
        // 与 mall-order 的 datasource URL 同一套参数，另加 rewriteBatchedStatements：
        // 否则 JDBC batch 在 MySQL 驱动里仍是逐条发送，几万行写入慢一个数量级
        String url = "jdbc:mysql://" + host + ":" + port + "/mall_oms?useUnicode=true&characterEncoding=UTF-8"
                + "&sslMode=" + ssl + "&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true";
        String statuses = env.getOrDefault("RECO_ORDER_STATUSES", "1,2,3,5").trim();
        List<Integer> statusList = "all".equalsIgnoreCase(statuses) ? List.of()
                : Arrays.stream(statuses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf).toList();
        return new BatchConfig(
                env.getOrDefault("RECO_JDBC_URL", url),
                env.getOrDefault("MYSQL_USERNAME", "root"),
                env.getOrDefault("MYSQL_PASSWORD", "root"),
                statusList,
                optLong(env.get("RECO_MEMBER_FROM")),
                optLong(env.get("RECO_MEMBER_TO")),
                Integer.parseInt(env.getOrDefault("RECO_TOP_K", "20")),
                Boolean.parseBoolean(env.getOrDefault("RECO_EXCLUDE_SAME_CATEGORY", "true")),
                Boolean.parseBoolean(env.getOrDefault("RECO_ACTIVATE", "true")),
                Double.parseDouble(env.getOrDefault("RECO_MIN_COVERAGE_RATIO", "0.5")),
                Integer.parseInt(env.getOrDefault("RECO_KEEP_BATCHES", "3")));
    }

    /** 写进 oms_reco_batch.params，事后能看出这一批是按什么口径算的（不含账号密码） */
    public String describe() {
        return "statuses=" + (orderStatuses.isEmpty() ? "all" : orderStatuses)
                + (memberFrom == null ? "" : " members=" + memberFrom + "~" + memberTo)
                + " topK=" + topK + " excludeSameCategory=" + excludeSameCategory + " activate=" + activate;
    }

    private static Long optLong(String v) {
        return v == null || v.isBlank() ? null : Long.valueOf(v.trim());
    }
}
