package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface IPutField<S, T extends S> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitPutField(this);
  }

  ILocal<?> inst();

  ILocal<S> source();

  IField<T> target();
}
