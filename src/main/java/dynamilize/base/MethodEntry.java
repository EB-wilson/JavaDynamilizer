package dynamilize.base;

import dynamilize.annotation.Super;
import dynamilize.annotation.This;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

@SuppressWarnings("unchecked")
public class MethodEntry<Owner extends DynamicObject<Owner>>{
  private final String name;
  private final FunctionType type;
  private final Function<Owner, ?> defFunc;

  public MethodEntry(Method invokeMethod){
    this.name = invokeMethod.getName();

    if(!Modifier.isStatic(invokeMethod.getModifiers()))
      throw new IllegalHandleException("cannot assign a non-static method to function");
    type = FunctionType.from(invokeMethod);

    Parameter[] parameters = invokeMethod.getParameters();
    int thisI = parameters[0].getAnnotation(This.class) != null? 0: -1;
    int superI = parameters[thisI + 1].getAnnotation(Super.class) != null? thisI + 1: -1;

    int offset = Math.max(thisI, superI) + 1;
    if(type.getTypes().length + offset != parameters.length)
      throw new IllegalHandleException("assigned arguments length unequals");

    if(thisI != -1){
      if(!Modifier.isFinal(parameters[thisI].getModifiers())){
        throw new IllegalHandleException("check the method " + invokeMethod + " parameters, the this pointer must be readOnly(final)");
      }
      if(!DynamicObject.class.isAssignableFrom(parameters[thisI].getType())){
        throw new IllegalHandleException("check the method " + invokeMethod + " parameters, the this pointer type must be assignable from " + DynamicObject.class);
      }
    }
    if(superI != -1){
      if(!Modifier.isFinal(parameters[superI].getModifiers())){
        throw new IllegalHandleException("check the method " + invokeMethod + " parameters, the super pointer must be readOnly(final)");
      }
      if(!DataPool.ReadOnlyPool.class.isAssignableFrom(parameters[superI].getType())){
        throw new IllegalHandleException("check the method " + invokeMethod + " parameters, the super pointer type must be assignable from " + DataPool.ReadOnlyPool.class);
      }
    }


    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try{
      MethodHandle call = lookup.unreflect(invokeMethod);
      Object[] parameterList = new Object[invokeMethod.getParameterCount()];
      defFunc = (self, args) -> {
        try{
          if(thisI != -1) parameterList[thisI] = self;
          if(superI != -1) parameterList[superI] = self.$dataPool$().getSuper();
          System.arraycopy(args, 0, parameterList, offset, args.length);
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

  public <R> Function<Owner, R> getFunction(){
    return (Function<Owner, R>) defFunc;
  }

  public FunctionType getType(){
    return type;
  }
}
