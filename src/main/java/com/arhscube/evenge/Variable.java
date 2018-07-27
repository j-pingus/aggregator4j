package com.arhscube.evenge;
import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target({ElementType.FIELD,ElementType.TYPE})
public @interface Variable {
    String value();
}
