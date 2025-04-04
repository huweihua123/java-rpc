/*
 * @Author: weihua hu
 * @Date: 2025-04-03 20:29:11
 * @LastEditTime: 2025-04-03 23:11:46
 * @LastEditors: weihua hu
 * @Description:
 */
package common.spi.annotation;

import java.lang.annotation.*;

/**
 * 自适应扩展注解
 * 用于标记可以根据URL参数动态选择实现的扩展点
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Adaptive {

    /**
     * 参数名列表
     * 从URL参数列表中依次尝试获取这些参数名，直到获取第一个非空值
     * 如果没有指定，则使用扩展点接口类名的点号分隔小写字符串
     */
    String[] value() default {};
}