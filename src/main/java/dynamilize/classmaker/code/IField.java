package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface IField<T> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitField(this);
  }

  String name();

  int modifiers();

  IClass<?> owner();

  IClass<T> type();

  Object initial();
}
