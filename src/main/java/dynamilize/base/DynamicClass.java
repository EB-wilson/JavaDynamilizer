package dynamilize.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicClass<Base>{
  protected DynamicClass<?> superDyClass;
  protected Class<Base> baseClass;

  protected List<MethodEntry<?>> methods = new ArrayList<>();
  protected Map<String, Initializer> varInit = new HashMap<>();

  public Class<Base> extend(){
    return baseClass;
  }

  public DynamicClass<?> superDyClass(){
    return superDyClass;
  }

  @SuppressWarnings("unchecked")
  public <T extends DynamicObject<T>> void initInstance(DataPool<T> pool){
    for(MethodEntry<?> entry: methods){
      pool.set(entry.getName(), (Function<T, ?>) entry.getFunction(), entry.getType().getTypes());
    }

    for(Map.Entry<String, Initializer> entry: varInit.entrySet()){
      pool.set(entry.getKey(), entry.getValue());
    }
  }
}
