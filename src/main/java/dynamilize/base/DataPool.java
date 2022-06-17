package dynamilize.base;

import java.util.*;

@SuppressWarnings({"unchecked"})
public class DataPool<Owner extends DynamicObject<Owner>>{
  private final DataPool<Owner> superPool;
  
  private Owner owner;

  private final Map<String, Map<FunctionType, Function<Owner, ?>>> funcPool = new HashMap<>();
  private final Map<String, IVariable> varPool = new HashMap<>();

  public DataPool(DataPool<Owner> superPool){
    this.superPool = superPool;
  }

  public void setOwner(Owner owner){
    this.owner = owner;
  }

  public Owner getOwner(){
    return owner;
  }

  public DataPool<? super Owner>.ReadOnlyPool getSuper(){
    return superPool.getReader();
  }

  public void set(String name, Function<Owner, ?> function, Class<?>... argsType){
    Objects.requireNonNullElseGet(getFunctions(name), () -> funcPool.computeIfAbsent(name, n -> new HashMap<>())).put(FunctionType.inst(argsType), function);
  }

  public void set(String name, Object value){
    Objects.requireNonNullElseGet(getVariable(name), () -> varPool.computeIfAbsent(name, n -> new Variable(name, false))).set(value);
  }

  public void add(IVariable var){
    varPool.put(var.name(), var);
    var.poolAdded(this);
  }

  protected Map<FunctionType, Function<Owner, ?>> getFunctions(String name){
    return funcPool.getOrDefault(name, superPool == null? null: superPool.getFunctions(name));
  }

  protected IVariable getVariable(String name){
    return varPool.getOrDefault(name, superPool == null? null: superPool.getVariable(name));
  }

  public <T> T get(String name){
    return (T) varPool.getOrDefault(name, superPool != null? superPool.get(name): null);
  }

  public <R> Function<Owner, R> select(String name, Class<?>... argTypes){
    Map<FunctionType, Function<Owner, R>> map = funcPool.getOrDefault(name, Collections.EMPTY_MAP);
    if(map.isEmpty()) return superPool != null? superPool.select(name, argTypes): null;

    FunctionType type = FunctionType.inst(argTypes);
    Function<Owner, R> res = map.get(type);
    if(res != null) return res;

    for(Map.Entry<FunctionType, Function<Owner, R>> entry: map.entrySet()){
      if(entry.getKey().match(argTypes)) return entry.getValue();
    }
    return superPool != null? (Function<Owner, R>) superPool.select(name, argTypes): null;
  }

  public <R> Function<Owner, R> select(String name, Object... arg){
    return select(name, Arrays.stream(arg).map(Object::getClass).toArray(Class[]::new));
  }

  private ReadOnlyPool getReader(){
    return new ReadOnlyPool();
  }

  class ReadOnlyPool{
    public <T> T get(String name){
      return DataPool.this.get(name);
    }

    public <R> Function<Owner, R> select(String name, Class<?>... argTypes){
      return DataPool.this.select(name, argTypes);
    }
  }
}
