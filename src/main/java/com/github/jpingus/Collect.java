package com.github.jpingus;
import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target({ElementType.FIELD,ElementType.TYPE})
@Repeatable(Collects.class)
public @interface Collect {
    String value();
    String when() default "" ;
    String what() default "this";
}
