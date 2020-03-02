package com.athena.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author xiangxz
 * @Description 标注业务类注解
 * @Date 10:00 PM 2020/3/1
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MyComponent {

    //用来传入标注类的注册名称
    String value() default "";
}
