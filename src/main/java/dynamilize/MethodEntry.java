package dynamilize;

import dynamilize.annotation.This;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;

/**对{@linkplain DynamicClass#visitClass(Class) 行为样版}中方法描述的入口，在动态类中描述子实例的某一函数行为。
 * <p>方法入口的运行实际上是对样版方法的引用，因此需要确保样版方法所在的类始终有效，方法入口会生成这个方法的入口函数提供给动态对象使用
 *
 * @author EBwilson */
@SuppressWarnings("unchecked")
public class MethodEntry{
  private final String name;
  private final FunctionType type;
  private final Function<?, ?> defFunc;

  private final boolean isFinal;

  /**直接通过目标方法创建方法入口，并生成对方法引用的句柄提供给匿名函数以描述此函数行为
   *
   * @param invokeMethod 样版方法*/
  public MethodEntry(Method invokeMethod){
    this.name = invokeMethod.getName();
    this.isFinal = Modifier.isFinal(invokeMethod.getModifiers());

    if(!Modifier.isStatic(invokeMethod.getModifiers()))
      throw new IllegalHandleException("cannot assign a non-static method to function");

    Parameter[] parameters = invokeMethod.getParameters();
    ArrayList<Parameter> arg = new ArrayList<>();

    boolean thisPointer = false;
    for(int i = 0; i < parameters.length; i++){
      Parameter param = parameters[i];
      if(param.getAnnotation(This.class) != null){
        if(thisPointer)
          throw new IllegalHandleException("only one self-pointer can exist");
        if(i != 0)
          throw new IllegalHandleException("self-pointer must be the first in parameters");

        thisPointer = true;
      }
      else arg.add(param);
    }

    type = FunctionType.inst(arg.stream().map(Parameter::getType).toArray(Class[]::new));

    boolean thisP = thisPointer;
    int invokeParamCont = invokeMethod.getParameterCount();
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try{
      MethodHandle call = lookup.unreflect(invokeMethod);
      ArrayList<Object> parameterList = new ArrayList<>(16);

      defFunc = (self, args) -> {
        try{
          int off = 0;
          if(thisP){
            if(parameterList.size() >= 1){
              parameterList.set(0, self);
            }
            else parameterList.add(self);
            off++;
          }

          Object[] argArr = args.args();
          for(int i = 0; i < argArr.length; i++){
            if(parameterList.size() >= 1){
              parameterList.set(i + off, argArr[i]);
            }
            else parameterList.add(argArr[i]);
          }

          for(int i = parameterList.size(); i > invokeParamCont; i--){
            parameterList.remove(i - 1);
          }

          return call.invokeWithArguments(parameterList);
        }catch(Throwable e){
          throw new RuntimeException(e);
        }
      };
    }catch(IllegalAccessException e){
      throw new RuntimeException(e);
    }
  }

  /**获取方法入口的名称*/
  public String getName(){
    return name;
  }

  /**此方法入口是否允许被替换*/
  public boolean modifiable(){
    return !isFinal;
  }

  /**获取此方法入口定义的引用匿名函数*/
  public <S, R> Function<S, R> getFunction(){
    return (Function<S, R>) defFunc;
  }

  /**获取此方法的形式参数表类型*/
  public FunctionType getType(){
    return type;
  }
}
