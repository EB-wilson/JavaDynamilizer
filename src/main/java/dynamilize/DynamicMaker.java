package dynamilize;

import dynamilize.base.*;
import dynamilize.classmaker.ClassInfo;
import dynamilize.classmaker.CodeBlock;
import dynamilize.classmaker.FieldInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class DynamicMaker{
  public static final String CALLSUPER = "$super";

  private final JavaHandleHelper helper;

  private final HashMap<Class<?>, Class<?>> pool = new HashMap<>();
  private final HashMap<Class<?>, HashMap<FunctionType, MethodHandle>> constructors = new HashMap<>();

  protected DynamicMaker(JavaHandleHelper helper){
    this.helper = helper;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public DynamicObject newInstance(DynamicObject<?> dynamicObject){
    return newInstance(null, (DynamicClass<? extends DynamicObject<?>>) dynamicObject);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public DynamicObject newInstance(Class<?>[] interfaces, DynamicClass<? extends DynamicObject<?>> dynamicClass){
    return newInstance((Class)Object.class, interfaces, (DynamicClass<? extends DynamicObject>) dynamicClass);
  }

  public <T extends DynamicObject<T>> T newInstance(Class<T> base, DynamicClass<? extends T> dynamicClass, Object... args){
    return newInstance(base, null, dynamicClass, args);
  }

  @SuppressWarnings("unchecked")
  public <T extends DynamicObject<T>> T newInstance(Class<T> base, Class<?>[] interfaces, DynamicClass<? extends T> dynamicClass, Object... args){
    Class<? extends T> clazz = getDynamicBase(base, interfaces, dynamicClass);
    try{
      DataPool<T> pool;
      List<Object> argsLis = new ArrayList<>(List.of(dynamicClass, pool = new DataPool<>(genBasePool(clazz))));
      argsLis.addAll(Arrays.asList(args));

      Constructor<?> cstr = clazz.getDeclaredConstructor(argsLis.stream().map(Object::getClass).toArray(Class[]::new));
      FunctionType type = FunctionType.inst(cstr.getParameterTypes());

      T inst = (T) constructors.computeIfAbsent(clazz, e -> new HashMap<>()).computeIfAbsent(type, t -> {
        helper.setAccess(cstr);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try{
          return lookup.unreflectConstructor(cstr);
        }catch(IllegalAccessException e){
          throw new RuntimeException(e);
        }
      }).invokeWithArguments(args);

      pool.setOwner(inst);
      dynamicClass.initInstance(pool);

      return inst;
    }catch(Throwable e){
      throw new RuntimeException(e);
    }
  }

  protected <T extends DynamicObject<T>> DataPool<T> genBasePool(Class<? extends T> base){
    DataPool<T> pool = new DataPool<>(null);
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    for(Method method: base.getDeclaredMethods()){
      String name = method.getName();
      if(name.endsWith(CALLSUPER)){
        try{
          MethodHandle handle = lookup.unreflect(method);

          pool.set(name, (self, args) -> {
            try{
              return handle.invokeWithArguments(args);
            }catch(Throwable e){
              throw new RuntimeException(e);
            }
          }, method.getParameterTypes());
        }catch(IllegalAccessException e){
          throw new RuntimeException(e);
        }
      }
    }

    for(Field field: base.getDeclaredFields()){
      helper.setAccess(field);
      pool.add(new JavaVariable(field));
    }

    return pool;
  }

  @SuppressWarnings("unchecked")
  public <T> Class<? extends T> getDynamicBase(Class<T> base, Class<?>[] interfaces, DynamicClass<? extends T> dynamicClass){
    return (Class<? extends T>) pool.computeIfAbsent(base, e -> generateClass(base, interfaces, dynamicClass));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected <T> ClassInfo<? extends T> makeClassInfo(Class<T> baseClass, Class<?>[] interfaces, DynamicClass<? extends T> dynamicClass){
    ArrayList<ClassInfo<?>> inter = new ArrayList<>(interfaces.length + 1);
    inter.add(ClassInfo.asType(DynamicClass.class));

    for(Class<?> i: interfaces){
      inter.add(ClassInfo.asType(i));
    }

    ClassInfo<T> classInfo = new ClassInfo<>(
        Modifier.PUBLIC,
        baseClass.getPackageName() + baseClass.getName() + "$dynamic$",
        ClassInfo.asType(baseClass),
        inter.toArray(ClassInfo[]::new)
    );

    FieldInfo<DataPool<?>> dataPool = (FieldInfo) classInfo.declareField(Modifier.PRIVATE | Modifier.FINAL, "$datapool$", ClassInfo.asType(DataPool.class), null);
    FieldInfo<DynamicClass<?>> dyType = (FieldInfo) classInfo.declareField(Modifier.PRIVATE | Modifier.FINAL, "$dynamic_type$", ClassInfo.asType(DynamicClass.class), null);

    ClassInfo<?>[] paramBase = {
        ClassInfo.asType(DynamicClass.class),
        ClassInfo.asType(DataPool.class),
    };

    for(Constructor<?> cstr: baseClass.getDeclaredConstructors()){
      if((cstr.getModifiers() & (Modifier.PRIVATE | Modifier.PROTECTED)) == 0) continue;

      List<ClassInfo<?>> params = Arrays.asList(paramBase);
      for(Class<?> parameterType: cstr.getParameterTypes()){
        params.add(ClassInfo.asType(parameterType));
      }
      CodeBlock<Void> code = classInfo.declareConstructor(Modifier.PUBLIC, params.toArray(ClassInfo[]::new));

    }

    return classInfo;
  }

  protected abstract <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces, DynamicClass<? extends T> dynamicClass);
}
