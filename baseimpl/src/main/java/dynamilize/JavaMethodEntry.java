package dynamilize;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**对{@linkplain DynamicClass#visitClass(Class, JavaHandleHelper) 行为样版}中方法描述的入口，在动态类中描述子实例的某一函数行为。
 * <p>方法入口的运行实际上是对样版方法的引用，因此需要确保样版方法所在的类始终有效，方法入口会生成这个方法的入口函数提供给动态对象使用
 *
 * @author EBwilson */
@SuppressWarnings("unchecked")
public class JavaMethodEntry implements IFunctionEntry{
  private final String name;
  private final FunctionType type;
  private final Function<?, ?> defFunc;

  /**直接通过目标方法创建方法入口，并生成对方法引用的句柄提供给匿名函数以描述此函数行为
   *
   * @param invokeMethod 样版方法*/
  public JavaMethodEntry(Method invokeMethod){
    this.name = invokeMethod.getName();
    this.type = FunctionType.inst(invokeMethod);

    defFunc = (self, args) -> {
      try {
        return invokeMethod.invoke(self.objSelf(), args.args());
      } catch (InvocationTargetException|IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    };
  }

  @Override
  public String getName(){
    return name;
  }

  @Override
  public <S, R> Function<S, R> getFunction(){
    return (Function<S, R>) defFunc;
  }

  @Override
  public FunctionType getType(){
    return type;
  }
}
