package dynamilize;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**对java字段的引用对象，基于{@linkplain MethodHandle 方法句柄}的默认内部实现
 *
 * @author EBwilson */
public class JavaVariable implements IVariable{
  private final Field field;

  private final MethodHandle getter;
  private final MethodHandle setter;

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  public JavaVariable(Field field){
    this.field = field;

    try {
      getter = LOOKUP.unreflectGetter(field);
      setter = LOOKUP.unreflectSetter(field);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String name(){
    return field.getName();
  }

  @SuppressWarnings("unchecked")
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
    try{
      setter.invoke(obj, value);
    }catch(Throwable e){
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public boolean get(DynamicObject<?> obj, boolean def) {
    try {
      return field.getBoolean(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public byte get(DynamicObject<?> obj, byte def) {
    try {
      return field.getByte(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public short get(DynamicObject<?> obj, short def) {
    try {
      return field.getShort(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public int get(DynamicObject<?> obj, int def) {
    try {
      return field.getInt(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public long get(DynamicObject<?> obj, long def) {
    try {
      return field.getLong(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public float get(DynamicObject<?> obj, float def) {
    try {
      return field.getFloat(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public double get(DynamicObject<?> obj, double def) {
    try {
      return field.getDouble(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, boolean value) {
    try {
      field.setBoolean(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, byte value) {
    try {
      field.setByte(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, short value) {
    try {
      field.setShort(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, int value) {
    try {
      field.setInt(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, long value) {
    try {
      field.setLong(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, float value) {
    try {
      field.setFloat(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, double value) {
    try {
      field.setDouble(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
