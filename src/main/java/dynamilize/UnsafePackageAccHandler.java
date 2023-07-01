package dynamilize;

import dynamilize.classmaker.AbstractClassGenerator;
import dynamilize.classmaker.ClassInfo;
import jdk.internal.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**基于{@link Unsafe}实现的强制保护域类加载处理程序
 *
 * @author EBwilson
 * @since 1.6*/
public class UnsafePackageAccHandler extends PackageAccHandler{
  private static final Unsafe unsafe;

  static{
    try{
      Demodulator.makeModuleOpen(Object.class.getModule(), "jdk.internal.misc", UnsafePackageAccHandler.class.getModule());

      Constructor<Unsafe> cstr = Unsafe.class.getDeclaredConstructor();
      cstr.setAccessible(true);
      unsafe = cstr.newInstance();
    }catch(NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e){
      throw new RuntimeException(e);
    }
  }

  private final AbstractClassGenerator generator;

  public UnsafePackageAccHandler(AbstractClassGenerator generator){
    this.generator = generator;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> Class<? extends T> loadClass(ClassInfo<?> clazz, Class<T> baseClass) {
    byte[] bytes = generator.genByteCode(clazz);

    return (Class<? extends T>) unsafe.defineClass(clazz.name(), bytes, 0, bytes.length, baseClass.getClassLoader(), null);
  }
}
