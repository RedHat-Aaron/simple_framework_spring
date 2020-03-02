package com.athena.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author xiangxz
 * @Description 自动注入注解
 * @Date 10:04 PM 2020/3/1
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MyAutowired {

    //用来传入按照什么名称自动注入
    String value() default "";
}
