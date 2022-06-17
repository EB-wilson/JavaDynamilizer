package dynamilize.classmaker.code;

import java.util.List;

public interface ICodeBlock extends Code{
  List<Code> codes();

  int modifiers();
}
