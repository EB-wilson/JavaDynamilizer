package com.github.ebwilson.sample_zh;

import dynamilize.*;
import dynamilize.runtimeannos.AspectInterface;
import dynamilize.runtimeannos.Super;
import dynamilize.runtimeannos.This;

import java.util.Date;
import java.util.HashMap;

/**这是动态对象的基本使用示例，在下面给出了一个使用动态对象的工作流以向您演示动态对象和动态类型的基本使用方法
 * <br>请首先阅读此案例并掌握动态类型及动态对象的基本使用方法后再阅读应用的案例*/
public class BaseUse {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {

    DynamicMaker maker = DynamicFactory.getDefault();//获取默认动态工厂，关于自定义动态工厂实现请参阅高级应用

    DynamicClass Sample = DynamicClass.get("Sample");//创建动态类型，这是必要的，无论动态类是否有行为，get方法会返回给定名称的动态类型，如果类型尚不存在则会创建一个新的动态类型

    /*===============================
     * 如下所示是动态对象的基本使用方法
     * 该段落展示最基本的动态对象的行为操作
     * ===============================*/

    //使用动态工厂来实例化动态对象，传递一个切面接口限定动态委托的范围，若限定切面为null，那么动态实例将对所有可见方法进行动态委托
    HashMap<String, String> map = maker.newInstance(HashMap.class, new Class[]{Example.class}, Sample).objSelf();

    //强转换为动态对象以调用行为，但实际上maker的newInstance方法创建返回的类型就是DynamicObject，objSelf()方法只是对对象this的一次强类型转换，此处因为泛型转换的问题没有直接使用返回的DynamicObject实例
    DynamicObject<HashMap<String, String>> dyMap = (DynamicObject<HashMap<String, String>>) map;
    //设置行为，对put方法进行监视：
    dyMap.setFunc("put", (self, su, arg) -> {
      su.invokeFunc("put", arg);//调用超方法，等价于super.put(key, value)

      System.out.println("putting " + arg.<String>get(0) + " -> " + arg.<String>get(1));
    }, Object.class, Object.class);//泛型擦除，对泛型参数的限定需使用类型参数的上界，此方法上界未定，即Object

    //测试打印
    map.put("a", "string 1");
    map.put("b", "string 2");
    map.put("c", "string 3");

    System.out.println("\n===========sep line==========\n");

    //对于动态对象，可以像脚本语言一样使用钩子函数：
    Function<HashMap<String, String>, Object> hook = dyMap.getFunc("put", Object.class, Object.class);
    dyMap.setFunc("put", (self, arg) -> {
      hook.invoke(self, arg);//调用钩子
      System.out.println("putting now, current entries amount: " + self.objSelf().size());
    }, Object.class, Object.class);

    //测试打印
    map.put("d", "string 4");
    map.put("e", "string 5");
    map.put("f", "string 6");

    System.out.println("\n===========sep line==========\n");

    //您也可以用反射式的语句调用动态对象的方法：
    System.out.println("reflectional invoke:");
    dyMap.invokeFunc("put", "g", "string 7");

    //当然，您也可以以类似的方式去操作对象的字段值:
    System.out.println("current entries amount: " + dyMap.getVar("size"));

    /*=======================================================
     * 以上是动态对象上的基本操作，到这里您可能对动态类型的作用稍有疑惑
     * 您将在这个段落了解到动态类型的用法及意义
     * =======================================================*/

    System.out.println("\n===========sep line==========\n");
    //上面已经定义过一个动态类型Sample，直接使用它，首先给动态类型声明一个函数：
    Sample.setFunction("put", (self, su, arg) -> {
      System.out.println("Dynamic class instance handle:");
      su.invokeFunc("put", arg);//这里的语义和对动态类型直接定义是一致的
    }, Object.class, Object.class);

    //测试打印，观察打印结果，可以发现调用的过程中最低层次的调用发生了变化
    //其实，在动态对象中除部分方法对java方法的引用外，最低层的函数引用是指向动态类型的，只是这一层的默认行为是向上调用或者无行为
    //因此，当你改变动态类型中描述的行为时，所有没有中断掉向上的引用的这个动态类型的实例，此方法的行为都会随之改变
    map.put("i", "string 8");
    map.put("j", "string 9");
    map.put("k", "string 10");

    System.out.println("\n===========sep line==========\n");

    //同样的，如果新增函数
    Sample.setFunction("putTime", (self, arg) -> {
      System.out.println("current time: " + new Date());
      self.invokeFunc("put", arg.get(0), new Date().toString());
    }, String.class);
    dyMap.invokeFunc("putTime", "currTime");

    //从map中获取currTime，可见给动态类型设置的行为被同步到了它的实例当中
    System.out.println("get time entry, res: " + map.get("currTime"));

    System.out.println("\n===========sep line==========\n");

    //当然，使用lambda为对象设置行为有些繁琐，为动态类型定义方法还有更为方便的形式
    //在下方，我们定义了一个类型Template作为类型样板，在此使样板类型生效
    Sample.visitClass(Template.class, maker.getHelper());

    //测试打印
    System.out.println("get: " + map.get("a"));
    System.out.println("get: " + map.get("b"));
    System.out.println("get: " + map.get("c"));

    System.out.println("\n===========sep line==========\n");

    //另外，动态类型也是可以被继承的，就像如下所示
    DynamicClass SampleChild = DynamicClass.declare("SampleChild", Sample);
    HashMap<String, String> map1 = maker.newInstance(HashMap.class, new Class[]{Example.class}, SampleChild).objSelf();

    SampleChild.setFunction("put", (self, su, arg) -> {
      su.invokeFunc("put", arg);
      System.out.println("current map: " + self);
    }, Object.class, Object.class);

    //测试打印，观察结果，对象从其超类继承了put方法的行为，而此类型定义的行为紧随之后执行，容易看出动态类型继承的行为和java本身的类继承行为非常相似
    map1.put("abc", "extend sample 1");
    map1.put("def", "extend sample 2");
    map1.put("ghi", "extend sample 3");

    //至此，关于动态类型的基本的使用方法说明就结束了，接下来会在案例中的其他文件中展示一些具体的应用，请首先将基本使用方法掌握后再阅读后续的案例
  }

  //使用java定义方法的模板，令对象直接从java类型中引用它的方法，使程序语义更清晰
  static class Template{
    //用于定义行为的方法必须是静态的，this指针通过第一个参数上添加@This注解传入，super则是第二个参数使用@Super注解，位置不可交换，但可以省略，譬如此处的self其实就是不必要的
    //在this和super之后的参数配合方法的名称进行签名，这个例子的签名等价于 public Object get(Object key)，会覆写map的行为

    //另外，使用动态类型时，this指针的类型就是不确定的了，所以通常来说不指定它的泛型限定类，但对于只会用于一个合法的java类层次结构的动态类，你可以为它分配一个确切的类型以更方便的使用，
    public static Object get(@This DynamicObject<?> self, @Super DataPool.ReadOnlyPool su, Object key){
      System.out.println("getting entries, key: " + key);
      return su.invokeFunc("get", key);//此处类型不确切，所以使用反射调用方法
    }
  }

  /**定义切面接口，用于限定动态委托的处理范围*/
  @AspectInterface
  public interface Example{
    void put(Object k, Object v);
    Object get(Object k);
  }
}