package dynamilize.classmaker;

import dynamilize.IllegalHandleException;
import dynamilize.classmaker.code.IClass;
import dynamilize.classmaker.code.annotation.AnnotatedElement;
import dynamilize.classmaker.code.annotation.IAnnotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

public abstract class AnnotatedMember implements AnnotatedElement{
  private final String name;
  private int modifiers;

  private List<IAnnotation<?>> annotationsList;

  public AnnotatedMember(String name){
    this.name = name;
  }

  public String name(){
    return name;
  }

  public int modifiers(){
    return modifiers;
  }

  public void setModifiers(int modifiers){
    this.modifiers = modifiers;
  }

  @Override
  public List<IAnnotation<?>> getAnnotations(){
    return annotationsList == null? new ArrayList<>(): annotationsList;
  }

  @Override
  public boolean hasAnnotation(IClass<? extends Annotation> annoType){
    return getAnnotation(annoType) != null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <A extends Annotation> IAnnotation<A> getAnnotation(IClass<A> annoType){
    if(annotationsList == null) return null;

    for(IAnnotation<?> annotation: annotationsList){
      if(annoType.equals(annotation.annotationType().typeClass())){
        return (IAnnotation<A>) annotation;
      }
    }

    return null;
  }

  @Override
  public void addAnnotation(IAnnotation<?> annotation){
    if(annotationsList == null){
      annotationsList = new ArrayList<>();
    }

    checkType(annotation);

    for(IAnnotation<?> anno: annotationsList){
      if(anno.annotationType().equals(annotation.annotationType())){
        if(!anno.annotationType().typeClass().hasAnnotation(ClassInfo.asType(Repeatable.class)))
          throw new IllegalHandleException("cannot add multiple annotations with same name that without repeatable meta annotation");
      }
    }

    annotationsList.add(annotation);
  }

  protected void checkType(IAnnotation<?> annotation){
    IAnnotation<Target> tarMark = annotation.annotationType().typeClass().getAnnotation(ClassInfo.asType(Target.class));
    if(tarMark == null) return;

    for(ElementType type: tarMark.asAnnotation().value()){
      if(isType(type)) return;
    }

    throw new IllegalHandleException(annotation + " can not use on " + this);
  }
}
