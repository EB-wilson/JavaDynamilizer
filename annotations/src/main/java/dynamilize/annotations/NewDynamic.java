package dynamilize.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface NewDynamic {
  String refDynamicMaker() default "<none>";
  String dynClassName();
  Class<?>[] impls() default {};
  Class<?>[] aspects() default {void.class};
}
