package dynamilize;


public class FunctionEntry<S, R> implements IFunctionEntry{
  private final String name;
  private final Function<S, R> func;
  private final FunctionType type;

  public FunctionEntry(String name, Function<S, R> func, FunctionType type){
    this.name = name;
    this.func = func;
    this.type = type;
  }

  public FunctionEntry(String name, Function.SuperGetFunction<S, R> func, FunctionType type, DataPool owner){
    this(
        name,
        (s, a) -> {
          DataPool.ReadOnlyPool p = owner.getSuper(s, s.baseSuperPointer());
          R res = func.invoke(s, p, a);
          p.recycle();
          return res;
        },
        type
    );
  }

  @Override
  public String getName(){
    return name;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Function<S, R> getFunction(){
    return func;
  }

  @Override
  public FunctionType getType(){
    return type;
  }
}
