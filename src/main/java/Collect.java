import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.FIELD)
@Repeatable(Collects.class)
public @interface Collect {
    String value();
    String when() default "" ;
}
