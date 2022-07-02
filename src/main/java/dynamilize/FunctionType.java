package dynamilize;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class FunctionType{
  public static int MAX_RECYCLE = 1024;

  private static final Class<?>[] EMPTY = new Class[0];
  private static final LinkedList<FunctionType> RECYCLE_POOL = new LinkedList<>();

  private Class<?>[] paramType;

  private FunctionType(Class<?>... paramType){
    this.paramType = paramType;
  }

  public static FunctionType inst(List<Class<?>> paramType){
    return inst(paramType.toArray(Class[]::new));
  }

  public static FunctionType inst(Class<?>... paramType){
    if(RECYCLE_POOL.isEmpty()) return new FunctionType(paramType);
    FunctionType res = RECYCLE_POOL.removeFirst();
    res.paramType = paramType;
    return res;
  }

  public static FunctionType inst(Object... param){
    return inst(Arrays.stream(param).map(Object::getClass).toArray(Class[]::new));
  }

  public static FunctionType from(MethodType type){
    return inst(type.parameterArray());
  }

  public static FunctionType from(Method method){
    return inst(method.getParameterTypes());
  }

  public static FunctionType generic(int argCount){
    Class<?>[] argTypes = new Class[argCount];
    Arrays.fill(argTypes, Object.class);
    return inst(argTypes);
  }

  public boolean match(Object... args){
    return match(Arrays.stream(args).map(Object::getClass).toArray(Class[]::new));
  }

  public boolean match(Class<?>... argsType){
    if(argsType.length != paramType.length) return false;

    for(int i = 0; i < paramType.length; i++){
      if(!paramType[i].isAssignableFrom(argsType[i])) return false;
    }

    return true;
  }

  public Class<?>[] getTypes(){
    return paramType;
  }

  public void recycle(){
    if(RECYCLE_POOL.size() >= MAX_RECYCLE) return;

    paramType = EMPTY;
    RECYCLE_POOL.add(this);
  }

  @Override
  public boolean equals(Object o){
    if(this == o) return true;
    if(!(o instanceof FunctionType that)) return false;
    return Arrays.equals(paramType, that.paramType);
  }

  @Override
  public int hashCode(){
    return Arrays.hashCode(paramType);
  }
}
