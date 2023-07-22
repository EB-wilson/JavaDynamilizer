package dynamilize;

import dynamilize.classmaker.ClassInfo;
import dynamilize.classmaker.CodeBlock;
import dynamilize.classmaker.Parameter;
import dynamilize.classmaker.code.IClass;
import dynamilize.classmaker.code.ILocal;
import dynamilize.classmaker.code.IMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**包私有方法访问提升使用的工具类，该类用于对一个目标类创建一串用于提升包私有方法的继承子树，以达到对包私有方法进行重写的目的
 * <br>具体来说，将目标基类传入{@link PackageAccHandler#handle(Class)}方法以后，会依次检查此类型的，每一个父类型中是否存在包私有的方法，
 * 若存在包私有方法，那么就会使用这个类的包名对上一层类型继承一次，通过{@link PackageAccHandler#loadClass(ClassInfo, Class)}将这个类加载到同一保护域，
 * 此时，此次继承类型即可访问目标类包私有方法，重写并将其访问修饰符提升为{@code protected}，如此完成对基类所有超类的遍历后即可访问所有该类层次结构中的包私有方法
 *
 * <p>直观的说，这个类做的事情如下：
 * <pre>{@code
 * 假设有下面几个类：
 *
 * package pac1;
 * public class A{
 *   void method1(){ //包私有方法
 *
 *   }
 *
 *   public void test(){
 *     method1();
 *   }
 * }
 *
 * package pac2;
 * import pac1.A;
 * public class B extends A{
 *   void method2(){ //包私有方法
 *
 *   }
 *
 *   @Override
 *   public void test(){
 *     super.test();
 *     method2();
 *   }
 * }
 *
 * 如果对handle方法传入B类，那么会创建如下两个类型：
 *
 * package pac2:
 * public class B$packageAccess$0 extends B{
 *   @Override
 *   protected void method2(){
 *     super.method2();
 *   }
 * }
 *
 * package pac1:
 * import pac2.B$packageAccess$0;
 * public class A$packageAccess$0 extends B$packageAccess$0{
 *   @Override
 *   protected void method1(){
 *     super.method2();
 *   }
 * }
 *
 * 最后handle方法会将类对象pac1.A$packageAccess$0返回，此时，所有包私有方法都已被提升为protected，其子类对两个包私有方法的重写，在调用test方法时都会生效
 * }</pre>
 * <strong>注意：<ul>
 *   <li>以上所示的跨包继承同包类的方法重写实际上在java编译器中是不合法的，但在JVM当中此逻辑有效，上述代码仅作逻辑示意</li>
 *   <li>由于java包命名空间限制的问题，无法正常从外部加载java开他包名的类型，所以开放包私有方法对包名以“java.”开头的类不生效</li>
 * </ul></strong>
 *
 * @author EBwilson
 * @since 1.6*/
@SuppressWarnings("unchecked")
public abstract class PackageAccHandler {
  public static final int PAC_PRI_FLAGS = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
  public static final ILocal[] A = new ILocal[0];
  private final Map<Class<?>, Class<?>> classMap = new HashMap<>();

  public <T> Class<? extends T> handle(Class<T> baseClass) {
    return (Class<? extends T>) classMap.computeIfAbsent(baseClass, c -> {
      Class<?> curr = c;
      Class<?> opening = null;

      while (curr != null) {
        if (shouldOpen(curr)) {
          ClassInfo<?> ci = makeClassInfo(opening == null ? ClassInfo.asType(curr) : ClassInfo.asType(opening), curr);
          opening = loadClass(ci, curr);
        }

        curr = curr.getSuperclass();
      }

      return opening == null? c: opening;
    });
  }

  protected boolean shouldOpen(Class<?> checking){
    if (checking.getPackageName().startsWith("java.")) return false;

    for (Method method : checking.getDeclaredMethods()) {
      if ((method.getModifiers() & PAC_PRI_FLAGS) == 0){
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("rawtypes")
  protected <T> ClassInfo<? extends T> makeClassInfo(ClassInfo<?> superClass, Class<?> base){
    ClassInfo<?> res = new ClassInfo<>(
        Modifier.PUBLIC,
        base.getName() + "$packageAccess$" + superClass.name().hashCode(),
        superClass
    );

    ClassInfo<?> baseC = ClassInfo.asType(base);

    for (Method method : base.getDeclaredMethods()) {
      if ((method.getModifiers() & PAC_PRI_FLAGS) == 0){
        IMethod<?, ?> sup = baseC.getMethod(
            ClassInfo.asType(method.getReturnType()),
            method.getName(),
            Arrays.stream(method.getParameterTypes()).map(ClassInfo::asType).toArray(ClassInfo[]::new)
        );

        CodeBlock<?> code = res.declareMethod(
            Modifier.PROTECTED,
            method.getName(),
            ClassInfo.asType(method.getReturnType()),
            Parameter.asParameter(method.getParameters())
        );

        ILocal<T> self = code.getThis();
        if (method.getReturnType() == void.class){
          code.invokeSuper(
              self,
              sup,
              null,
              code.getParamList().toArray(A)
          );
        }
        else{
          ILocal ret = code.local(ClassInfo.asType(method.getReturnType()));

          code.invokeSuper(
              self,
              sup,
              ret,
              code.getParamList().toArray(A)
          );
          code.returnValue(ret);
        }
      }
    }

    for (Constructor<?> constructor : superClass.getTypeClass().getDeclaredConstructors()) {
      if ((constructor.getModifiers() & PAC_PRI_FLAGS) == 0 && !(base.getPackage().equals(superClass.getTypeClass().getPackage()))) continue;
      if ((constructor.getModifiers() & (Modifier.PRIVATE | Modifier.FINAL)) != 0) continue;

      IMethod<?, ?> su = superClass.getConstructor(Arrays.stream(constructor.getParameterTypes()).map(ClassInfo::asType).toArray(IClass[]::new));

      CodeBlock<?> code = res.declareConstructor(Modifier.PUBLIC, Parameter.asParameter(constructor.getParameters()));
      code.invoke(code.getThis(), su, null, code.getParamList().toArray(A));
    }

    return (ClassInfo<? extends T>) res;
  }

  /**将目标{@linkplain ClassInfo 类型信息}加载为与基类型同一保护域的对象，根据目标运行平台实现该方法*/
  protected abstract <T> Class<? extends T> loadClass(ClassInfo<?> clazz, Class<T> baseClass);
}
