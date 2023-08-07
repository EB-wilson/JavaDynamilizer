package dynamilize.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**动态工厂上下文标记器，用于确定在一个{@linkplain DynamicContext 动态语义上下文}中所使用的动态工厂
 * <p>具体来说，动态上下文的诸多行为会依赖于一个动态工厂，此注解即应用于一个字段或者局部变量，亦或者是一个方法参数之上，其上下文语义与java变量，字段和方法参数无异，
 * 请注意，当注解用于java静态字段时，此标记同样会具有静态上下文，反之亦然，即您不能在静态上下文内访问非静态上下文工厂标识
 *
 * <p>对于同一个上下文内的多次设置上下文标记，则在访问动态工厂时，会选择<strong>在此之前的最后一个标记</strong>使用*/
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
public @interface ContextMaker {
}
