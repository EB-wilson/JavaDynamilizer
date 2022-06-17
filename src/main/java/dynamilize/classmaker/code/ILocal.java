package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface ILocal<T> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitLocal(this);
  }

  String name();

  int modifiers();

  IClass<T> type();

  Object initial();
}
