package com.mall.testsupport;

import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试用的中间件镜像，<b>版本跟线上对齐</b>。
 *
 * <p>版本对齐不是洁癖。测试用一个和线上不同的版本，最好的情况是白测
 * （测过的行为跟线上无关），最坏的情况是给出错误的信心 ——
 * 比如 MySQL 8.0 和 8.4 在默认认证插件、以及 {@code sql_mode} 上都有差异，
 * 在一个版本上过的 SQL 到另一个版本可能直接报错。
 *
 * <p>这些值必须和 mall-deploy/charts/mall/values.yaml 里的镜像保持一致。
 * 改集群镜像版本时记得一起改这里 —— 不一致不会有任何报错，
 * 只是测试悄悄地在测另一个东西。
 *
 * <p>Elasticsearch 用官方镜像而不是线上那个 {@code elasticsearch-ik}：
 * ik 分词器只在建索引/查询时才起作用，而这些测试目前只验证上下文能起来，
 * 用官方镜像可以省掉在 CI 里给测试作业配 GHCR 拉取凭证这一步。
 * <b>一旦有测试真正去建索引或做分词查询，就必须换成 ik 那个镜像</b>，
 * 否则 {@code ik_smart} 会因为分词器不存在而失败。
 */
public final class TestImages {

    /** 对齐 values.yaml 的 mysql:8.0.29 */
    public static final DockerImageName MYSQL = DockerImageName.parse("mysql:8.0.29");

    /** 对齐 values.yaml 的 redis:7-alpine */
    public static final DockerImageName REDIS = DockerImageName.parse("redis:7-alpine");

    /** 对齐 values.yaml 的 rabbitmq:3.12-management-alpine */
    public static final DockerImageName RABBITMQ = DockerImageName.parse("rabbitmq:3.12-management-alpine");

    /** 对齐 elasticsearch-ik/Dockerfile 的基础镜像 elasticsearch:8.11.1，也对齐客户端版本 */
    public static final DockerImageName ELASTICSEARCH = DockerImageName.parse("elasticsearch:8.11.1");

    private TestImages() {
    }
}
