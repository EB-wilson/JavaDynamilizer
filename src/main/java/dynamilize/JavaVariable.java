package dynamilize;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**对java字段的引用对象，基于{@linkplain MethodHandle 方法句柄}的默认内部实现
 *
 * @author EBwilson */
@SuppressWarnings("unchecked")
public class JavaVariable implements IVariable{
  private final Field field;
  private final MethodHandle setter;
  private final MethodHandle getter;

  public JavaVariable(Field field){
    this.field = field;
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    try{
      getter = lookup.unreflectGetter(field);
      setter = isConst()? null: lookup.unreflectSetter(field);
    }catch(IllegalAccessException e){
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public String name(){
    return field.getName();
  }

  @Override
  public boolean isConst(){
    return Modifier.isFinal(field.getModifiers());
  }

  @Override
  public <T> T get(DynamicObject<?> obj){
    try{
      return (T) getter.invoke(obj);
    }catch(ClassCastException e){
      throw e;
    }catch(Throwable e){
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, Object value){
    if(isConst())
      throw new IllegalHandleException("can not modifier a const variable");

    try{
      setter.invoke(obj, value);
    }catch(Throwable e){
      throw new IllegalHandleException(e);
    }
  }
}
