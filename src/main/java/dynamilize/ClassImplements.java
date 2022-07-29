package dynamilize;

import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
class ClassImplements<T>{
  final Class<T> base;
  final Class<?>[] interfaces;

  public ClassImplements(Class<T> base, Class<?>[] interfaces){
    this.base = base;
    this.interfaces = interfaces;
  }

  @Override
  public String toString(){
    return base.getCanonicalName() + Arrays.hashCode(Arrays.stream(interfaces).map(Class::getCanonicalName).toArray(String[]::new));
  }

  @Override
  public boolean equals(Object o){
    if(this == o) return true;
    if(!(o instanceof ClassImplements<?> that)) return false;
    return base.equals(that.base) && Arrays.equals(interfaces, that.interfaces);
  }

  @Override
  public int hashCode(){
    int result = Objects.hash(base);
    result = 31*result + Arrays.hashCode(interfaces);
    return result;
  }
}
