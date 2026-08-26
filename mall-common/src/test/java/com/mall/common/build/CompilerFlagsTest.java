package com.mall.common.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 断言编译选项真的生效了。
 *
 * <h3>为什么需要它</h3>
 * 构建选项静默失效是这个仓库反复吃到的一类问题：pom 里写着，构建也成功，
 * 但产物里没有对应效果，而且没有任何警告。
 * <p>
 * 具体到 -parameters（把方法参数名写进 class 文件）：实测出现过【本地构建的 class 有参数名、
 * CI 构建的没有】，同一份源码本地起得来、集群里起不来。Spring 那边的表现是
 * "Parameter 0 of method xxx required a single bean, but 3 were found" ——
 * 而它报错里关于 -parameters 的提示是【无条件输出】的（反汇编
 * NoUniqueBeanDefinitionFailureAnalyzer 确认：ldc 到 append 之间没有任何分支），
 * 所以那句话既不能证明参数名缺失、也会把排查带偏。
 * <p>
 * 这个测试把"参数名到底有没有编进去"变成一条明确的断言。它跑在 CI 里，
 * 而 CI 正是问题出现的地方 —— 于是编译选项一旦失效就是测试失败，而不是几小时后
 * 某个 pod 起不来。
 *
 * <h3>注意：这个测试不能替代 @Qualifier</h3>
 * 装配的正确性不应该建立在编译选项一定生效之上，所以有多个同类型候选 bean 的注入点
 * 一律显式写 &#64;Qualifier（见 MallMqAutoConfiguration）。
 * 这个测试守的是另一件事：-parameters 对别的地方（比如 &#64;ConfigurationProperties 的
 * 构造器绑定、Spring Data 的派生查询）也有用，不该在无人察觉的情况下丢掉。
 */
class CompilerFlagsTest {

    @Test
    @DisplayName("-parameters 必须生效：class 文件里要保留方法参数名")
    void parameterNamesAreRetained() throws NoSuchMethodException {
        Method method = CompilerFlagsTest.class.getDeclaredMethod(
                "sampleMethod", String.class, int.class);
        Parameter[] parameters = method.getParameters();

        // isNamePresent() 直接反映 class 文件里有没有 MethodParameters 属性，
        // 这是 JDK 给出的权威判据，比解析 javap 输出可靠。
        assertTrue(parameters[0].isNamePresent(),
                "class 文件里没有参数名 —— 说明编译时没有加上 -parameters。"
                + "检查根 pom 的 maven-compiler-plugin 是否有 <parameters>true</parameters>，"
                + "以及那段 <configuration> 是否真的被应用（settings.xml 的 profile 可能覆盖属性，"
                + "所以那里用的是字面量而不是属性引用）。");

        // 再核对具体的名字：只判断 isNamePresent 还不够 —— 万一某个构建路径给出
        // arg0/arg1 这种占位名，isNamePresent 也可能是 true，但按名字装配照样会失败。
        assertEquals("text", parameters[0].getName(), "第一个参数名不对");
        assertEquals("count", parameters[1].getName(), "第二个参数名不对");
    }

    /** 仅供上面的测试反射用。参数名就是被断言的对象，不要改名。 */
    @SuppressWarnings("unused")
    private void sampleMethod(String text, int count) {
        // 空实现：这个方法只是一个用来检查字节码里参数名的样本
    }
}
