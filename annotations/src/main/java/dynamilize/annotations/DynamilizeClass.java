package dynamilize.annotations;

import dynamilize.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**此注解用于将一个一般的java类型转化为动态类型记录
 * <p>具体来说，一个具备此注解的类，其中声明的各种方法语句的上下文都会被调整为适配到{@link DynamicClass#visitClass(Class, JavaHandleHelper)}的语义，
 * 并保存其对应的动态类型对象及名称。该注解并不会递归的处理被标记类中的内部类，但是被标记的类中<strong>不能有任何非静态的内部类</strong>
 * <p>此标记转换的动态类类名由DynamilizeClass.{@link DynamilizeClass#className()}给出，默认使用标记的类类名，细节请参阅这个注解属性
 *
 * <br>
 * <br>
 * <p><strong>警告：此注解转换的动态类型标记可能并不能可靠的加载，在您使用这个注解生成的动态类之前，请通过调用类的任意字段/方法来确保此类型已加载</strong></p>
 * <p><strong>特别注意，在注解{@link NewDynamic}中传递该类的NAME常量并不能使jvm加载这个类，因为NAME会被转化为常量提供给注解参数，
 * 因此在通过上下文注解引用的该动态类并不能可靠的加载此动态类</strong></p>
 * <br>
 *
 * <p>以下是注解的工作细节：
 * <ul>
 *   <li>首先，检查该类型中的声明是否合法，而后在其中搜索{@linkplain JavaHandleHelper 辅助器}，向其之后添加两个字段
 *   {@code public static final DynamicClass INSTANCE}和{@code public static final String NAME}分别保存对动态类实例的引用和动态类类名，
 *   这两个字段都会添加{@link dynamilize.runtimeannos.Exclude}以排除，其中INSTANCE的初始化在后文会提到</li>
 *   <li>接着，注解处理器会在此类上下文中定位每一个字段引用和方法引用，确定这些引用的目标是否为this或者super，若引用的目标满足且是一个隐式成员引用，
 *   那么会将this和super添加为显式声明</li>
 *   <li>遍历此类所有方法，将所有成员方法全部变为静态方法，并在参数前两位添加符合{@link DynamicClass#visitMethod(Method, JavaHandleHelper)}规则的self参数和sup参数，
 *   而对于声明的静态方法则添加{@link dynamilize.runtimeannos.Exclude}注解从声明中排除</li>
 *   <li>然后，将所有符合规则的成员方法中对本类上下文引用的语句转换为通过self参数传入的动态对象的方法调用，例如{@code this.run()}会被替换为{@code self.invokeFunc("run")}，
 *   但是默认情况下这个对run的调用会添加方法签名以快速定位函数，关于签名选项，请参阅{@link DynamilizeClass#signatureMethods()}</li>
 *   <li>最后，处理所有声明的字段，对成员字段会按照符合{@link DynamicClass#visitField(Field)}的规范变换，若其具有初始化数据，
 *   当初始化数据为常量值时会直接将字段设置为static，否则，字段的初始化语句会被包装为{@link dynamilize.Initializer.Producer}，且字段类型也会同步调整。
 *   对于静态字段则会简单的像是对静态方法一样添加注解进行排除</li>
 * </ul>
 *
 * <p>此外，被标记为动态类型记录的类也可以扩展一个动态类型记录，这会对INSTANCE字段的初始化产生影响，具体来说，初始化语句都是调用{@link DynamicClass#visit(String, Class, DynamicClass, JavaHandleHelper)}
 * 来直接获取动态类型实例，而扩展会影响的就是其最后一个参数，直观来看<pre>{@code
 * //一个没有扩展的动态类型记录中，INSTANCE的等价初始化语句是这样的：
 * public static final DynamicClass INSTANCE = DynamicClass.visit(NAME, <ThisClass>.class, null, $helper$);
 * //而有扩展一个父类的动态类型记录中，INSTANCE的等价初始化语句是这样的：
 * public static final DynamicClass INSTANCE = DynamicClass.visit(NAME, <ThisClass>.class, <SuperClass>.INSTANCE, $helper$);
 * }</pre>
 * 值得关注的是其中的字段{@code $helper$}，这个字段是保存用于访问java类使用的辅助器的静态字段，
 * 您同样必须在原始的类型定义中创建指定名称的java静态字段，否则将无法通过编译，关于选择这个字段的细节，请参阅DynamilizeClass.{@link DynamilizeClass#helperField()}
 *
 * <p>另外，在您使用这个注解标记类型时，将类型标记为abstract并实现{@link DynamilizedClass}接口是一种不错的选择，该接口包含了INSTANCE字段和NAME字段，
 * 这是一种魔法操作，例如，在java中，您可以直接对被标记的类调用INSTANCE字段和NAME字段，但这是通过类转移的调用，实际上会指向{@link DynamilizedClass}接口的字段，
 * 但是在注解处理后，被标记类中会被添加这两个字段，从外部访问这个类型的INSTANCE和NAME字段的目标就会被确定而非重定向到DynamilizedClass接口,
 * 具体细节请参阅{@link DynamilizedClass}*/
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DynamilizeClass {
  /**应用于动态类的名称，这会为此类的NAME字段设置值，默认在不设置这个名称的情况下，注解处理器会使用被标记的类的完整类名作为该动态类的名称*/
  String className() default "<none>";
  /**此类型动态化时所使用的静态java辅助器存储字段名称，在将类型声明转化为动态类的过程中，会在类的一级声明中搜索这个字段，
   * 并将INSTANCE字段添加在其后方以确保有效的调用此字段，默认为"$helper$"，您可以自行指定字段名称*/
  String helperField() default "$helper$";
  /**是否对所有动态化的方法调用添加方法签名，关于方法签名的性能影响，可参阅{@link DataPool#select(String, FunctionType)}，若您将此设置为true，
   * 那么所有从方法调用转换来的动态方法引用都会标记其调用的方法签名类型，并在调用时传入以提升性能
   * <p>您可以根据需要设置此选项，假如内存性能十分重要，则推荐设为false，否则通常来说设为true都能提升时间性能，尤其是在动态类型多次扩展嵌套的情况下*/
  boolean signatureMethods() default true;

  /**辅助类型动态声明的魔法接口，通常在您对一个类型标记为{@link DynamilizeClass}时，建议将类型设置为abstract并添加实现此接口
   * <p>接口扩展自{@link DynamicObject}，以便在您描述类型行为时可以直接使用动态对象的方法，值得注意的是，
   * 您在此上下文中调用属于{@link DynamicObject}的API是不会被再次转化为动态调用动态方法的，即这些API会被注解处理器略过，仅仅将调用对象重定向到self参数
   * <p>此外，该接口中定义了两个常量字段和一个方法，字段用作在java语义中直接引用该类型的INSTANCE字段和NAME字段，在经过注解处理器处理后这两个字段不会被重定向到该接口。
   * <p>方法{@link DynamilizedClass#super$()}则是对动态super指针的虚拟引用，以便您可以以动态描述的方法那样调用super池的行为，对这个方法的调用在注解处理后会被转化为对sup参数的引用*/
  interface DynamilizedClass<T> extends DynamicObject<T> {
    DynamicClass INSTANCE = DynamicClass.get("<dynamicBase>");
    String NAME = "";

    default DataPool.ReadOnlyPool super$(){ return null; }
  }
}
