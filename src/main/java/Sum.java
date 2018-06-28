import java.lang.annotation.*;

@Retention(
        RetentionPolicy.RUNTIME
)
@Target(ElementType.FIELD)
@Repeatable(Sums.class)
public @interface Sum {
    String value();
}
