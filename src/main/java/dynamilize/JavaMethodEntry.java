package dynamilize;

import dynamilize.annotation.This;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;

import static dynamilize.ArgumentList.ARG_LEN_MAP;

/**对{@linkplain DynamicClass#visitClass(Class) 行为样版}中方法描述的入口，在动态类中描述子实例的某一函数行为。
 * <p>方法入口的运行实际上是对样版方法的引用，因此需要确保样版方法所在的类始终有效，方法入口会生成这个方法的入口函数提供给动态对象使用
 *
 * @author EBwilson */
@SuppressWarnings("unchecked")
public class JavaMethodEntry implements MethodEntry{
  private final String name;
  private final FunctionType type;
  private final Function<?, ?> defFunc;

  private final boolean isFinal;

  /**直接通过目标方法创建方法入口，并生成对方法引用的句柄提供给匿名函数以描述此函数行为
   *
   * @param invokeMethod 样版方法*/
  public JavaMethodEntry(Method invokeMethod){
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

    type = FunctionType.inst(FunctionType.toTypes(arg));

    boolean thisP = thisPointer;
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try{
      MethodHandle call = lookup.unreflect(invokeMethod);

      defFunc = (self, args) -> {
        Object[] argsArray = args.args();
        Object[] realArgArr = ARG_LEN_MAP[thisP? argsArray.length + 1: argsArray.length];
        if(thisP){
          realArgArr[0] = self;
          if(argsArray.length != 0) System.arraycopy(argsArray, 0, realArgArr, 1, realArgArr.length);
        }
        else System.arraycopy(argsArray, 0, realArgArr, 0, realArgArr.length);
        //实际上这里没用三元符可以减少一条将要执行的jvm指令

        try{
          return call.invokeWithArguments(realArgArr);
        }catch(Throwable e){
          throw new RuntimeException(e);
        }
      };
    }catch(IllegalAccessException e){
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getName(){
    return name;
  }

  @Override
  public boolean modifiable(){
    return !isFinal;
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
