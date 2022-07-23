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

  public static Class<?>[] wrapper(Class<?>... clazz){
    return Arrays.stream(clazz).map(FunctionType::wrapper).toArray(Class[]::new);
  }

  public static Class<?>[] unwrapped(Class<?>... clazz){
    return Arrays.stream(clazz).map(FunctionType::unwrapped).toArray(Class[]::new);
  }

  public static Class<?> wrapper(Class<?> clazz){
    if(clazz == int.class) return Integer.class;
    if(clazz == float.class) return Float.class;
    if(clazz == long.class) return Long.class;
    if(clazz == double.class) return Double.class;
    if(clazz == byte.class) return Byte.class;
    if(clazz == short.class) return Short.class;
    if(clazz == boolean.class) return Boolean.class;
    if(clazz == char.class) return Character.class;
    return clazz;
  }

  public static Class<?> unwrapped(Class<?> clazz){
    if(clazz == Integer.class) return int.class;
    if(clazz == Float.class) return float.class;
    if(clazz == Long.class) return long.class;
    if(clazz == Double.class) return double.class;
    if(clazz == Byte.class) return byte.class;
    if(clazz == Short.class) return short.class;
    if(clazz == Boolean.class) return boolean.class;
    if(clazz == Character.class) return char.class;
    return clazz;
  }

  public static FunctionType inst(List<Class<?>> paramType){
    return inst(paramType.toArray(new Class[0]));
  }

  public static FunctionType inst(Class<?>... paramType){
    if(RECYCLE_POOL.isEmpty()) return new FunctionType(paramType);
    FunctionType res = RECYCLE_POOL.removeFirst();
    res.paramType = paramType;
    return res;
  }

  public static FunctionType inst(Object... param){
    return inst(unwrapped(Arrays.stream(param).map(Object::getClass).toArray(Class[]::new)));
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
    return match(unwrapped(Arrays.stream(args).map(Object::getClass).toArray(Class[]::new)));
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
    if(!(o instanceof FunctionType)) return false;
    FunctionType that = (FunctionType) o;
    return Arrays.equals(paramType, that.paramType);
  }

  @Override
  public int hashCode(){
    return Arrays.hashCode(paramType);
  }
}
