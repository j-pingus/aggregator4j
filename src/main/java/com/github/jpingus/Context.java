package com.github.jpingus;
import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.TYPE)
public @interface Context {
    String value();
}