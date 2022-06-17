package dynamilize.base;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public interface DynamicObject<Self extends DynamicObject<Self>>{
  DynamicClass<Self> getDyClass();

  <T> T getVar(String name);

  <T> void setVar(String name, T value);

  default <T> T calculateVar(String name, Calculator<T> calculator){
    T res;
    setVar(name, res = calculator.calculate(getVar(name)));
    return res;
  }

  <R> Function<Self, R> getFunc(String name, FunctionType args);

  default <R> R invokeFunc(String name, Object... args){
    Function<Self, R> res = getFunc(name, FunctionType.inst(Arrays.stream(args).map(Object::getClass).toArray(Class[]::new)));
    if(res == null)
      throw new IllegalHandleException("no such method declared: " + name);

    return res.invoke((Self) this, args);
  }
}
