import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.FIELD)
@Repeatable(Executes.class)
public @interface Execute {
    String value();
    String when() default "" ;
}
