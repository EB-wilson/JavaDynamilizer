package dynamilize;

import dynamilize.annotation.This;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;

@SuppressWarnings("unchecked")
public class MethodEntry{
  private final String name;
  private final FunctionType type;
  private final Function<?, ?> defFunc;

  private final boolean isFinal;

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

  public String getName(){
    return name;
  }

  public boolean modifiable(){
    return !isFinal;
  }

  public <S, R> Function<S, R> getFunction(){
    return (Function<S, R>) defFunc;
  }

  public FunctionType getType(){
    return type;
  }
}
