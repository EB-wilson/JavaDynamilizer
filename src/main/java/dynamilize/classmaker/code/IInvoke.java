package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

import java.util.List;

public interface IInvoke<R> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitInvoke(this);
  }

  ILocal<?> target();

  IMethod<?, R> method();

  List<ILocal<?>> args();

  ILocal<? extends R> returnTo();
}
