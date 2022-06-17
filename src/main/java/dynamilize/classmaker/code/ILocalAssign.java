package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface ILocalAssign<S, T extends S> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitLocalSet(this);
  }

  ILocal<S> source();

  ILocal<T> target();
}
