package dynamilize.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**<strong>此注解依赖{@linkplain DynamicContext 动态上下文标识}！您必须在所在环境外层添加动态上下文标识才能使用此注解</strong>
 * <br>实例化变量的动态化语句标志，用于声明一个java语义的动态对象实例化语句
 * <p>具体来讲，此注解的作用即将形如<pre>{@code
 * [modifiers] Type variableName = new Type(<args...>)
 * }</pre>的语句转换为创建一个动态代理对象，这可以是字段声明，亦可以是方法中的局部变量声明，其中等号右侧必须是一个new语句。
 * <br>注意，右侧的new语句不得携带匿名扩展块，<i>在未来的更新中可能会添加对此的支持</i>
 * <br>假如您实例化的是一个非静态内部类，形如{@code outer.new Inner(<args...>)}同样也是有效的
 *
 * <p>实例化动态代理对象声明的参数从注解的属性内提供，此注解的参数：
 * <ul>
 *   <li><strong>{@link NewDynamic#refDynamicMaker()}</strong> - 引用{@linkplain dynamilize.DynamicMaker 动态工厂}的语句</li>
 *   <li><strong>{@link NewDynamic#dynClassName()}</strong> - 此动态对象所用的{@linkplain dynamilize.DynamicClass 动态类型}名称</li>
 *   <li><strong>{@link NewDynamic#impls()}</strong> - 该动态委托应用的额外接口表</li>
 *   <li><strong>{@link NewDynamic#aspects()}</strong> - 该动态委托的目标{@linkplain dynamilize.runtimeannos.AspectInterface 切面接口}</li>
 * </ul>
 * 使用上述的参数，此注解会将一个满足条件的语句变换为创建动态对象，例如：
 * <pre>{@code
 * //假设，在上文已经标记了一个动态工厂"maker"
 * @NewDynamic(dynClassName = "SampleDyc", impls = {Collection.class, Runnable.class})
 * Object dyObj = new Object();
 * }</pre>
 * 那么，经过处理编译后的等效代码是这样的：
 * <pre>{@code
 * Object dyObj = maker.newInstance(Object.class, new Class<?>[]{Collection.class, Runnable.class}, (Class<?>[])null, DynamicClass.get("SampleDyc")).objSelf();
 * }</pre>
 * 如果在new调用构造函数时有传入参数，参数会正确的添加在上述的语句最后一个参数处。一个比较特殊的地方在于，为了内存性能，
 * 您传递的接口列表（额外接口和切面接口）都会被处理为一个存在于类中的静态常量以供重复调用，对于列表接口类型相同的一组接口会复用同一个常量接口表
 * <br>
 * <br>
 * <p>另外，如果您声明的变量类型是{@link dynamilize.DynamicObject}，同时将右侧表达式添加类型转换到DynamicObject，此时生成的语句将直接获取原始的动态对象，
 * 而不调用其{@code objSelf()}方法，举例来说，假设：
 * <pre>{@code
 * @NewDynamic(dynClassName = "SampleDyc", impls = {Collection.class, Runnable.class})
 * DynamicObject<Object> dyObj = (DynamicObject<Object>)new Object();
 * }</pre>
 * 那么，经过处理编译后的等效代码是这样的：
 * <pre>{@code
 * DynamicObject<Object> dyObj = maker.newInstance(Object.class, new Class<?>[]{Collection.class, Runnable.class}, (Class<?>[])null, DynamicClass.get("SampleDyc"));
 * }</pre>
 * 简单来说这个注解的作用就是让您更便捷的使用java语句创建一个动态代理对象，而不需要使用maker的{@code newInstance(...)}方法
 * <p>关于此注解的作用细节，请参阅本注解的参数说明*/
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface NewDynamic {
  /**创建动态对象使用的{@linkplain dynamilize.DynamicMaker 动态工厂}引用语句，这会直接被整合为一段java语句插入到newInstance调用中，
   * <p>默认使用动态上下文中被标记的动态工厂，并且通常情况下也不推荐设置直接引用语句*/
  String refDynamicMaker() default "<none>";
  /**此动态代理对象的动态类型名称，在生成的语句中直接调用{@code DynamicClass.get(...)}方法来获取指定的动态类型*/
  String dynClassName();
  /**动态委托要实现的额外接口列表*/
  Class<?>[] impls() default {};
  /**动态委托要实现的{@linkplain dynamilize.runtimeannos.AspectInterface 切面接口}列表*/
  Class<?>[] aspects() default {void.class};
}
