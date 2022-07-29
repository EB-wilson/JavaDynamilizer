import dynamilize.DynamicClass;
import dynamilize.DynamicMaker;
import dynamilize.DynamicObject;
import dynamilize.ProxyMaker;
import dynamilize.classmaker.ASMGenerator;
import dynamilize.classmaker.BaseClassLoader;
import dynamilize.classmaker.ClassInfo;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("unchecked")
public class DynamicTest{
  public static void main(String[] args){
    DynamicClass dyc = DynamicClass.get("Demo");                              //首先，传入类型进行行为描述

    dyc.setFinalFunc("run", (s, a) -> {
      s.superPoint().invokeFunc("run", a);
      System.out.println(s + " value: " + a.get(0));
      return null;
    }, int.class, boolean.class);

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

    ProxyMaker proxyMaker = ProxyMaker.getDefault(maker, (s, m, a) -> {
      System.out.println(m);
      return m.invoke(s, a);
    });

    DynamicObject<Runner> proxy = proxyMaker.newProxyInstance(Runner.class); //传入委托的基类以构造动态对象

    DynamicObject<Runner> obj = maker.newInstance(Runner.class, dyc); //传入委托的基类以构造动态对象

    obj.self().run(87, true);
    proxy.self().run(88, false);
    proxy.self().hashCode();
  }

  public static class Runner{
    public void run(int time, boolean boo){
      System.out.println(time + " default super " + boo);
    }
  }
}

