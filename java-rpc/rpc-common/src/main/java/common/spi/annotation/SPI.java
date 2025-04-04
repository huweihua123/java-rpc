/*
 * @Author: weihua hu
 * @Date: 2025-04-03 19:37:53
 * @LastEditTime: 2025-04-03 20:28:12
 * @LastEditors: weihua hu
 * @Description: 
 */
package common.spi.annotation;

import java.lang.annotation.*;

/**
 * SPI注解，标记一个接口为扩展点
 * 支持通过Java SPI和RPC自定义SPI机制进行扩展
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface SPI {

    /**
     * 默认实现名称
     * 指定接口默认的实现类
     */
    String value() default "";

    /**
     * 扩展点分类，用于支持分组加载
     */
    String category() default "";

    /**
     * 是否单例模式
     * 默认为true，即所有相同名称的扩展点返回同一实例
     */
    boolean singleton() default true;
}