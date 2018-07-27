package com.arhscube.evenge;
import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.TYPE)
public @interface Context {
    String value();
}
