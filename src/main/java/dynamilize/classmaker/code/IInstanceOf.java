package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface IInstanceOf extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitInstanceOf(this);
  }

  ILocal<?> target();

  IClass<?> type();

  ILocal<Boolean> result();
}
