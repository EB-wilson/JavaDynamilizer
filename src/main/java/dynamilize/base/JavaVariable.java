package dynamilize.base;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@SuppressWarnings("unchecked")
public class JavaVariable implements IVariable{
  private final Field field;
  private final MethodHandle setter;
  private final MethodHandle getter;

  private DynamicObject<?> owner;

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
  public void poolAdded(DataPool<?> pool){
    owner = pool.getOwner();
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
  public <T> T get(){
    try{
      return (T) getter.invoke(owner);
    }catch(ClassCastException e){
      throw e;
    }catch(Throwable e){
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public void set(Object value){
    if(isConst())
      throw new IllegalHandleException("can not modifier a const variable");

    try{
      setter.invoke(owner, value);
    }catch(Throwable e){
      throw new IllegalHandleException(e);
    }
  }
}
