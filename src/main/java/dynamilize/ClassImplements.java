package dynamilize;

import java.util.Arrays;
import java.util.Objects;

/**保存类的超类及实现接口的容器，用于快速确认委托的构造类型
 *
 * @author EBwilson */
class ClassImplements<T>{
  final Class<T> base;
  final Class<?>[] interfaces;
  private int hash;

  public ClassImplements(Class<T> base, Class<?>[] interfaces){
    this.base = base;
    this.interfaces = interfaces;
    this.hash = Objects.hash(base);
    hash = 31*hash + Arrays.hashCode(interfaces);
  }

  @Override
  public String toString(){
    return base.getCanonicalName() + Arrays.hashCode(Arrays.stream(interfaces).map(Class::getCanonicalName).toArray(String[]::new));
  }

  @Override
  public boolean equals(Object o){
    if(this == o) return true;
    if(!(o instanceof ClassImplements<?> that)) return false;
    return base.equals(that.base) && interfaces.length == that.interfaces.length && hash == ((ClassImplements<?>) o).hash;
  }

  @Override
  public int hashCode(){
    return hash;
  }
}
