package dynamilize.classmaker.code;

import dynamilize.classmaker.ElementVisitor;
import dynamilize.classmaker.code.annotation.AnnotatedElement;
import dynamilize.classmaker.code.annotation.AnnotationType;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

public interface IClass<T> extends Element, AnnotatedElement{
  @Override
  default void accept(ElementVisitor visitor){
    visitor.visitClass(this);
  }

  @Override
  default ElementKind kind(){
    return ElementKind.CLASS;
  }

  /**返回此类型标识所标记的类型
   *
   * @return 此类型标记标记的类*/
  Class<T> getTypeClass();

  boolean isExistedClass();

  boolean isPrimitive();

  IClass<T[]> asArray();

  <A extends Annotation> AnnotationType<A> asAnnotation(Map<String, Object> defaultAttributes);

  boolean isAnnotation();

  boolean isArray();

  IClass<?> componentType();

  String name();

  String realName();

  String internalName();

  int modifiers();

  IClass<? super T> superClass();

  List<IClass<?>> interfaces();

  List<Element> elements();

  <Type> IField<Type> getField(IClass<Type> type, String name);

  <R> IMethod<T, R> getMethod(IClass<R> returnType, String name, IClass<?>... args);

  IMethod<T, Void> getConstructor(IClass<?>... args);

  boolean isAssignableFrom(IClass<?> target);
}
