package com.github.ebwilson.sample.aop;

import dynamilize.*;
import dynamilize.runtimeannos.AspectInterface;
import dynamilize.runtimeannos.FuzzyMatch;

/** This case shows a better usage for AOP. Please first read the usage and function of {@linkplain dynamilize.DynamicClass Dynamic Object}, so that you will have a better understand of the function in this case.
 *
 * <br>As the 3 classes provided in the beginning of the code. Usually, OOP (Object-Oriented Programming) teaches developers to extends several sub-classes.
 * But the extend of a class is single-directional. When dealing with the common behavior inherited, we may extend another sub-class over and over again.
 * And then we write the same overriding code in the each of the sub-classes, which increases workloads and coupling.
 *
 * <p>Classically, the way to solve the problem is to change the logic of the code to decrease the coupling, for example, moving the common behavior to the base-class. But it does not work many time. So AOP (Aspect-Oriented Programming) comes up.
 * <p>It teaches developers to pay attention to the common behavior of multiple types, which is even able not to be extended form the same class. Further, the framework refer to the type as variable</p>*/
public class AspectUsage {
  /**This is the base class where defined several methods, which will be the common behaviors of the sub-classes*/
  static class ClassA{
    public void update(){}
    public void draw(){}
    public void showInfo(){
      System.out.println("Now here is `ClassA`");
    }
    public void showInfo(String string){
      System.out.println("Here is `ClassA`'s info: " + string);
    }
  }

  //The following are the sub-classes extended from the ClassA. What do you think we should do to add the following functions to the instance of ClassB and ClassC:
  //×-After `update()`, prints how many times it is invoked;
  //×-Before and after `draw()`, prints a line "--------------------";
  //×-" Before `showInfo()`, prints a "> ".

  //And what if the sub-classes are more than ClassB and ClassC?

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

  // Now let's get the default DynamicMaker
  static DynamicMaker maker = DynamicFactory.getDefault();

  /**An aspect interface for the class above.*/
  @AspectInterface
  public interface ClassAspect{
    void update();
    void draw();
    /**The method is with {@linkplain FuzzyMatch Fuzzy Match}, where the {@linkplain FuzzyMatch#anySameName() anySameName} parameter means that the method can override any methods with the same name*/
    @FuzzyMatch(anySameName = true)
    void showInfo();
  }

  //Let's start
  public static void main(String[] args) {
    //Creates a dynamic type
    DynamicClass SampleAspect = DynamicClass.get("SampleAspect");

    SampleAspect.setVariable("updateCounter", 0);
    //Next, we will describe the functions
    SampleAspect.setFunction("update", (s, su, arg) -> {
      su.invokeFunc("update", arg);
      System.out.println("update invoked counter: " + s.calculateVar("updateCounter", (int i) -> i + 1));
    });
    SampleAspect.setFunction("draw", (s, su, arg) -> {
      System.out.println("--------------------");
      su.invokeFunc("draw", arg);
      System.out.println("--------------------");
    });
    //Defines a function for two methods with different signatures
    Function.NonRetSuperGetFunc<?> fun = (s, su, arg) -> {
      System.out.print("> ");
      su.invokeFunc("showInfo", arg);
    };
    SampleAspect.setFunction("showInfo", fun);
    // -> public void showInfo(){...}
    SampleAspect.setFunction("showInfo", fun, String.class);
    // -> public void showInfo(String string){...}

    //Then, creates two classes' aspect proxy object
    DynamicObject<ClassB> instB = maker.newInstance(ClassB.class, new Class[]{ClassAspect.class}, SampleAspect);
    DynamicObject<ClassC> instC = maker.newInstance(ClassC.class, new Class[]{ClassAspect.class}, SampleAspect);

    //Test printing
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

    //Obviously, this greatly reduces the coupling and workload of the code, and you can use less code to complete similar tasks, especially when there are far more sub-classes.
  }
}
