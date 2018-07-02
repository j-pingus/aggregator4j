import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.FIELD)
@Repeatable(Aggregators.class)
public @interface Aggregator {
    String value();
    String when() default "" ;
}
