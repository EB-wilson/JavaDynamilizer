package dynamilize.classmaker;

import dynamilize.classmaker.code.*;

import java.util.Map;
import java.util.Stack;

public abstract class AbstractClassGenerator implements CodeVisitor{
  protected Map<String, ILocal<?>> localMap;

  protected Stack<Code> memberStack = new Stack<>();

  @Override
  public void visitClass(IClass<?> clazz){
    memberStack.push(clazz);
    for(Code code: clazz.codes()){
      code.accept(this);
    }
    memberStack.pop();
  }

  @Override
  public void visitCodeBlock(ICodeBlock block){
    for(Code code: block.codes()){
      code.accept(this);
    }
  }

  @Override
  public void visitMethod(IMethod<?, ?> method){
    memberStack.push(method);
    method.block().accept(this);
    memberStack.pop();
  }

  @Override
  public void visitLocal(ILocal<?> local){
    localMap.put(local.name(), local);
  }

  public abstract <T> Class<T> generateClass(ClassInfo<T> classInfo);
}
