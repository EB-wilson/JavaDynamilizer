package dynamilize;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unchecked"})
public class DataPool<Owner>{
  private final DataPool<Owner> superPool;
  
  private Owner owner;

  private final Map<String, Map<FunctionType, Function<Owner, ?>>> funcPool = new HashMap<>();
  private final Map<String, IVariable> varPool = new HashMap<>();

  public DataPool(DataPool<Owner> superPool){
    this.superPool = superPool;
  }

  public void setOwner(Owner owner){
    this.owner = owner;
    if(superPool != null) superPool.setOwner(owner);
  }

  public Owner getOwner(){
    return owner;
  }

  public DataPool<? super Owner>.ReadOnlyPool getSuper(){
    return superPool.getReader();
  }

  public void set(String name, Function<Owner, ?> function, Class<?>... argsType){
    funcPool.computeIfAbsent(name, n -> new HashMap<>()).put(FunctionType.inst(argsType), function);
  }

  public void set(String name, Object value){
    varPool.computeIfAbsent(name, Variable::new).set(value);
  }

  public void setConst(String name, Object value){
    if(varPool.containsKey(name))
      throw new IllegalHandleException("cannot set existed variable to const");

    varPool.put(name, new Variable(name, value));
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
    return getVariable(name).get();
  }

  @SuppressWarnings("rawtypes")
  public <R> Function<Owner, R> select(String name, FunctionType type){
    Map<FunctionType, Function<Owner, R>> map = (Map) getFunctions(name);
    if(map == null) return null;

    Function<Owner, R> res = map.get(type);
    type.recycle();
    if(res != null) return res;

    throw new NoSuchMethodError("no such method with name " + name);
  }

  private ReadOnlyPool getReader(){
    return new ReadOnlyPool();
  }

  public class ReadOnlyPool{
    private ReadOnlyPool(){}

    public <T> T get(String name){
      return DataPool.this.get(name);
    }

    public <R> Function<Owner, R> select(String name, FunctionType type){
      return DataPool.this.select(name, type);
    }

    public <R> R invokeFunc(String name, Object... args){
      FunctionType type = FunctionType.inst(args);
      R res = (R) select(name, type).invoke(owner, args);
      type.recycle();
      return res;
    }
  }
}
