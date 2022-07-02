import dynamilize.DynamicClass;
import dynamilize.DynamicMaker;
import dynamilize.DynamicObject;
import dynamilize.annotation.This;

public class DynamicTest{
  public static void main(String[] args){
    DynamicClass dyc = new DynamicClass("Test");

    dyc.visitClass(DynamicTemplate.class);

    Runner obj = DynamicMaker.getDefault().newInstance(Runner.class, dyc);
    obj.run();

    dyc.visitClass(DynamicTemplate1.class);
    obj.run();

    DynamicObject<Runner> dyO = (DynamicObject<Runner>) obj;
    dyO.setFunc("run", (s, r) -> {
      System.out.println("jjjjj");
      return null;
    });
    obj.run();
  }

  public static class Runner{
    public void run(){}
  }

  public static class DynamicTemplate{
    public static void run(
        @This
        final DynamicObject<?> self){
      System.out.println("testing");
      System.out.println("dynamic");
    }
  }

  public static class DynamicTemplate1{
    public static void run(
        @This
        final DynamicObject<?> self){
      System.out.println("running");
      System.out.println("test is processing");
    }
  }
}

