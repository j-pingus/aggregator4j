package com.github.jpingus;
import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.FIELD)
public @interface Executes {
    Execute[] value();
}
