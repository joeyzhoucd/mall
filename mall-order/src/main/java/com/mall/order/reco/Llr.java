package com.mall.order.reco;

/**
 * Dunning 对数似然比 G²（2×2 列联表对「独立」的检验统计量）。
 *
 * <pre>
 *            有 B        没 B
 *   有 A     k11         k12
 *   没 A     k21         k22
 * </pre>
 * 衡量「A、B 一起出现有多不像巧合」，而不是「比例有多夸张」—— 只共现过 1 次的冷门，
 * lift 可以很高，G² 上不去。为什么选它而不是收缩 lift，见 mall-deploy/PLAN.md 推荐评估一节。
 *
 * <p><b>与离线评估逐位一致</b>：公式、加法顺序都照抄 mall-deploy/eval/algorithms.js，
 * 对数用 {@link StrictMath#log}（fdlibm，与 V8 同源），不用 {@link Math#log}
 * —— 后者允许 JVM 用 CPU 指令，结果可能差最后一位，同分附近的名次就会对调，
 * 「离线评估里 LLR 赢了」这句话就不再适用于线上。一致性由 ComplementCalculatorTest 守着。
 */
public final class Llr {

    private Llr() {
    }

    /** 熵形式：2·[H(行和) + H(列和) − H(四格)]，H(k…) = xlx(Σk) − Σ xlx(k) */
    public static double g2(long k11, long k12, long k21, long k22) {
        return 2 * (h(k11 + k12, k21 + k22) + h(k11 + k21, k12 + k22) - h(k11, k12, k21, k22));
    }

    /** 定义式 2·Σ O·ln(O/E)。只给测试用：两种写法必须处处相等 */
    static double g2Direct(long k11, long k12, long k21, long k22) {
        double n = k11 + k12 + k21 + k22;
        double[] r = {k11 + k12, k21 + k22};
        double[] c = {k11 + k21, k12 + k22};
        long[][] o = {{k11, k12}, {k21, k22}};
        double g = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                double e = r[i] * c[j] / n;
                if (o[i][j] > 0) {
                    g += o[i][j] * StrictMath.log(o[i][j] / e);
                }
            }
        }
        return 2 * g;
    }

    private static double h(long... ks) {
        long sum = 0;
        for (long k : ks) {
            sum += k;
        }
        double acc = 0;
        for (long k : ks) {
            acc = acc + xlx(k);
        }
        return xlx(sum) - acc;
    }

    private static double xlx(double x) {
        return x > 0 ? x * StrictMath.log(x) : 0;
    }
}
