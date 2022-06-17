package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface IReturn<Type> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitReturn(this);
  }

  ILocal<Type> result();
}
