package dynamilize.classmaker;

import dynamilize.classmaker.code.IClass;
import dynamilize.classmaker.code.IMethod;

import java.util.List;

public class MethodInfo<S, R> extends Member implements IMethod<S, R>{
  private final CodeBlock<R> block;

  IClass<S> owner;

  IClass<R> returnType;
  List<IClass<?>> paramTypes;

  public MethodInfo(IClass<S> owner, int modifiers, String name, IClass<R> returnType, IClass<?>... paramTypes){
    super(name);
    setModifiers(modifiers);
    this.block = new CodeBlock<>();
    this.owner = owner;
    this.returnType = returnType;
    this.paramTypes = List.of(paramTypes);
  }

  @Override
  public List<IClass<?>> paramTypes(){
    return paramTypes;
  }

  @Override
  public IClass<S> owner(){
    return owner;
  }

  @Override
  public IClass<R> returnType(){
    return returnType;
  }

  @Override
  public CodeBlock<R> block(){
    return block;
  }
}
