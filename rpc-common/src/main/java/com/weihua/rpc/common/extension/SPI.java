/*
 * @Author: weihua hu
 * @Date: 2025-04-13 19:02:02
 * @LastEditTime: 2025-04-13 19:02:03
 * @LastEditors: weihua hu
 * @Description:
 */
package com.weihua.rpc.common.extension;

import java.lang.annotation.*;

/**
 * SPI注解，标记一个接口为扩展点接口
 * 可以被ServiceLoader和ExtensionLoader加载
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SPI {

    /**
     * 默认扩展名
     */
    String value() default "";
}