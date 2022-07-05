import dynamilize.DynamicClass;
import dynamilize.DynamicMaker;
import dynamilize.DynamicObject;
import dynamilize.annotation.This;

@SuppressWarnings("unchecked")
public class DynamicTest{
  public static void main(String[] args){
    DynamicClass dyc = DynamicClass.get("Demo");                           //声明动态类型
    dyc.visitClass(DynamicTemplate0.class);                                //首先，传入类型进行行为描述

    Runner obj = DynamicMaker.getDefault().newInstance(Runner.class, dyc); //传入委托的基类以构造动态对象
    DynamicObject<Runner> dyO = (DynamicObject<Runner>) obj;               //获得对象的动态对象类型接口对象

    obj.run();                                                             //执行run方法，此时的行为已经被委托覆盖

    dyc.visitClass(DynamicTemplate1.class);                                //传入第二个类型变更行为描述
    dyO.setVar("var0", "string var value");                                //设置变量
    obj.run();                                                             //再次执行run方法，此时行为被第二次覆盖

    dyO.setFunc("run", (s, r) -> {                                         //lambda模式覆盖run方法行为
      System.out.println("then, lambda mode action");
      return null;
    });
    obj.run();                                                             //再次执行run方法
  }

  public static class Runner{
    public void run(){
      System.out.println("default super");
    }
  }

  public static class DynamicTemplate0{
    public static void run(@This final DynamicObject<Runner> self){        //描述一个覆盖方法行为的方法模板，方法需要为public static
      System.out.println("call super first");                                //self参数指向对象本身，即this指针
      self.superPoint().invokeFunc("run");                                 //访问run方法的超类方法，其指向run方法的原定义
      System.out.println("template0 action");
    }
  }

  public static class DynamicTemplate1{
    public static void run(@This final DynamicObject<Runner> self){
      System.out.println("template1 action: " + self.<String>getVar("var0"));
    }
  }
}

