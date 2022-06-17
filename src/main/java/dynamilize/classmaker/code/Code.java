package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

public interface Code{
  void accept(CodeVisitor visitor);
}
