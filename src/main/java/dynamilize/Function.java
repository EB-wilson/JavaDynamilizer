package dynamilize;

@FunctionalInterface
public interface Function<S, R>{
  R invoke(DynamicObject<S> self, ArgumentList args);

  default R invoke(DynamicObject<S> self, Object... args){
    ArgumentList argLis = ArgumentList.as(args);
    try{
      return invoke(self, argLis);
    }finally{
      argLis.type().recycle();
      argLis.recycle();
    }
  }

  default R invoke(DynamicObject<S> self, FunctionType type, Object... args){
    ArgumentList argLis = ArgumentList.asWithType(type, args);
    try{
      return invoke(self, argLis);
    }finally{
      argLis.recycle();
    }
  }

  interface NonRetFunction<S>{
    void invoke(DynamicObject<S> self, ArgumentList args);
  }
}
