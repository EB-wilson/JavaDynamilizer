package dynamilize.classmaker;

import dynamilize.classmaker.code.*;

import java.util.Map;

public abstract class AbstractClassGenerator implements ElementVisitor{
  protected Map<String, ILocal<?>> localMap;

  @Override
  public void visitClass(IClass<?> clazz){
    for(Element element: clazz.elements()){
      element.accept(this);
    }
  }

  @Override
  public void visitCodeBlock(ICodeBlock<?> block){
    for(Element element: block.codes()){
      element.accept(this);
    }
  }

  @Override
  public void visitMethod(IMethod<?, ?> method){
    if(method.block() != null) method.block().accept(this);
  }

  @Override
  public void visitLocal(ILocal<?> local){
    localMap.put(local.name(), local);
  }

  public abstract byte[] genByteCode(ClassInfo<?> classInfo);

  protected abstract <T> Class<T> generateClass(ClassInfo<T> classInfo);
}
