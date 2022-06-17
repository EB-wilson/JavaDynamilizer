package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface ILabel extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitLabel(this);
  }
}
