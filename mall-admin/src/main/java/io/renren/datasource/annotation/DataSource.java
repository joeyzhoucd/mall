package io.renren.datasource.annotation;

import java.lang.annotation.*;

/**
 * Data source annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataSource {
    String value() default "";
}