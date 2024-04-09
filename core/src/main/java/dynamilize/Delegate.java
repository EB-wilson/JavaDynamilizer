package dynamilize;

/**经过包装的委托调用函数接口，与{@linkplain Function 函数}不同，委托不接收this指针*/
@FunctionalInterface
public interface Delegate<R>{
  R invoke(ArgumentList args);

  default R invoke(Object... args){
    ArgumentList lis = ArgumentList.as(args);
    R r = invoke(lis);
    lis.type().recycle();
    lis.recycle();
    return r;
  }

  default R invoke(FunctionType type, Object... args){
    ArgumentList lis = ArgumentList.asWithType(type, args);
    R r = invoke(lis);
    lis.recycle();
    return r;
  }
}
