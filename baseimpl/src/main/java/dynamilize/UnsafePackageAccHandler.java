package dynamilize;

import dynamilize.classmaker.AbstractClassGenerator;
import dynamilize.classmaker.ClassInfo;
import jdk.internal.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**基于{@link jdk.internal.misc.Unsafe}实现的强制保护域类加载处理程序
 *
 * @author EBwilson
 * @since 1.6*/
public class UnsafePackageAccHandler extends PackageAccHandler{
  private static final Object unsafe;

  static{
    try{
      Demodulator.makeModuleOpen(Object.class.getModule(), "jdk.internal.misc", UnsafePackageAccHandler.class.getModule());

      Constructor<?> cstr = Unsafe.class.getDeclaredConstructor();
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

  @Override
  protected <T> Class<? extends T> loadClass(ClassInfo<?> clazz, Class<T> baseClass) {
    return defineClass(clazz.name(), generator.genByteCode(clazz), baseClass.getClassLoader());
  }

  @SuppressWarnings("unchecked")
  static <T> Class<? extends T> defineClass(String name, byte[] bytes, ClassLoader loader){
    return (Class<? extends T>) ((Unsafe)unsafe).defineClass (name, bytes, 0, bytes.length, loader, null);
  }
}
