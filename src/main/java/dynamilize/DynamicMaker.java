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

import static dynamilize.ArgumentList.argLenArray;

/**动态类型运作的核心工厂类型，用于将传入的动态类型与委托基类等构造出动态委托类型以及其实例。
 * <p>定义了基于{@link ASMGenerator}的默认生成器实现，通过{@link DynamicMaker#getDefault()}获取该实例以使用
 * <p>若需要特殊的实现，则需要重写/实现此类的方法，主要的方法：
 * <ul>
 * <li><strong>{@link DynamicMaker#makeClassInfo(Class, Class[])}</strong>：决定此工厂如何构造委托类的描述信息
 * <li><strong>{@link DynamicMaker#generateClass(Class, Class[])}</strong>：决定如何解析描述信息生成字节码以及如何加载字节码为java类
 * </ul>
 * 实施时必须按照方法的描述给出基本的实现，不应该改变原定的最低上下文请求。
 * <pre>
 * 使用方法可参阅：
 *   {@link DynamicClass}
 * DynamicMaker的方法：
 *   {@link DynamicMaker#newInstance(DynamicClass)}
 *   {@link DynamicMaker#newInstance(Class[], DynamicClass)}
 *   {@link DynamicMaker#newInstance(Class, DynamicClass, Object...)}
 *   {@link DynamicMaker#newInstance(Class, Class[], DynamicClass, Object...)}
 * </pre>
 *
 * @see DynamicClass
 * @see DynamicObject
 * @see DataPool
 *
 * @author EBwilson */
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
  public static final IMethod<DataPool, Void> SET_OWNER = DATA_POOL_TYPE.getMethod(ClassInfo.VOID_TYPE, "setOwner", ClassInfo.OBJECT_TYPE);
  public static final IMethod<DataPool, Function> SELECT = DATA_POOL_TYPE.getMethod(FUNCTION_TYPE, "select", ClassInfo.STRING_TYPE, FUNCTION_TYPE_TYPE);
  public static final IMethod<DynamicObject, Object> INVOKE = DYNAMIC_OBJECT_TYPE.getMethod(ClassInfo.OBJECT_TYPE, "invokeFunc", ClassInfo.STRING_TYPE, ClassInfo.OBJECT_TYPE.asArray());

  private static final Map<String, Set<FunctionType>> OVERRIDES = new HashMap<>();
  private static final Set<Class<?>> INTERFACE_TEMP = new HashSet<>();
  private static final Class[] EMPTY_CLASSES = new Class[0];
  public static final ILocal[] LOCALS_EMP = new ILocal[0];

  private final JavaHandleHelper helper;

  private final HashMap<ClassImplements<?>, Class<?>> pool = new HashMap<>();
  private final HashMap<Class<?>, HashMap<FunctionType, MethodHandle>> constructors = new HashMap<>();

  /**创建一个实例，并传入其要使用的{@linkplain JavaHandleHelper java行为支持器}，子类引用此构造器可能直接设置默认的行为支持器而无需外部传入*/
  protected DynamicMaker(JavaHandleHelper helper){
    this.helper = helper;
  }

  /**获取默认的动态类型工厂，工厂具备基于{@link ASMGenerator}与适用于<i>HotSpot JVM</i>运行时的{@link JavaHandleHelper}进行的实现。
   * 适用于：
   * <ul>
   * <li>java运行时版本1.8的所有jvm
   * <li>java运行时版本大于等于1.9的<i>甲骨文HotSpot JVM</i>
   * <li><strong><i>IBM OpenJ9</i>运行时尚未支持</strong>
   * </ul>
   * 若有范围外的需求，可按需要进行实现*/
  public static DynamicMaker getDefault(){
    BaseClassLoader loader = new BaseClassLoader(DynamicMaker.class.getClassLoader());
    ASMGenerator generator = new ASMGenerator(loader, Opcodes.V1_8);

    return new DynamicMaker(acc -> {
      acc.setAccessible(true);
    }){
      @Override
      protected <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces){
        return makeClassInfo(baseClass, interfaces).generate(generator);
      }
    };
  }

  /**使用默认构造函数构造没有实现额外接口的动态类的实例，实例的java类型委托类为{@link Object}
   *
   * @param dynamicClass 用于实例化的动态类型
   * @return 构造出的动态实例*/
  public DynamicObject newInstance(DynamicClass dynamicClass){
    return newInstance(EMPTY_CLASSES, dynamicClass);
  }

  /**使用默认构造函数构造动态类的实例，实例实现了给出的接口列表，java类型委托为{@link Object}
   *
   * @param interfaces 实例实现的接口列表
   * @param dynamicClass 用于实例化的动态类型
   * @return 构造出的动态实例*/
  @SuppressWarnings("rawtypes")
  public DynamicObject newInstance(Class<?>[] interfaces, DynamicClass dynamicClass){
    return (DynamicObject) newInstance(Object.class, interfaces, dynamicClass);
  }

  /**用给出的构造函数参数构造动态类的实例，参数表必须可以在委托的java类型中存在匹配的可用构造器。
   * <p>实例无额外接口，类型委托由参数确定
   *
   * @param base 执行委托的java类型，这将决定此实例可分配到的类型
   * @param dynamicClass 用于实例化的动态类型
   * @param args 构造函数实参
   * @return 构造出的动态实例
   *
   * @throws RuntimeException 若构造函数实参无法匹配到相应的构造器或者存在其他异常*/
  public <T> T newInstance(Class<T> base, DynamicClass dynamicClass, Object... args){
    return newInstance(base, EMPTY_CLASSES, dynamicClass, args);
  }

  /**用给出的构造函数参数构造动态类的实例，参数表必须可以在委托的java类型中存在匹配的可用构造器。
   * <p>实例实现了给出的接口列表，类型委托由参数确定
   *
   * @param base 执行委托的java类型，这将决定此实例可分配到的类型
   * @param interfaces 实例实现的接口列表
   * @param dynamicClass 用于实例化的动态类型
   * @param args 构造函数实参
   * @return 构造出的动态实例
   *
   * @throws RuntimeException 若构造函数实参无法匹配到相应的构造器或者存在其他异常*/
  @SuppressWarnings("unchecked")
  public <T> T newInstance(Class<T> base, Class<?>[] interfaces, DynamicClass dynamicClass, Object... args){
    checkBase(base);

    Class<? extends T> clazz = getDynamicBase(base, interfaces);
    try{
      List<Object> argsLis = new ArrayList<>(Arrays.asList(dynamicClass, genPool(clazz, dynamicClass)));
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

      return inst;
    }catch(Throwable e){
      throw new RuntimeException(e);
    }
  }

  /**检查委托基类的可用性，基类若为final或者不是动态委托产生的类型则都会抛出异常*/
  protected <T> void checkBase(Class<T> base){
    if(base.getAnnotation(DynamicType.class) != null)
      throw new IllegalHandleException("cannot derive a dynamic class with a dynamic class");

    if(base.isInterface())
      throw new IllegalHandleException("interface cannot use to derive a dynamic class, please write it in parameter \"interfaces\" to implement this interface");

    if(Modifier.isFinal(base.getModifiers()))
      throw new IllegalHandleException("cannot derive a dynamic class with a final class");
  }

  /**生成动态类型相应的数据池，数据池完成动态类型向上对超类的迭代逐级分配数据池信息，同时对委托产生的动态类型生成所有方法和字段的函数/变量入口并放入数据池
   *
   * @param base 动态委托类
   * @param dynamicClass 描述行为的动态类型
   * @return 生成的动态类型数据池*/
  protected <T> DataPool<T> genPool(Class<? extends T> base, DynamicClass dynamicClass){
    DataPool<T> basePool = new DataPool<>(null);
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    for(Method method: base.getDeclaredMethods()){
      CallSuperMethod callSuper = method.getAnnotation(CallSuperMethod.class);
      if(callSuper != null){
        String name = callSuper.srcMethod();
        try{
          MethodHandle handle = lookup.unreflect(method);

          basePool.set(name, (self, args) -> {
            Object[] argArr = args.args();
            Object[] arg = argLenArray[argArr.length + 1];
            arg[0] = self;
            System.arraycopy(argArr, 0, arg, 1, argArr.length);

            try{
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

  /**根据委托基类和实现的接口获取生成的动态类型实例的类型，类型会生成并放入池，下一次获取会直接从池中取出该类型
   *
   * @param base 委托的基类
   * @param interfaces 需要实现的接口列表*/
  @SuppressWarnings("unchecked")
  protected <T> Class<? extends T> getDynamicBase(Class<T> base, Class<?>[] interfaces){
    return (Class<? extends T>) pool.computeIfAbsent(new ClassImplements<>(base, interfaces), e -> generateClass(base, interfaces));
  }

  /**由基类与接口列表建立动态类的打包名称，打包名称具有唯一性（或者足够高的离散性，不应出现频繁的碰撞）和不变性
   *
   * @param baseClass 基类
   * @param interfaces 接口列表
   * @return 由基类名称与全部接口名称的哈希值构成的打包名称*/
  public static <T> String getDynamicName(Class<T> baseClass, Class<?>... interfaces){
    return baseClass.getName() + "$dynamic$" + Arrays.hashCode(Arrays.stream(interfaces).map(Class::getName).toArray());
  }

  /**创建动态实例类型的类型标识，这应当覆盖所有委托目标类的方法和实现的接口中的方法，若超类的某一成员方法不是抽象的，需保留对超类方法的入口，
   * 再重写本方法，对超类方法的入口需要有一定的标识以供生成基类数据池的引用函数时使用。
   * <p>对于给定的基类和接口列表，生成的动态实例基类的名称是唯一的（或者足够的离散以至于几乎不可能碰撞）。
   * <p>关于此方法实现需要完成的工作，请参阅{@link DynamicObject}中的描述
   *
   * @param baseClass 委托基类
   * @param interfaces 实现的接口列表
   * @return 完成了所有必要描述的类型标识*/
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected <T> ClassInfo<? extends T> makeClassInfo(Class<T> baseClass, Class<?>[] interfaces){
    ArrayList<ClassInfo<?>> inter = new ArrayList<>(interfaces.length + 1);
    inter.add(ClassInfo.asType(DynamicObject.class));

    for(Class<?> i: interfaces){
      inter.add(ClassInfo.asType(i));
    }

    ClassInfo<? extends T> classInfo = new ClassInfo<>(
        Modifier.PUBLIC,
        getDynamicName(baseClass, interfaces),
        ClassInfo.asType(baseClass),
        inter.toArray(new ClassInfo[0])
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
    //   this.$datapool$.setOwner(this);
    // }
    for(Constructor<?> cstr: baseClass.getDeclaredConstructors()){
      if((cstr.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0) continue;

      List<Parameter<?>> params = Arrays.asList(Parameter.as(paramBase));
      List<Parameter<?>> superParams = Arrays.asList(Parameter.asParameter(cstr.getParameters()));
      params.addAll(superParams);

      IMethod<?, Void> constructor = classInfo.superClass().getConstructor(superParams.stream().map(Parameter::getType).toArray(IClass[]::new));

      CodeBlock<Void> code = classInfo.declareConstructor(Modifier.PUBLIC, params.toArray(new Parameter[0]));
      List<ILocal<?>> l = code.getParamList();
      ILocal<?> self = code.getThis();
      code.invokeSuper(self, constructor, null, l.subList(2, l.size()).toArray(LOCALS_EMP));

      ILocal<DynamicClass> dyC = code.getParam(1);
      ILocal<DataPool<?>> datP = code.getParam(2);
      code.assign(self, dyC, dyType);
      code.assign(self, datP, dataPool);

      code.invoke(datP, SET_OWNER, null, self);
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
              code.invokeSuper(code.getThis(), superMethod, res, code.getParamList().toArray(LOCALS_EMP));
              code.returnValue(res);
            }
            else code.invokeSuper(code.getThis(), superMethod, null, code.getParamList().toArray(LOCALS_EMP));

            AnnotationType<CallSuperMethod> callSuper = AnnotationType.asAnnotationType(CallSuperMethod.class);
            HashMap<String, Object> map = new HashMap<>();
            map.put("srcMethod", methodName);
            callSuper.annotateTo(code.owner(), map);
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

  /**生成委托自基类并实现了给出的接口列表的类型，而类的行为描述请参考{@link DynamicMaker#makeClassInfo(Class, Class[])}，类型描述会在此方法产出。
   * <p>该方法需要做的事通常是将makeClassInfo获得的类型标识进行生成并加载其表示的java类型
   *
   * @param baseClass 委托基类
   * @param interfaces 实现的接口列表
   * @return 对全方法进行动态委托的类型*/
  protected abstract <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces);

  /**动态委托类型标识，由此工厂生成的动态委托类型都会具有此注解标识*/
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DynamicType{}

  /**超类方法标识，带有一个属性描述了此方法所引用的超类方法名称，所有生成的对super方法入口都会具有此注解。*/
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface CallSuperMethod{
    /**方法所调用的超类源方法，将提供给初级数据池标识对超类方法的引用*/
    String srcMethod();
  }

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
      if(!(o instanceof ClassImplements<?>)) return false;
      ClassImplements<?> that = (ClassImplements<?>) o;
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
