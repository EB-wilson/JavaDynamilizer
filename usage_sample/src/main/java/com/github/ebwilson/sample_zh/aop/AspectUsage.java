package com.github.ebwilson.sample_zh.aop;

import dynamilize.*;
import dynamilize.runtimeannos.AspectInterface;
import dynamilize.runtimeannos.FuzzyMatch;

/**本篇案例演示的是更好的面向切面用法，在您阅读本篇前请务必了解{@linkplain dynamilize.DynamicClass 动态类型}的使用及行为效果，以理解本篇中各行为的效果
 *
 * <br>正如本例子开头给出的三个类，多数情况下，面向对象的思想会指导开发者对一个类扩展多个子类进行编写，
 * 但是类进行扩展的过程是单向的，当我们需要对这些子类继承来的共有行为进行操作时，我们可能需要一个又一个的类分别扩展一个子类，
 * 然后又在子类里写下完全一样的方法重写，这就大大提高了工作量和代码的耦合度。
 *
 * <p>一般来说，解决这个问题通常会考虑改变代码的逻辑来减少耦合度，比如提升共同行为至基类，但通常这不是很好的办法，因此我们引入了面向切面编程，即AOP
 * <p>AOP思想指导开发者只关注多个类型的共有行为（这甚至可以不是从一个基类继承而来），而本框架更进一步的将其视为“可变的”，请继续往下阅读</p>*/
public class AspectUsage {
  /**这是一个基类，它描述了几个方法，关注这些方法，这会是子类当中的共有特征*/
  public static class ClassA{
    public void update(){}
    public void draw(){}
    public void showInfo(){
      System.out.println("Now here is `ClassA`");
    }
    public void showInfo(String string){
      System.out.println("Here is `ClassA`'s info: " + string);
    }
  }

  //下面则是对ClassA的两个分别扩展的子类，思考一下，假设我们需要让ClassB和ClassC的实例具有如下效果：
  //×-在调用update()时，在执行后打印update方法被调用的次数
  //×-在调用draw()方法时，在draw的首行前增加一行和末尾追加一行，分别打印"--------------------"
  //×-在调用两个showInfo时，分别在之前添加一个"> "

  //另外，当子类远远不只有ClassB和ClassC的情况呢？

  public static class ClassB extends ClassA {
    public void update(){
      System.out.println("updating");
    }
    public void draw(){
      System.out.println("- - - - -");
    }
    public void showInfo(){
      System.out.println("Now here is `ClassB`");
    }
    public void showInfo(String string){
      System.out.println("Here is `ClassB`'s info: " + string);
    }
  }

  public static class ClassC extends ClassA {
    public void reset(){
      System.out.println("reset! (Actually did nothing)");
    }
    public void draw(){
      System.out.println("* * * * *");
    }
    public void showInfo(){
      System.out.println("Now here is `ClassC`");
    }
    public void showInfo(String string){
      System.out.println("Here is `ClassC`'s info: " + string);
    }
  }

  //请继续往下看，现在，创建一个DynamicMaker，此处使用默认工厂
  static DynamicMaker maker = DynamicFactory.getDefault();

  /**现在，针对上述的基类创建一个切面接口*/
  @AspectInterface
  public interface ClassAspect{
    void update();
    void draw();
    /**本方法标记为{@linkplain FuzzyMatch 模糊匹配}，属性{@linkplain FuzzyMatch#anySameName() anySameName}标记此方法匹配所有同名重载*/
    @FuzzyMatch(anySameName = true)
    void showInfo();
  }

  //开始
  public static void main(String[] args) {
    //创建一个动态类
    DynamicClass SampleAspect = DynamicClass.get("SampleAspect");

    SampleAspect.setVariable("updateCounter", 0);
    //下面，我们描述这个切面动态类进行的所有操作
    SampleAspect.setFunction("update", (s, su, arg) -> {
      su.invokeFunc("update", arg);
      System.out.println("update invoked counter: " + s.calculateVar("updateCounter", (int i) -> i + 1));
    });
    SampleAspect.setFunction("draw", (s, su, arg) -> {
      System.out.println("--------------------");
      su.invokeFunc("draw", arg);
      System.out.println("--------------------");
    });
    //共同行为，采取同一函数同时设置为两个签名的重载方法
    Function.NonRetSuperGetFunc<?> fun = (s, su, arg) -> {
      System.out.print("> ");
      su.invokeFunc("showInfo", arg);
    };
    SampleAspect.setFunction("showInfo", fun);
    // -> public void showInfo(){...}
    SampleAspect.setFunction("showInfo", fun, String.class);
    // -> public void showInfo(String string){...}

    //接下来，我们分别创建两个类的切面代理对象：
    DynamicObject<ClassB> instB = maker.newInstance(ClassB.class, new Class[]{ClassAspect.class}, SampleAspect);
    DynamicObject<ClassC> instC = maker.newInstance(ClassC.class, new Class[]{ClassAspect.class}, SampleAspect);

    //测试打印
    System.out.println("testing instance B:");
    ClassB b = instB.objSelf();
    b.update();
    b.draw();
    b.showInfo();
    b.showInfo("sample test");

    System.out.println("testing instance C:");
    ClassC c = instC.objSelf();
    c.update();
    c.draw();
    c.showInfo();
    c.showInfo("sample test");

    c.reset();

    //显然，这大大的降低了代码的耦合度和工作量，你可以使用更少的代码来完成类似的工作，尤其在子类分支远远更多的情况下
  }
}
