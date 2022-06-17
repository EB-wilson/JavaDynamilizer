package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface ICast extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitCast(this);
  }

  ILocal<?> source();

  ILocal<?> target();
}
