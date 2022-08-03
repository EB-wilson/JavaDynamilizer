import dynamilize.*;
import dynamilize.classmaker.ASMGenerator;
import dynamilize.classmaker.BaseClassLoader;
import dynamilize.classmaker.ClassInfo;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class DynamicTest{
  public static void main(String[] args){
    DynamicClass dyc = DynamicClass.get("Demo");                              //首先，传入类型进行行为描述

    BaseClassLoader loader = new BaseClassLoader(DynamicMaker.class.getClassLoader());
    ASMGenerator generator = new ASMGenerator(loader, Opcodes.V1_8);
    DynamicMaker maker = new DynamicMaker(acc -> acc.setAccessible(true)){
      @Override
      protected <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces){
        ClassInfo<?> i = makeClassInfo(baseClass, interfaces);
        
        File f = new File("temp", "Output.class");
        f.getParentFile().mkdirs();

        try{
          f.createNewFile();
          new FileOutputStream(f).write(generator.genByteCode(i));
        }catch(IOException e){
          throw new RuntimeException(e);
        }

        return (Class<? extends T>) i.generate(generator);
      }
    };
    //DynamicMaker maker = DynamicMaker.getDefault();

    DynamicObject<Runner> r = maker.newInstance(Runner.class, dyc);
    Runner rs = new Runner(), rr = r.self();

    Function<Runner, Long> fn = r.getFunc("run", long.class);

    FunctionType sign = FunctionType.inst(long.class);
    long sum = 0;
    for(int i = 0; i < 1024; i++){
      sum += (Long) ((DynamicMaker.SuperInvoker)rr).invokeSuper("run(J)", System.nanoTime());
    }
    long a = sum;
    System.out.println(a);

    System.out.println("=========================================================");

    sum = 0;
    for(int i = 0; i < 1024; i++){
      sum += rs.run(System.nanoTime());
    }
    long b = sum;
    System.out.println(b);

    System.out.println(a - b);
  }

  public static class Runner{
    public String a;

    private static final HashMap<String, Integer> map = new HashMap<>();

    static {
      map.put("run1", 0);
      map.put("run2", 1);
    }

    public long run(long time){
      StringBuilder b = new StringBuilder();
      for(int i = 0; i < 1; i++){
        b.append(i);
      }
      a = b.toString();
      return System.nanoTime() - time;
    }
  }
}

