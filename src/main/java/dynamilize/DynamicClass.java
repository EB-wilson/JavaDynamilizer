package dynamilize;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class DynamicClass{
  private final String name;

  protected DynamicClass superDyClass;

  protected Map<String, Map<FunctionType, MethodEntry>> methods = new HashMap<>();
  protected Map<String, Initializer> varInit = new HashMap<>();

  public DynamicClass(String name){
    this.name = name;
  }

  public String getName(){
    return name;
  }

  public DynamicClass superDyClass(){
    return superDyClass;
  }

  public <T> void initInstance(DataPool<T> pool){
    for(Map<FunctionType, MethodEntry> map: methods.values()){
      for(MethodEntry entry: map.values()){
        FunctionType type = entry.getType();
        pool.set(entry.getName(), (self, args) -> map.get(type).getFunction().invoke(self, args), entry.getType().getTypes());
      }
    }

    for(Map.Entry<String, Initializer> entry: varInit.entrySet()){
      pool.set(entry.getKey(), entry.getValue());
    }
  }

  public void visitClass(Class<?> template){
    for(Method method: template.getDeclaredMethods()){
      if(!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) continue;

      MethodEntry methodEntry = new MethodEntry(method);

      methods.computeIfAbsent(methodEntry.getName(), e -> new HashMap<>()).put(methodEntry.getType(), methodEntry);
    }

    for(Field field: template.getDeclaredFields()){
      if(!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) continue;

      Object value;
      try{
         value = field.get(null);
      }catch(IllegalAccessException e){
        throw new RuntimeException(e);
      }

      varInit.put(field.getName(), () -> value);
    }
  }
}
