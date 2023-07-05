package dynamilize;

import dynamilize.classmaker.ASMGenerator;
import dynamilize.classmaker.AbstractClassGenerator;
import dynamilize.classmaker.BaseClassLoader;
import dynamilize.classmaker.ByteClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**用于创建{@linkplain DynamicMaker 动态生成器}的工厂类，内部提供了默认生成器的声明
 *
 * @author EBwilson
 * @since 1.6*/
public class DynamicFactory {
  protected PackageAccHandler handler;
  protected AbstractClassGenerator generator;
  protected JavaHandleHelper helper;

  private static final DynamicFactory DEFAULT = new DynamicFactory(){{
    setDefaultGenerator();
    setDefaultHelper();
    setPackageAccHandler(new UnsafePackageAccHandler(generator));
  }};

  /**直接获取默认的通用实现实例，该方法获取的动态生成器等价于：
   * <pre>{@code
   * new DynamicFactory().setDefaultGenerator().setDefaultHelper().getMaker();
   * }</pre>
   *
   * @see DynamicFactory#setDefaultGenerator()
   * @see DynamicFactory#setDefaultHelper()
   * @return 一个由默认声明生产的动态生成器*/
  public static DynamicMaker getDefault(){
    return DEFAULT.getMaker();
  }

  /**获取{@linkplain DynamicMaker 动态生成器}实例
   *
   * @throws NullPointerException 若有必要的属性尚未设置*/
  public DynamicMaker getMaker(){
    PackageAccHandler handler = this.handler;

    AbstractClassGenerator generator = this.generator;

    Objects.requireNonNull(helper);
    Objects.requireNonNull(generator);

    return handler == null? new DynamicMaker(helper) {
      @Override
      protected <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces, Class<?>[] aspects) {
        return makeClassInfo(baseClass, interfaces, aspects).generate(generator);
      }
    }: new DynamicMaker(helper) {
      @Override
      protected <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces, Class<?>[] aspects) {
        return makeClassInfo(baseClass, interfaces, aspects).generate(generator);
      }

      @Override
      protected <T> Class<? extends T> handleBaseClass(Class<T> baseClass) {
        return handler.handle(baseClass);
      }
    };
  }

  /**设置{@linkplain PackageAccHandler 包私有访问处理器}，若不设置则动态生成器不对超类的包私有方法进行委托
   *
   * @see PackageAccHandler*/
  public DynamicFactory setPackageAccHandler(PackageAccHandler handler){
    this.handler = handler;
    return this;
  }

  /**设置将{@linkplain dynamilize.classmaker.ClassInfo 类型描述}生成为Java类对象的{@linkplain AbstractClassGenerator 类型生成器}，<strong>必要！</strong>
   *
   * @see AbstractClassGenerator*/
  public DynamicFactory setGenerator(AbstractClassGenerator generator){
    this.generator = generator;
    return this;
  }

  /**设置对Java层行为进行处理的{@linkplain JavaHandleHelper Java行为支持器}，<strong>必要！</strong>
   *
   * @see JavaHandleHelper*/
  public DynamicFactory setJavaHandleHelper(JavaHandleHelper helper){
    this.helper = helper;
    return this;
  }

  /**使用{@linkplain  DynamicFactory#setDefaultGenerator(ByteClassLoader, int) 默认配置}设置类型生成器，字节码加载器使用默认实现{@link BaseClassLoader}，目标字节码版本为152（Java1.8）
   *
   * @see DynamicFactory#setDefaultGenerator(ByteClassLoader, int)
   * @see BaseClassLoader*/
  public DynamicFactory setDefaultGenerator(){
    return setDefaultGenerator(new BaseClassLoader(DynamicFactory.class.getClassLoader()), 52);
  }

  /**将类型生成器设置为{@link ASMGenerator}的实现，该实现适用于绝大多数情况的JVM
   *
   * @param loader 加载字节码为类对象的类加载器
   * @param byteLevel 目标字节码级别，最低支持50（Java1.8）
   *
   * @see ASMGenerator*/
  public DynamicFactory setDefaultGenerator(ByteClassLoader loader, int byteLevel){
    if (byteLevel < 52 || byteLevel > 255)
      throw new IllegalArgumentException("illegal byte code level: " + byteLevel);

    generator = new ASMGenerator(loader, byteLevel);

    return this;
  }

  /**设置{@link JavaHandleHelper}的默认内部实现，该实现直接引用{@link JavaVariable}和{@link JavaMethodEntry}以及默认的反模块化访问
   *
   * @see JavaVariable
   * @see JavaMethodEntry*/
  public DynamicFactory setDefaultHelper(){
    helper = new JavaHandleHelper() {
      @Override
      public void makeAccess(Object object) {
        if (object instanceof Executable ext){
          if (ext instanceof Method method) Demodulator.makeModuleOpen(method.getReturnType().getModule(), method.getReturnType(), DynamicMaker.class.getModule());

          if (ext instanceof Constructor<?> cstr) Demodulator.makeModuleOpen(cstr.getDeclaringClass().getModule(), cstr.getDeclaringClass(), DynamicMaker.class.getModule());

          for (Class<?> type : ext.getParameterTypes()) {
            Demodulator.makeModuleOpen(type.getModule(), type, DynamicMaker.class.getModule());
          }

          ext.setAccessible(true);
        }
        else if (object instanceof Field field){
          Demodulator.makeModuleOpen(field.getType().getModule(), field.getType(), DynamicMaker.class.getModule());

          field.setAccessible(true);
        }
        else if (object instanceof Class<?> clazz){
          Demodulator.makeModuleOpen(clazz.getModule(), clazz.getPackage(), DynamicMaker.class.getModule());
        }
      }

      @Override
      public IVariable genJavaVariableRef(Field field, DataPool targetPool) {
        return new JavaVariable(field);
      }

      @Override
      public IFunctionEntry genJavaMethodRef(Method method, DataPool targetPool) {
        return new JavaMethodEntry(method, targetPool);
      }
    };

    return this;
  }
}
