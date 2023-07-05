package dynamilize.annotation;

import dynamilize.DynamicClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**此注解用于声明一个接口为切面接口类型，在{@link dynamilize.DynamicMaker}中构造动态实例时传入的切面接口需要携带此注解。
 * <p>当一个接口携带该注解时，它应当满足如下规范：
 * <ul>
 *   <li><strong>接口中不得有非抽象的默认方法</strong></li>
 *   <li><strong>接口所扩展的所有接口必须也是切面接口</strong></li>
 *   <li><strong>接口所扩展的切面接口中也不得存在非抽象方法</strong></li>
 * </ul>
 *
 * 切面接口扩展的所有切面接口会迭代的将所有方法添加为切面行为，在{@link dynamilize.DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)}方法中亦可以传入若干个切面接口，
 * 所有的切面接口共同组合为这个动态实例上应用的切面。
 *
 * <p>切面中的行为，其作用在于创建动态实例的增强类型时，在对象的方法中定位将被委托的方法，即限定动态化过程会作用到的方法，从一个案例理解：
 * <pre>{@code
 * 一个切面接口：
 * @AspectInterface
 * public interface Sample{
 *   void put(Object key, Object value);
 *   Object get(Object key);
 *   Object remove(Object key);
 * }
 *
 * 现在，使用这个切面接口，创建一个动态化的HashMap：
 * DynamicMaker maker = DynamicFactory.getDefault();
 * DynamicObject<HashMap<String, String>> map = maker.newInstance(HashMap.class, new Class[]{Sample.class}, DynamicClass.get("Temp"));
 *
 * map.setFunction("put", (self, su, arg) -> {
 *   System.out.println("putting:" + arg.get(0) + "-" + arg.get(1));
 *   su.invokeFunc("put", arg);
 * }, Object.class, Object.class);//put 方法在切面声明中存在，该行为可被监视
 *
 * map.setFunction("clear", (self, su, arg) -> {
 *   System.out.println("clearing");
 *   su.invokeFunc("clear", arg);
 * });//clear 未在切面中声明，此处声明的行为只会向动态对象添加一个名为clear的函数，但不会对clear方法本身进行监视
 * }</pre>
 *
 * 您也可以不提供切面接口，在创建动态实例时，若将切面接口表设置为null，那么此动态对象会是<strong>全委托</strong>的，
 * <br>即此动态实例会将所有的<strong>包括继承来的非private，非final，非static</strong>方法启用动态化。
 * <br><strong>注意！</strong>全委托会对所有的被处理方法创建增强字节码，这个过程的成本可能十分昂贵，
 * 在您可以确定切面范围的情况下，尽可能采用切面限定委托范围，而不是广泛的使用全委托*/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AspectInterface {
}