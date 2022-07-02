package dynamilize;

@SuppressWarnings("unchecked")
public interface DynamicObject<Self>{
  DynamicClass getDyClass();

  DataPool<Self>.ReadOnlyPool superPoint();

  <T> T getVar(String name);

  <T> void setVar(String name, T value);

  default <T> T calculateVar(String name, Calculator<T> calculator){
    T res;
    setVar(name, res = calculator.calculate(getVar(name)));
    return res;
  }

  <R> Function<Self, R> getFunc(String name, FunctionType type);

  <R> void setFunc(String name, Function<Self, R> func, Class<?>... argTypes);

  default <R> R invokeFunc(String name, Object... args){
    FunctionType type = FunctionType.inst(args);
    Function<Self, R> res = getFunc(name, type);
    type.recycle();

    if(res == null)
      throw new IllegalHandleException("no such method declared: " + name);

    return res.invoke((Self) this, args);
  }
}
