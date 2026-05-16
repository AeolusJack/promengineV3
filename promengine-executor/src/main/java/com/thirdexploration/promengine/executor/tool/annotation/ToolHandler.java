package com.thirdexploration.promengine.executor.tool.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ToolHandler {

    String name();

    String description();

    String version() default "1.0.0";

    Category category() default Category.UTILITY;

    Location location() default Location.AUTO;

    boolean enabled() default true;

    enum Category {
        FILE, COMMAND, NETWORK, BROWSER, DATABASE, UTILITY, CUSTOM,INFORMATION,CODE,DATA,MEDIA
    }

    enum Location {
        AUTO,
        LOCAL,
        SANDBOX,
        REMOTE
    }
}