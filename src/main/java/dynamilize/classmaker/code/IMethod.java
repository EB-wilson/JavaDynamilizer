package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

import java.util.List;

public interface IMethod<S, R> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitMethod(this);
  }

  String name();

  int modifiers();

  List<IClass<?>> paramTypes();

  IClass<S> owner();

  IClass<R> returnType();

  ICodeBlock block();
}
