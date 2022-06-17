package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface ICondition extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitCondition(this);
  }

  ILocal<Boolean> condition();

  ILabel ifJump();
}
