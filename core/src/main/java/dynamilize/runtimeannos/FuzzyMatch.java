package dynamilize.runtimeannos;

import java.lang.annotation.*;

/**模糊化切面方法匹配器，该注解应当仅用在{@linkplain AspectInterface 且面接口}的方法上，
 * 令被注解的切面方法以模糊的搜索规则匹配与本方法同名的<strong>其他重载方法</strong>
 *
 * <p>此注解需要配合规定的对方法的参数注解来描述对一个方法名称的匹配规则，而参数注解请参阅：
 * <ul>
 *   <li><strong>{@link AnyType}</strong></li>
 *   <li><strong>{@link TypeAssignable}</strong></li>
 *   <li><strong>{@link Exact}(默认值)</strong></li>
 * </ul>
 *
 * 对于不携带此注解的切面方法会采用精确匹配，其匹配器等价于此注解使用默认参数，所有参数都不携带匹配注解，即：
 * <pre>{@code
 * void method(String arg1, Object arg2);
 *
 * 等价于:
 *
 * @FuzzyMatch
 * void method(String arg1, Object arg2);
 * }</pre>
 *
 * 如果在切面接口的扩展结构中多次声明了不同的模糊匹配器，那么实际匹配会以最先找到的匹配器为准
 *
 * <strong>注意：</strong>无论您如何设置匹配器参数，所有在切面接口中的方法都永远不可能被排除，
 * 因为正常情况下切面接口中定义的方法在任何时候都会被动态对象实现，该模糊匹配器不能被用作自我过滤，仅能用于搜寻匹配的其他超类方法来添加委托。
 * 显然，通过模糊搜索匹配的额外切面方法并不能通过相应的且面接口类型进行访问*/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FuzzyMatch {
  /**模糊搜索的范围是否包含动态委托进行的基类（包括动态实现的接口列表）*/
  boolean inBaseClass() default true;
  /**模糊搜索的范围是否包含动态委托进行的基类扩展的超类（包括所有实现的接口）*/
  boolean inSuperClass() default true;
  /**是否仅匹配抽象方法（来自动态实现的接口或者委托基类为抽象类）*/
  boolean abstractOnly() default false;

  /**是否匹配所有范围内名称相同的方法，若此属性为真，则将忽略方法的参数表直接匹配所有同名的重载方法*/
  boolean anySameName() default false;

  /**使该参数位置匹配任何类型的参数
   * <br>属性： {@link AnyType#value()} */
  @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface AnyType{
    /**用于匹配参数名称的正则表达式，默认忽略参数名称仅比对参数类型*/
    String value() default "^.*$";
  }
  /**使该参数位置匹配可进行分配的类型，即被匹配的参数类型应当与匹配参数相同或者是其子类
   * <br>属性： {@link TypeAssignable#value()}*/
  @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface TypeAssignable{
    /**用于匹配参数名称的正则表达式，默认忽略参数名称仅比对参数类型*/
    String value() default "^.*$";
  }
  /**使该参数位置匹配的参数类型与这个参数必须是完全一致的*/
  @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Exact{
    Exact INSTANCE = new Exact(){
      @Override
      public Class<? extends Annotation> annotationType() {
        return Exact.class;
      }
    };
  }
}
