package dynamilize.annotations;

import dynamilize.JavaHandleHelper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DynamilizeClass {
  /**是否为java引用模式，若为是则会首先将所类的所有public成员static化，而后将其按visit模式传递给{@linkplain dynamilize.DynamicClass#visitClass(Class, JavaHandleHelper)}
   * <br>否则，会将所有实例的声明首先转换为{@link dynamilize.Function}再传递给动态类进行声明*/
  boolean javaRefMode() default true;
  String className() default "<none>";
}
