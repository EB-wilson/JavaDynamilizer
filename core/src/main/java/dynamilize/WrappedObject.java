package dynamilize;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**用于对对象进行动态访问的包装对象，仅具有受限的动态行为（可视为一个反射包装器）。
 * <p>
 * 对于一个包装动态对象，仅能动态的访问其原有的属性和方法，以下行为将抛出异常：
 * <ul>
 *   <li>尝试访问在被包装对象的层次结构中没有定义的成员（字段，方法）</li>
 *   <li>尝试修改包装动态对象的动态类型</li>
 *   <li>将包装动态对象尝试转换成被包装的对象类型</li>
 * </ul>*/
public class WrappedObject<T> implements DynamicObject<T>{
  private static final DynamicClass $wrappedDef$ = new DynamicClass("$wrappedDef$", null){
    @Override public void setVariable(String name, Initializer.Producer<?> prov)
    { throw new IllegalHandleException("wrapped object class is immutable"); }

    @Override
    public <S, R> void setFunction(String name, Function<S, R> func, Class<?>... argTypes)
    { throw new IllegalHandleException("wrapped object class is immutable"); }

    @Override
    public void visitClass(Class<?> template, JavaHandleHelper helper)
    { throw new IllegalHandleException("wrapped object class is immutable"); }

    @Override
    public void visitField(Field field)
    { throw new IllegalHandleException("wrapped object class is immutable"); }

    @Override
    public void visitMethod(Method method, JavaHandleHelper helper)
    { throw new IllegalHandleException("wrapped object class is immutable"); }
  };

  private final DataPool pool;
  private final T obj;

  WrappedObject(T obj, DataPool pool) {
    this.pool = pool;
    this.obj = obj;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T objSelf() {
    return obj;
  }

  @Override
  public DynamicClass getDyClass() {
    return $wrappedDef$;
  }

  @Override
  public IVariable getVariable(String name) {
    return pool.getVariable(name);
  }

  @Override
  public DataPool.ReadOnlyPool baseSuperPointer() {
    return null;
  }

  @Override
  public void setVariable(IVariable variable) {
    throw new IllegalHandleException("wrapped object cannot add new variable");
  }

  @Override
  public <T1> T1 varValueGet(String name) {
    throw new IllegalHandleException("unsupported operation");
  }

  @Override
  public <T1> void varValueSet(String name, T1 value) {
    throw new IllegalHandleException("unsupported operation");
  }

  @Override
  public IFunctionEntry getFunc(String name, FunctionType type) {
    return pool.select(name, type);
  }

  @Override
  public <R> void setFunc(String name, Function<T, R> func, Class<?>... argTypes) {
    throw new IllegalHandleException("wrapped object cannot add new function");
  }

  @Override
  public <R> void setFunc(String name, Function.SuperGetFunction<T, R> func, Class<?>... argTypes) {
    throw new IllegalHandleException("wrapped object cannot add new function");
  }
}
