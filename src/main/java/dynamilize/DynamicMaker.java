package dynamilize;

import dynamilize.classmaker.*;
import dynamilize.classmaker.code.IClass;
import dynamilize.classmaker.code.ILocal;
import dynamilize.classmaker.code.IMethod;
import dynamilize.classmaker.code.annotation.AnnotationType;
import org.objectweb.asm.Opcodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@SuppressWarnings("rawtypes")
public abstract class DynamicMaker{
  public static final String CALLSUPER = "$super";
  public static final ClassInfo<DynamicClass> DYNAMIC_CLASS_TYPE = ClassInfo.asType(DynamicClass.class);
  public static final ClassInfo<DynamicObject> DYNAMIC_OBJECT_TYPE = ClassInfo.asType(DynamicObject.class);
  public static final ClassInfo<DataPool> DATA_POOL_TYPE = ClassInfo.asType(DataPool.class);
  public static final ClassInfo<FunctionType> FUNCTION_TYPE_TYPE = ClassInfo.asType(FunctionType.class);
  public static final ClassInfo<Function> FUNCTION_TYPE = ClassInfo.asType(Function.class);
  public static final ClassInfo<DataPool.ReadOnlyPool> READONLY_POOL_TYPE = ClassInfo.asType(DataPool.ReadOnlyPool.class);

  public static final IMethod<DataPool, DataPool.ReadOnlyPool> GET_SUPER = DATA_POOL_TYPE.getMethod(READONLY_POOL_TYPE, "getSuper");
  public static final IMethod<DataPool, Object> GET = DATA_POOL_TYPE.getMethod(ClassInfo.OBJECT_TYPE, "get", ClassInfo.STRING_TYPE);
  public static final IMethod<DataPool, Void> SETVAR = DATA_POOL_TYPE.getMethod(ClassInfo.VOID_TYPE, "set", ClassInfo.STRING_TYPE, ClassInfo.OBJECT_TYPE);
  public static final IMethod<DataPool, Void> SETFUNC = DATA_POOL_TYPE.getMethod(ClassInfo.VOID_TYPE, "set", ClassInfo.STRING_TYPE, FUNCTION_TYPE, ClassInfo.CLASS_TYPE.asArray());
  public static final IMethod<DataPool, Function> SELECT = DATA_POOL_TYPE.getMethod(FUNCTION_TYPE, "select", ClassInfo.STRING_TYPE, FUNCTION_TYPE_TYPE);
  public static final IMethod<DynamicObject, Object> INVOKE = DYNAMIC_OBJECT_TYPE.getMethod(ClassInfo.OBJECT_TYPE, "invokeFunc", ClassInfo.STRING_TYPE, ClassInfo.OBJECT_TYPE.asArray());

  private static final Map<String, Set<FunctionType>> OVERRIDES = new HashMap<>();
  private static final Set<Class<?>> INTERFACE_TEMP = new HashSet<>();
  private static final Class[] EMPTY_CLASSES = new Class[0];

  private final JavaHandleHelper helper;

  private final HashMap<ClassImplements<?>, Class<?>> pool = new HashMap<>();
  private final HashMap<Class<?>, HashMap<FunctionType, MethodHandle>> constructors = new HashMap<>();

  protected DynamicMaker(JavaHandleHelper helper){
    this.helper = helper;
  }

  public static DynamicMaker getDefault(){
    ASMGenerator generator = new ASMGenerator(new BaseClassLoader(DynamicMaker.class.getClassLoader()), Opcodes.V1_8);

    return new DynamicMaker(acc -> {
      try{
        Class.forName("java.lang.Module");
        Demodulator.checkAndMakeModuleOpen(acc.getClass().getModule(), acc.getClass().getPackage(), DynamicClass.class.getModule());
      }catch(ClassNotFoundException ignored){}

      acc.setAccessible(true);
    }){
      @Override
      protected <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces, DynamicClass dynamicClass){
        return makeClassInfo(baseClass, interfaces, dynamicClass).generate(generator);
      }
    };
  }

  public DynamicObject newInstance(DynamicClass dynamicClass){
    return newInstance(EMPTY_CLASSES, dynamicClass);
  }

  @SuppressWarnings("rawtypes")
  public DynamicObject newInstance(Class<?>[] interfaces, DynamicClass dynamicClass){
    return (DynamicObject) newInstance(Object.class, interfaces, dynamicClass);
  }

  public <T> T newInstance(Class<T> base, DynamicClass dynamicClass, Object... args){
    return newInstance(base, EMPTY_CLASSES, dynamicClass, args);
  }

  @SuppressWarnings("unchecked")
  public <T> T newInstance(Class<T> base, Class<?>[] interfaces, DynamicClass dynamicClass, Object... args){
    checkBase(base);

    Class<? extends T> clazz = getDynamicBase(base, interfaces, dynamicClass);
    try{
      DataPool<T> pool;
      List<Object> argsLis = new ArrayList<>(List.of(dynamicClass, pool = genPool(clazz, dynamicClass)));
      argsLis.addAll(Arrays.asList(args));

      Constructor<?> cstr = clazz.getDeclaredConstructor(argsLis.stream().map(Object::getClass).toArray(Class[]::new));
      FunctionType type = FunctionType.inst(cstr.getParameterTypes());

      T inst = (T) constructors.computeIfAbsent(clazz, e -> new HashMap<>()).computeIfAbsent(type, t -> {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try{
          return lookup.unreflectConstructor(cstr);
        }catch(IllegalAccessException e){
          throw new RuntimeException(e);
        }
      }).invokeWithArguments(argsLis.toArray());
      type.recycle();

      pool.setOwner(inst);

      return inst;
    }catch(Throwable e){
      throw new RuntimeException(e);
    }
  }

  protected <T> void checkBase(Class<T> base){
    if(base.getAnnotation(DynamicType.class) != null)
      throw new IllegalHandleException("cannot derive a dynamic class with a dynamic class");

    if(base.isInterface())
      throw new IllegalHandleException("interface cannot use to derive a dynamic class, please write it in parameter \"interfaces\" to implement this interface");

    if(Modifier.isFinal(base.getModifiers()))
      throw new IllegalHandleException("cannot derive a dynamic class with a final class");
  }

  protected <T> DataPool<T> genPool(Class<? extends T> base, DynamicClass dynamicClass){
    DataPool<T> basePool = new DataPool<>(null);
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    for(Method method: base.getDeclaredMethods()){
      String name = method.getName();
      if(name.endsWith(CALLSUPER)){
        try{
          MethodHandle handle = lookup.unreflect(method);

          List<Object> arg = new ArrayList<>();

          basePool.set(name.replace(CALLSUPER, ""), (self, args) -> {
            try{
              arg.clear();
              arg.add(self);
              arg.addAll(List.of(args));
              return handle.invokeWithArguments(arg);
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
      basePool.add(new JavaVariable(field));
    }

    Stack<DynamicClass> classStack = new Stack<>();
    DynamicClass currClass = dynamicClass;

    while(currClass != null){
      classStack.push(currClass);
      currClass = currClass.superDyClass();
    }

    DataPool<T> pool = basePool;
    while(!classStack.empty()){
      pool = new DataPool<>(pool);
      classStack.pop().initInstance(pool);
    }

    return pool;
  }

  @SuppressWarnings("unchecked")
  public <T> Class<? extends T> getDynamicBase(Class<T> base, Class<?>[] interfaces, DynamicClass dynamicClass){
    return (Class<? extends T>) pool.computeIfAbsent(new ClassImplements<>(base, interfaces), e -> generateClass(base, interfaces, dynamicClass));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected <T> ClassInfo<? extends T> makeClassInfo(Class<T> baseClass, Class<?>[] interfaces, DynamicClass dynamicClass){
    ArrayList<ClassInfo<?>> inter = new ArrayList<>(interfaces.length + 1);
    inter.add(ClassInfo.asType(DynamicObject.class));

    for(Class<?> i: interfaces){
      inter.add(ClassInfo.asType(i));
    }

    String packageName = baseClass.getPackageName() + ".";
    if(packageName.equals("java.lang")) packageName = "dyjava.lang";
    else if(packageName.equals(".")) packageName = "";

    ClassInfo<? extends T> classInfo = new ClassInfo<>(
        Modifier.PUBLIC,
         packageName + dynamicClass.getName() + "$dynamic$",
        ClassInfo.asType(baseClass),
        inter.toArray(ClassInfo[]::new)
    );

    FieldInfo<DynamicClass> dyType = classInfo.declareField(
        Modifier.PRIVATE | Modifier.FINAL,
        "$dynamic_type$",
        DYNAMIC_CLASS_TYPE,
        null
    );
    FieldInfo<DataPool<?>> dataPool = (FieldInfo) classInfo.declareField(
        Modifier.PRIVATE | Modifier.FINAL,
        "$datapool$",
        DATA_POOL_TYPE,
        null
    );

    Object[] paramBase = new Object[]{
        0, DynamicClass.class, "$dyc$",
        0, DataPool.class, "$datP$"
    };

    // public <init>(DynamicClass $dyC$, DataPool $datP$, *parameters*){
    //   super(*parameters*);
    //   this.$dynamic_type$ = $dyC$;
    //   this.$datapool$ = $datP$;
    // }
    for(Constructor<?> cstr: baseClass.getDeclaredConstructors()){
      if((cstr.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0) continue;

      List<Parameter<?>> params = Arrays.asList(Parameter.as(paramBase));
      List<Parameter<?>> superParams = Arrays.asList(Parameter.asParameter(cstr.getParameters()));
      params.addAll(superParams);

      IMethod<?, Void> constructor = classInfo.superClass().getConstructor(superParams.stream().map(Parameter::getType).toArray(IClass[]::new));

      CodeBlock<Void> code = classInfo.declareConstructor(Modifier.PUBLIC, params.toArray(Parameter[]::new));
      List<ILocal<?>> l = code.getParamList();
      ILocal<?> self = code.getParam(0);
      code.invokeSuper(self, constructor, null, l.subList(2, l.size()).toArray(ILocal[]::new));

      ILocal<DynamicClass> dyC = code.getParam(1);
      ILocal<DataPool<?>> datP = code.getParam(2);
      code.assign(self, dyC, dyType);
      code.assign(self, datP, dataPool);
    }

    OVERRIDES.clear();
    INTERFACE_TEMP.clear();

    Stack<Class<?>> interfaceStack = new Stack<>();
    HashMap<String, HashSet<FunctionType>> overrideMethods = new HashMap<>();

    Class<?> curr = baseClass;

    ClassInfo<?> typeClass;
    MethodInfo<?, ?> superMethod;
    while(curr != null || !interfaceStack.empty()){
      if(curr != null){
        for(Class<?> i: curr.getInterfaces()){
          if(INTERFACE_TEMP.add(i)) interfaceStack.push(i);
        }
      }
      else curr = interfaceStack.pop();

      typeClass = ClassInfo.asType(curr);
      for(Method method: curr.getDeclaredMethods()){
        // 如果方法是静态的，或者方法不对子类可见则不重写此方法
        if(Modifier.isStatic(method.getModifiers())) continue;
        if((method.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0) continue;

        if(Modifier.isFinal(method.getModifiers())) continue;

        if(!overrideMethods.computeIfAbsent(method.getName(), e -> new HashSet<>()).add(FunctionType.from(method))) continue;

        String methodName = method.getName();
        ClassInfo<?> returnType = ClassInfo.asType(method.getReturnType());

        if(OVERRIDES.computeIfAbsent(methodName, e -> new HashSet<>()).add(FunctionType.from(method))){
          superMethod = !Modifier.isAbstract(method.getModifiers()) && !curr.isInterface() || method.isDefault()? typeClass.getMethod(
              returnType,
              methodName,
              Arrays.stream(method.getParameterTypes()).map(ClassInfo::asType).toArray(ClassInfo[]::new)
          ): null;

          // public *returnType* *name*(*parameters*){
          //   *[return]* this.invoke("*name*", parameters);
          // }
          {
            CodeBlock<?> code = classInfo.declareMethod(
                Modifier.PUBLIC,
                methodName,
                returnType,
                Parameter.asParameter(method.getParameters())
            );

            ILocal<String> met = code.local(ClassInfo.STRING_TYPE);
            ILocal<Object[]> params = code.local(ClassInfo.OBJECT_TYPE.asArray());
            ILocal<Integer> length = code.local(ClassInfo.INT_TYPE);

            code.loadConstant(met, method.getName());

            code.loadConstant(length, method.getParameterCount());
            code.newArray(ClassInfo.OBJECT_TYPE, params, length);

            ILocal<Integer> index = code.local(ClassInfo.INT_TYPE);
            for(int i = 0; i < code.getParamList().size(); i++){
              code.loadConstant(index, i);
              code.arrayPut(params, index, code.getParam(i));
            }

            if(returnType != ClassInfo.VOID_TYPE){
              ILocal res = code.local(returnType);
              code.invoke(code.getThis(), INVOKE, res, met, params);
              code.returnValue(res);
            }
            else code.invoke(code.getThis(), INVOKE, null, met, params);
          }

          // public *returnType* *name*$super(*parameters*){
          //   *[return]* super.*name*(*parameters*);
          // }
          if(superMethod != null){
            CodeBlock<?> code = classInfo.declareMethod(
                Modifier.PUBLIC,
                methodName + CALLSUPER,
                returnType,
                Parameter.asParameter(method.getParameters())
            );

            if(returnType != ClassInfo.VOID_TYPE){
              ILocal res = code.local(returnType);
              code.invokeSuper(code.getThis(), superMethod, res, code.getParamList().toArray(ILocal[]::new));
              code.returnValue(res);
            }
            else code.invokeSuper(code.getThis(), superMethod, null, code.getParamList().toArray(ILocal[]::new));
          }
        }
      }

      if(!curr.isInterface()){
        curr = curr.getSuperclass();
      }
      else curr = null;
    }

    // public DynamicClass<Self> getDyClass(){
    //   return this.$dynamic_type$;
    // }
    {
      CodeBlock<DynamicClass> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "getDyClass",
          DYNAMIC_CLASS_TYPE
      );
      ILocal<DynamicClass> res = code.local(DYNAMIC_CLASS_TYPE);
      code.assign(code.getThis(), dyType, res);
      code.returnValue(res);
    }

    // public DataPool<Self>.ReadOnlyPool superPoint(){
    //   return this.$datapool$.getSuper();
    // }
    {
      CodeBlock<DataPool.ReadOnlyPool> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "superPoint",
          READONLY_POOL_TYPE
      );
      ILocal<DataPool.ReadOnlyPool> res = code.local(READONLY_POOL_TYPE);
      ILocal<DataPool> pool = code.local(DATA_POOL_TYPE);
      code.assign(code.getThis(), dataPool, pool);
      code.invoke(pool, GET_SUPER, res);
      code.returnValue(res);
    }

    // public <T> T getVar(String name){
    //   return this.$datapool$.get(name);
    // }
    {
      CodeBlock<Object> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "getVar",
          ClassInfo.OBJECT_TYPE,
          Parameter.as(0, ClassInfo.STRING_TYPE, "name")
      );
      ILocal<DataPool> pool = code.local(DATA_POOL_TYPE);
      code.assign(code.getThis(), dataPool, pool);

      ILocal<Object> result = code.local(ClassInfo.OBJECT_TYPE);
      code.invoke(pool, GET, result, code.getParam(1));
      code.returnValue(result);
    }

    // <T> void setVar(String name, T value){
    //   this.$datapool$.set(name, value);
    // }
    {
      CodeBlock<Void> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "setVar",
          ClassInfo.VOID_TYPE,
          Parameter.as(
              0, ClassInfo.STRING_TYPE, "name",
              0, ClassInfo.OBJECT_TYPE, "value"
          )
      );
      ILocal<DataPool> pool = code.local(DATA_POOL_TYPE);
      code.assign(code.getThis(), dataPool, pool);

      code.invoke(pool, SETVAR, null, code.getParam(1), code.getParam(2));
      code.returnVoid();
    }

    // <R> Function<Self, R> getFunc(String name, FunctionType type){
    //   return this.$datapool$.select(name, type);
    // }
    {
      CodeBlock<Function> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "getFunc",
          FUNCTION_TYPE,
          Parameter.as(
              0, ClassInfo.STRING_TYPE, "name",
              0, FUNCTION_TYPE_TYPE, "type"
          )
      );
      ILocal<DataPool> pool = code.local(DATA_POOL_TYPE);
      code.assign(code.getThis(), dataPool, pool);

      ILocal<Function> fnc = code.local(FUNCTION_TYPE);
      code.invoke(pool, SELECT, fnc, code.getParam(1), code.getParam(2));
      code.returnValue(fnc);
    }

    // <R> void setFunc(String name, Function<Self, R> func, Class<?>... argTypes){
    //   this.$datapool$.set(name, func, argTypes);
    // }
    {
      CodeBlock<Void> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "setFunc",
          ClassInfo.VOID_TYPE,
          Parameter.as(
              0, ClassInfo.STRING_TYPE, "name",
              0, FUNCTION_TYPE, "func",
              0, ClassInfo.CLASS_TYPE.asArray(), "argTypes"
          )
      );

      ILocal<DataPool> pool = code.local(DATA_POOL_TYPE);
      code.assign(code.getThis(), dataPool, pool);

      code.invoke(pool, SETFUNC, null, code.getParam(1), code.getParam(2), code.getParam(3));
    }

    AnnotationType<DynamicType> dycAnno = AnnotationType.asAnnotationType(DynamicType.class);
    dycAnno.annotateTo(classInfo, null);

    return classInfo;
  }

  protected abstract <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces, DynamicClass dynamicClass);

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DynamicType{}

  private static class ClassImplements<T>{
    final Class<T> base;
    final Class<?>[] interfaces;

    public ClassImplements(Class<T> base, Class<?>[] interfaces){
      this.base = base;
      this.interfaces = interfaces;
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
}
