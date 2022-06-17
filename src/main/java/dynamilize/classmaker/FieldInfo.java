package dynamilize.classmaker;

import dynamilize.classmaker.code.IClass;
import dynamilize.classmaker.code.IField;

import java.lang.reflect.Modifier;

public class FieldInfo<T> extends Member implements IField<T>{
  IClass<?> owner;
  IClass<T> type;
  Object initial;

  public FieldInfo(ClassInfo<?> owner, int modifiers, String name, IClass<T> type, Object initial){
    super(name);
    setModifiers(modifiers);
    this.owner = owner;
    this.type = type;
    this.initial = initial;

    if(initial != null && !Modifier.isStatic(modifiers))
      throw new IllegalArgumentException("cannot initial a constant to non-static field");

    if(!(initial instanceof Number)
    && !(initial instanceof Boolean)
    && !(initial instanceof String)
    && !(initial instanceof Character)){
      throw new IllegalArgumentException("initial must be a primitive type or String");
    }
  }

  @Override
  public IClass<?> owner(){
    return owner;
  }

  @Override
  public IClass<T> type(){
    return type;
  }

  @Override
  public Object initial(){
    return initial;
  }
}
