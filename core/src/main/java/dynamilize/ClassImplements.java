package dynamilize;

import java.util.Arrays;
import java.util.Objects;

/**保存类的超类及实现接口的容器，用于快速确认委托的构造类型
 *
 * @author EBwilson */
class ClassImplements<T>{
  final Class<T> base;
  final Class<?>[] interfaces;
  final Class<?>[] aspects;
  private int hash;

  public ClassImplements(Class<T> base, Class<?>[] interfaces, Class<?>[] aspects){
    this.base = base;
    this.interfaces = interfaces;
    this.aspects = aspects;
    this.hash = Objects.hash(base);
    hash = 31*hash + Arrays.hashCode(interfaces)^Arrays.hashCode(aspects);
  }

  @Override
  public String toString(){
    return base.getCanonicalName() + Arrays.hashCode(Arrays.stream(interfaces).map(Class::getCanonicalName).toArray(String[]::new));
  }

  @Override
  public boolean equals(Object o){
    if(this == o) return true;
    if(!(o instanceof ClassImplements<?> that)) return false;
    return base.equals(that.base) && interfaces.length == that.interfaces.length && (aspects != null && that.aspects != null && aspects.length == that.aspects.length) && hash == that.hash;
  }

  @Override
  public int hashCode(){
    return hash;
  }
}
