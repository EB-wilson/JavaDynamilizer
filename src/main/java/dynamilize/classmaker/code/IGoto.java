package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface IGoto extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitGoto(this);
  }

  ILabel target();
}
