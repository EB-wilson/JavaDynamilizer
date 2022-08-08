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

    dyc.setFunction("run", (s, superPointer, a) -> {
      System.out.println("yuj");
      superPointer.invokeFunc("run", a);
    }, long.class);

    DynamicObject<Runner> r = maker.newInstance(Runner.class, dyc);
    Runner rr = r.self();

    Func<Void> run = r.getFunction("run", long.class);
    run.invoke((long)8799);

    r.invokeFunc("run", System.nanoTime());
    rr.p();
    rr.p();
  }

  public static class Runner{
    public String a;

    private static final HashMap<String, Integer> map = new HashMap<>();

    static {
      map.put("run1", 0);
      map.put("run2", 1);
    }

    public void run(long time){
      System.out.println(time);
      a = String.valueOf(time);
    }

    public void p(){
      System.out.println(a);
    }
  }
}

