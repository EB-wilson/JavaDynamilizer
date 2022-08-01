package dynamilize;

import java.util.HashMap;
import java.util.Map;

/**用于存储和处置动态对象数据的信息容器，不应从外部访问，每一个动态对象都会绑定一个数据池存放对象的变量/函数等信息。
 * <p>对于一个{@linkplain DynamicClass 动态类}的实例，实例的数据池一定会有一个父池，这个池以动态类的直接超类描述的信息进行初始化。
 * <p>访问池信息无论如何都是以最近原则，即若本池内没有找到数据，则以距离实例的池最近的具有此变量/函数的父池的数据为准
 * <p>关于变量与函数的写入策略略有不同，请参阅{@link DataPool#set(String, Object)}和{@link DataPool#set(String, Function, Class[])}
 *
 * @author EBwilson*/
@SuppressWarnings({"unchecked"})
public class DataPool<Owner>{
  private final DataPool<Owner> superPool;
  
  private DynamicObject<Owner> owner;

  private final Map<String, Map<FunctionType, Function<Owner, ?>>> funcPool = new HashMap<>();
  private final Map<String, IVariable> varPool = new HashMap<>();

  /**创建一个池对象并绑定到父池，父池可为null，这种情况下此池应当为被委托类型的方法/字段引用。
   * <p><strong>你不应该在外部使用时调用此类型</strong>
   *
   * @param superPool 此池的父池*/
  public DataPool(DataPool<Owner> superPool){
    this.superPool = superPool;
  }

  /**绑定到一个{@linkplain DynamicObject 动态对象}，在动态对象创建后立即调用，通常，这应当生成为动态委托类的构造函数语句
   *
   * @param owner 此池的所有者*/
  public void setOwner(DynamicObject<Owner> owner){
    this.owner = owner;
    if(superPool != null) superPool.setOwner(owner);
  }

  /**获得此池绑定到的{@linkplain DynamicObject 动态对象}
   *
   * @return 池的所有者*/
  public DynamicObject<Owner> getOwner(){
    return owner;
  }

  /**获取父池，父池是不可变的，这意味着你不可以直接更改父池的内容，它是只读模式
   *
   * @return 父池的只读对象*/
  public DataPool<? super Owner>.ReadOnlyPool getSuper(){
    return superPool.getReader();
  }

  /**在本池设置一个函数，无论父池是否具有同名同类型的函数存在，若本池中存在同名同类型函数，则旧函数将被覆盖。
   * <p>函数的名称，行为与参数类型和java方法保持一致，返回值由行为传入的匿名函数确定，例如：
   * <pre>{@code
   * java定义的方法
   * int getTime(String location){
   *   return foo(location);
   * }
   * 等价于设置函数
   * set("getTime", (self, args) -> return foo(args.get(0)), String.class);
   * }</pre>
   *
   * @param name 函数名称
   * @param argsType 函数的参数类型列表
   * @param function 描述此函数行为的匿名函数*/
  public void set(String name, Function<Owner, ?> function, Class<?>... argsType){
    funcPool.computeIfAbsent(name, n -> new HashMap<>()).put(FunctionType.inst(argsType), function);
  }

  /**设置池的变量，这将搜寻父池中已存在的目标变量，若在类层次结构中找到了此名称的变量，则设置其值，否则会在本池设置新的变量
   *
   * @param name 变量名称
   * @param value 变量的值*/
  public void set(String name, Object value){
    IVariable var = getVariable(name);
    if(var == null){
      var = varPool.computeIfAbsent(name, Variable::new);
      var.poolAdded(this);
    }
    var.set(value);
  }

  /**在本池设置常量，若在类层次结构中存在同名变量则无法设置常量
   *
   * @param name 常量名称
   * @param value 常量值*/
  public void setConst(String name, Object value){
    if(getVariable(name) != null || varPool.containsKey(name))
      throw new IllegalHandleException("cannot set existed variable to const");

    varPool.put(name, new Variable(name, value));
  }

  /**向池中添加一个变量对象，一般来说仅在标记java字段作为变量时会用到
   *
   * @param var 加入池的变量*/
  public void add(IVariable var){
    varPool.put(var.name(), var);
    var.poolAdded(this);
  }

  /**从类层次结构中获取变量的对象
   *
   * @param name 变量名
   * @return 变量对象*/
  protected IVariable getVariable(String name){
    return varPool.getOrDefault(name, superPool == null? null: superPool.getVariable(name));
  }

  /**获取变量的值，如果变量未定义则会抛出异常
   *
   * @param name 变量名称
   * @return 该变量的值
   * @throws IllegalHandleException 若变量尚未被定义*/
  public <T> T get(String name){
    IVariable var = getVariable(name);
    if(var == null)
      throw new IllegalHandleException("variable " + name + " was undefined");

    return var.get();
  }

  /**将类层次结构中定义的函数输出为匿名函数，会优先查找类型签名相同的函数，若未查找到相同的才会转入类型签名匹配的函数，
   * 因此调用函数在性能需求较高的情况下，建议对实参列表明确声明类型的签名，这可以有效提高重载决策的速度
   * <p>如果函数没有被定义，则会抛出异常
   *
   * @param name 函数的名称
   * @param type 函数的参数类型
   * @return 选中函数的匿名函数
   * @throws NoSuchMethodError 若指定的函数未被定义*/
  @SuppressWarnings("rawtypes")
  public <R> Function<Owner, R> select(String name, FunctionType type){
    Map<FunctionType, Function<Owner, R>> map = (Map) funcPool.get(name);
    Function<Owner, R> res;

    if(map != null){
      res = map.get(type);
      if(res != null) return res;
    }

    if(superPool != null) return superPool.select(name, type);

    res = selectMatch(name, type);
    if(res != null) return res;

    throw new NoSuchMethodError("no such method with name " + name);
  }

  @SuppressWarnings("rawtypes")
  private <R> Function<Owner, R> selectMatch(String name, FunctionType type){
    Map<FunctionType, Function<Owner, R>> map = (Map) funcPool.get(name);
    if(map != null){
      for(Map.Entry<FunctionType, Function<Owner, R>> entry: map.entrySet()){
        if(entry.getKey().match(type.getTypes())){
          return entry.getValue();
        }
      }
    }

    if(superPool != null) return superPool.selectMatch(name, type);

    return null;
  }

  /**获得池的只读对象*/
  private ReadOnlyPool getReader(){
    return new ReadOnlyPool();
  }

  public class ReadOnlyPool{
    private ReadOnlyPool(){}

    /**@see DynamicObject#getVar(String)*/
    public <T> T getVar(String name){
      return DataPool.this.get(name);
    }

    /**@see DynamicObject#getFunc(String, FunctionType)*/
    public <R> Function<Owner, R> getFunc(String name, FunctionType type){
      return DataPool.this.select(name, type);
    }

    /**@see DynamicObject#invokeFunc(String, Object...)*/
    public <R> R invokeFunc(String name, Object... args){
      ArgumentList lis = ArgumentList.as(args);
      try{
        return invokeFunc(name, lis);
      }finally{
        lis.type().recycle();
        lis.recycle();
      }
    }

    public <R> R invokeFunc(FunctionType type, String name, Object... args){
      ArgumentList lis = ArgumentList.asWithType(type, args);
      try{
        return invokeFunc(name, lis);
      }finally{
        lis.recycle();
      }
    }

    /**@see DynamicObject#invokeFunc(String, ArgumentList)*/
    public <R> R invokeFunc(String name, ArgumentList args){
      FunctionType type = args.type();

      return (R) getFunc(name, type).invoke(owner, args);
    }
  }
}
