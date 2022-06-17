package dynamilize.classmaker.code;

import dynamilize.classmaker.CodeVisitor;

import java.util.List;

public interface IClass<T> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitClass(this);
  }

  /**返回此类型标识所标记的类型
   *
   * @return 此类型标记标记的类*/
  Class<T> getTypeClass();

  String name();

  String realName();

  int modifiers();

  IClass<? super T> superClass();

  List<IClass<?>> interfaces();

  List<Code> codes();

  <Type> IField<Type> getField(IClass<Type> type, String name);

  <R> IMethod<T, R> getMethod(IClass<R> returnType, String name, IClass<?>... args);

  <R> IMethod<T, R> getConstructor(IClass<?>... args);

  boolean isAssignableFrom(IClass<?> target);
}
