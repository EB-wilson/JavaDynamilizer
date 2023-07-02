package dynamilize;

import dynamilize.classmaker.AbstractClassGenerator;
import dynamilize.classmaker.ClassInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

/**基于{@link jdk.internal.misc.Unsafe}实现的强制保护域类加载处理程序
 *
 * @author EBwilson
 * @since 1.6*/
public class UnsafePackageAccHandler extends PackageAccHandler{
  private static final Object unsafe;
  private static final Method defineClass;

  static{
    try{
      Demodulator.makeModuleOpen(Object.class.getModule(), "jdk.internal.misc", UnsafePackageAccHandler.class.getModule());

      Class<?> clazz = Class.forName("jdk.internal.misc.Unsafe");

      defineClass = clazz.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);

      Constructor<?> cstr = clazz.getDeclaredConstructor();
      cstr.setAccessible(true);
      unsafe = cstr.newInstance();
    }catch(NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException |
           ClassNotFoundException e){
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

    try {
      return (Class<? extends T>) defineClass.invoke(unsafe, clazz.name(), bytes, 0, bytes.length, baseClass.getClassLoader(), null);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
