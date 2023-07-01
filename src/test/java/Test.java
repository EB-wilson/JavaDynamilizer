import dynamilize.*;
import dynamilize.classmaker.ASMGenerator;
import dynamilize.classmaker.BaseClassLoader;
import org.objectweb.asm.Opcodes;
import tesaaa.TBA;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Test {
  public static void main(String[] args) throws Throwable {
    DynamicMaker maker = new DynamicFactory()
        .setDefaultHelper()
        .setDefaultGenerator()
        .setPackageAccHandler(
            new UnsafePackageAccHandler(
                new ASMGenerator(new BaseClassLoader(Test.class.getClassLoader()), Opcodes.V1_8)
            )
        ).getMaker();

    DynamicClass cl = DynamicClass.get("SAM");
    DynamicObject<TBA> sam = maker.newInstance(TBA.class, cl);

    sam.setFunc("run", (s, su, a) -> {
      System.out.println("yyyyyyyyyyy");
    });
    sam.setFunc("run2", (s, su, a) -> {
      System.out.println("zzzzzz");
    });

    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle h = lookup.findConstructor(TBA.class, MethodType.methodType(void.class));

    h.invoke();

    sam.objSelf().run1();
  }
}
