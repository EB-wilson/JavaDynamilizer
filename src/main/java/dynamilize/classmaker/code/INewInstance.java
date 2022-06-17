package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

import java.util.List;

public interface INewInstance<T> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitNewInstance(this);
  }

  IMethod<T, Void> constructor();

  ILocal<? extends T> instanceTo();

  List<ILocal<?>> params();
}
