package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface IGetField<S, T extends S> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitGetField(this);
  }

  ILocal<?> inst();

  IField<S> source();

  ILocal<T> target();
}
