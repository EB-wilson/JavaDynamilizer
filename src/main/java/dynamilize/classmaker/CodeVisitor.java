package dynamilize.classmaker;

import dynamilize.classmaker.code.*;

public interface CodeVisitor{
  void visitClass(IClass<?> clazz);

  void visitMethod(IMethod<?, ?> method);

  void visitField(IField<?> field);

  void visitLocal(ILocal<?> local);

  void visitInvoke(IInvoke<?> invoke);

  void visitGetField(IGetField<?, ?> getField);

  void visitPutField(IPutField<?, ?> putField);

  void visitLocalSet(ILocalAssign<?, ?> localSet);

  void visitOperate(IOperate<?> operate);

  void visitCast(ICast cast);

  void visitGoto(IGoto iGoto);

  void visitLabel(ILabel label);

  void visitCondition(ICondition condition);

  void visitCodeBlock(ICodeBlock codeBlock);

  <Type> void visitReturn(IReturn<Type> iReturn);

  void visitInstanceOf(IInstanceOf instanceOf);

  void visitNewInstance(INewInstance newInstance);
}
