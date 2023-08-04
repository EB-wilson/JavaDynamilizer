package com.github.ebwilson.sample_zh;

import dynamilize.DynamicFactory;
import dynamilize.DynamicMaker;
import dynamilize.DynamicObject;
import dynamilize.annotations.ContextMaker;
import dynamilize.annotations.DynamicContext;
import dynamilize.annotations.NewDynamic;
import dynamilize.classmaker.ASMGenerator;
import dynamilize.classmaker.BaseClassLoader;
import dynamilize.classmaker.ClassInfo;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

@DynamicContext
public class Test {

  @ContextMaker
  private static final DynamicMaker maker = new DynamicFactory().setGenerator(new ASMGenerator(new BaseClassLoader(Test.class.getClassLoader()), Opcodes.V1_8){
    @Override
    public byte[] genByteCode(ClassInfo<?> classInfo) {
      byte[] bytes = super.genByteCode(classInfo);
      try(FileOutputStream fo = new FileOutputStream(classInfo.name() + ".class")) {
        fo.write(bytes);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      return bytes;
    }
  }).setDefaultHelper().getMaker();
  public static void main(String[] args) {
    @NewDynamic(dynClassName = "dyc", impls = {Map.class})
    DynamicObject<Object> a = (DynamicObject<Object>) new Object();

    a.setVar("a", 18);
    System.out.println(a.getVar("a", 0));
  }
}
