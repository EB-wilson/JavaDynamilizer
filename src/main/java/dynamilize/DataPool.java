package dynamilize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**用于存储和处置动态对象数据的信息容器，不应从外部访问，每一个动态对象都会绑定一个数据池存放对象的变量/函数等信息。
 * <p>对于一个{@linkplain DynamicClass 动态类}的实例，实例的数据池一定会有一个父池，这个池以动态类的直接超类描述的信息进行初始化。
 * <p>访问池信息无论如何都是以最近原则，即若本池内没有找到数据，则以距离实例的池最近的具有此变量/函数的父池的数据为准
 *
 * @author EBwilson*/
@SuppressWarnings({"unchecked"})
public class DataPool{
  private static final List<MethodEntry> TMP_LIS = new ArrayList<>();
  public static final MethodEntry[] EMP_METS = new MethodEntry[0];

  private static final List<IVariable> TMP_VAR = new ArrayList<>();
  public static final IVariable[] EMP_VARS = new IVariable[0];

  private final DataPool superPool;

  private final Map<String, Map<FunctionType, MethodEntry>> funcPool = new HashMap<>();
  private final Map<String, IVariable> varPool = new HashMap<>();
  private final Map<String, Object> varValue = new HashMap<>();
  private DynamicObject<?> owner;

  /**创建一个池对象并绑定到父池，父池可为null，这种情况下此池应当为被委托类型的方法/字段引用。
   * <p><strong>你不应该在外部使用时调用此类型</strong>
   *
   * @param superPool 此池的父池*/
  public DataPool(DataPool superPool){
    this.superPool = superPool;
  }

  /**初始化并绑定到一个{@linkplain DynamicObject 动态对象}，在动态对象创建后立即调用，通常，这应当在动态委托类的构造函数中生成此方法的调用语句
   *
   * @param owner 此池的所有者*/
  public void init(DynamicObject<?> owner){
    this.owner = owner;
  }

  /**获得此池绑定到的{@linkplain DynamicObject 动态对象}
   *
   * @return 池的所有者*/
  public DynamicObject<?> getOwner(){
    return owner;
  }

  /**获取父池，父池是不可变的，这意味着你不可以直接更改父池的内容，它是只读模式
   *
   * @return 父池的只读对象*/
  public DataPool.ReadOnlyPool getSuper(){
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
  public void set(String name, Function<?, ?> function, Class<?>... argsType){
    FunctionType type = FunctionType.inst(argsType);
    funcPool.computeIfAbsent(name, n -> new HashMap<>())
        .put(type, new FunctionEntry<>(name, true, function, type));
  }

  public void set(MethodEntry methodEntry){
    funcPool.computeIfAbsent(methodEntry.getName(), e -> new HashMap<>()).put(methodEntry.getType(), methodEntry);
  }

  /**从类层次结构中获取变量的对象
   *
   * @param name 变量名
   * @return 变量对象*/
  protected IVariable getVariable(String name){
    return varPool.getOrDefault(name, superPool == null? null: superPool.getVariable(name));
  }

  /**向池中添加一个变量对象，一般来说仅在标记java字段作为变量时会用到
   *
   * @param var 加入池的变量*/
  public void setVariable(IVariable var){
    varPool.putIfAbsent(var.name(), var);
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

    return var.get(owner);
  }

  /**将类层次结构中定义的函数输出为匿名函数，会优先查找类型签名相同的函数，若未查找到相同的才会转入类型签名匹配的函数，
   * 因此调用函数在性能需求较高的情况下，建议对实参列表明确声明类型的签名，这可以有效提高重载决策的速度
   * <p>如果函数没有被定义，则会抛出异常
   *
   * @param name 函数的名称
   * @param type 函数的参数类型
   * @return 选中函数的匿名函数*/
  public <S, R> Function<S, R> select(String name, FunctionType type){
    Map<FunctionType, MethodEntry> map = funcPool.get(name);
    MethodEntry res;

    if(map != null){
      res = map.get(type);
      if(res != null) return res.getFunction();
    }

    if(superPool != null) return superPool.select(name, type);

    return selectMatch(name, type);
  }

  private <S, R> Function<S, R> selectMatch(String name, FunctionType type){
    Map<FunctionType, MethodEntry> map = funcPool.get(name);
    if(map != null){
      for(Map.Entry<FunctionType, MethodEntry> entry: map.entrySet()){
        if(entry.getKey().match(type.getTypes())){
          return entry.getValue().getFunction();
        }
      }
    }

    if(superPool != null) return superPool.selectMatch(name, type);

    return null;
  }

  public IVariable[] getVariables(){
    TMP_VAR.clear();
    TMP_VAR.addAll(varPool.values());
    return TMP_VAR.toArray(EMP_VARS);
  }

  public MethodEntry[] getFunctions(){
    TMP_LIS.clear();
    for(Map<FunctionType, MethodEntry> entry: funcPool.values()){
      TMP_LIS.addAll(entry.values());
    }

    return TMP_LIS.toArray(EMP_METS);
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
    public <S, R> Function<S, R> getFunc(String name, FunctionType type){
      return DataPool.this.select(name, type);
    }

    /**@see DynamicObject#invokeFunc(String, Object...)*/
    public <R> R invokeFunc(String name, Object... args){
      ArgumentList lis = ArgumentList.as(args);
      R r = invokeFunc(name, lis);
      lis.type().recycle();
      lis.recycle();
      return r;
    }

    public <R> R invokeFunc(FunctionType type, String name, Object... args){
      ArgumentList lis = ArgumentList.asWithType(type, args);
      R r = invokeFunc(name, lis);
      lis.recycle();
      return r;
    }

    /**@see DynamicObject#invokeFunc(String, ArgumentList)*/
    public <R> R invokeFunc(String name, ArgumentList args){
      FunctionType type = args.type();

      return (R) getFunc(name, type).invoke((DynamicObject<Object>) owner, args);
    }
  }
}
