package dynamilize;

import dynamilize.annotation.AspectInterface;
import dynamilize.classmaker.*;
import dynamilize.classmaker.code.*;
import dynamilize.classmaker.code.annotation.AnnotationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static dynamilize.classmaker.ClassInfo.*;
import static dynamilize.classmaker.CodeBlock.stack;

/**
 * 动态类型运作的核心工厂类型，用于将传入的动态类型与委托基类等构造出动态委托类型以及其实例。
 * <p>定义了基于{@link ASMGenerator}的默认生成器实现，通过{@link DynamicMaker#getDefault()}获取该实例以使用
 * <p>若需要特殊的实现，则需要重写/实现此类的方法，主要的方法：
 * <ul>
 * <li><strong>{@link DynamicMaker#makeClassInfo(Class, Class[], Class[])}</strong>：决定此工厂如何构造委托类的描述信息
 * <li><strong>{@link DynamicMaker#generateClass(Class, Class[], Class[])}</strong>：决定如何解析描述信息生成字节码以及如何加载字节码为java类
 * </ul>
 * 实施时必须按照方法的描述给出基本的实现，不应该改变原定的最低上下文请求。
 * <pre>
 * 使用方法可参阅：
 *   {@link DynamicClass}
 * DynamicMaker的方法：
 *   {@link DynamicMaker#newInstance(DynamicClass)}
 *   {@link DynamicMaker#newInstance(Class[], Class[], DynamicClass)}
 *   {@link DynamicMaker#newInstance(Class, Class[], DynamicClass, Object...)}
 *   {@link DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)}
 * </pre>
 *
 * @author EBwilson
 * @see DynamicClass
 * @see AspectInterface
 * @see DynamicObject
 * @see DataPool
 */
@SuppressWarnings({"rawtypes", "DuplicatedCode"})
public abstract class DynamicMaker {
  public static final String CALLSUPER = "$super";

  private static final HashSet<String> INTERNAL_FIELD = new HashSet<>(Arrays.asList(
      "$dynamic_type$",
      "$datapool$",
      "$varValuePool$",
      "$superbasepointer$"
  ));

  public static final ClassInfo<DynamicClass> DYNAMIC_CLASS_TYPE = asType(DynamicClass.class);
  public static final ClassInfo<DynamicObject> DYNAMIC_OBJECT_TYPE = asType(DynamicObject.class);
  public static final ClassInfo<DataPool> DATA_POOL_TYPE = asType(DataPool.class);
  public static final ClassInfo<FunctionType> FUNCTION_TYPE_TYPE = asType(FunctionType.class);
  public static final MethodInfo<FunctionType, FunctionType> TYPE_INST = FUNCTION_TYPE_TYPE.getMethod(
      FUNCTION_TYPE_TYPE,
      "inst",
      CLASS_TYPE.asArray());
  public static final ClassInfo<Function> FUNCTION_TYPE = asType(Function.class);
  public static final ClassInfo<DataPool.ReadOnlyPool> READONLY_POOL_TYPE = asType(DataPool.ReadOnlyPool.class);
  public static final ClassInfo<Integer> INTEGER_CLASS_TYPE = asType(Integer.class);
  public static final ClassInfo<HashMap> HASH_MAP_TYPE = asType(HashMap.class);
  public static final ClassInfo<IVariable> VAR_TYPE = ClassInfo.asType(IVariable.class);
  public static final ClassInfo<NoSuchMethodException> NOSUCH_METHOD = asType(NoSuchMethodException.class);
  public static final ClassInfo<ArgumentList> ARG_LIST_TYPE = asType(ArgumentList.class);
  public static final ClassInfo<Function.SuperGetFunction> SUPER_GET_FUNC_TYPE = ClassInfo.asType(Function.SuperGetFunction.class);
  public static final ClassInfo<IFunctionEntry> FUNC_ENTRY_TYPE = ClassInfo.asType(IFunctionEntry.class);

  public static final IMethod<DataPool, DataPool.ReadOnlyPool> GET_READER = DATA_POOL_TYPE.getMethod(READONLY_POOL_TYPE, "getReader", DYNAMIC_OBJECT_TYPE);
  public static final IMethod<HashMap, Object> MAP_GET = HASH_MAP_TYPE.getMethod(OBJECT_TYPE, "get", OBJECT_TYPE);
  public static final IMethod<HashMap, Object> MAP_GET_DEF = HASH_MAP_TYPE.getMethod(OBJECT_TYPE, "getOrDefault", OBJECT_TYPE, OBJECT_TYPE);
  public static final IMethod<Integer, Integer> VALUE_OF = INTEGER_CLASS_TYPE.getMethod(INTEGER_CLASS_TYPE, "valueOf", INT_TYPE);
  public static final IMethod<HashMap, Object> MAP_PUT = HASH_MAP_TYPE.getMethod(OBJECT_TYPE, "put", OBJECT_TYPE, OBJECT_TYPE);
  public static final IMethod<DataPool, IVariable> GET_VAR = DATA_POOL_TYPE.getMethod(VAR_TYPE, "getVariable", STRING_TYPE);
  public static final IMethod<DataPool, Void> SET_VAR = DATA_POOL_TYPE.getMethod(ClassInfo.VOID_TYPE, "setVariable", VAR_TYPE);
  public static final IMethod<DataPool, Void> SETFUNC = DATA_POOL_TYPE.getMethod(ClassInfo.VOID_TYPE, "setFunction", STRING_TYPE, FUNCTION_TYPE, ClassInfo.CLASS_TYPE.asArray());
  public static final IMethod<DataPool, Void> SETFUNC2 = DATA_POOL_TYPE.getMethod(ClassInfo.VOID_TYPE, "setFunction", STRING_TYPE, SUPER_GET_FUNC_TYPE, ClassInfo.CLASS_TYPE.asArray());
  public static final IMethod<DataPool, IFunctionEntry> SELECT = DATA_POOL_TYPE.getMethod(FUNC_ENTRY_TYPE, "select", STRING_TYPE, FUNCTION_TYPE_TYPE);
  public static final IMethod<DataPool, Void> INIT = DATA_POOL_TYPE.getMethod(VOID_TYPE, "init", DYNAMIC_OBJECT_TYPE, OBJECT_TYPE.asArray());
  public static final IMethod<DynamicObject, Object> INVOKE = DYNAMIC_OBJECT_TYPE.getMethod(OBJECT_TYPE, "invokeFunc", FUNCTION_TYPE_TYPE, STRING_TYPE, OBJECT_TYPE.asArray());
  public static final IMethod<ArgumentList, Object[]> GET_LIST = ARG_LIST_TYPE.getMethod(OBJECT_TYPE.asArray(), "getList", INT_TYPE);
  public static final IMethod<ArgumentList, Void> RECYCLE_LIST = ARG_LIST_TYPE.getMethod(VOID_TYPE, "recycleList", OBJECT_TYPE.asArray());

  private static final Map<String, Set<FunctionType>> OVERRIDES = new HashMap<>();
  private static final Map<String, Set<FunctionType>> FINALS = new HashMap<>();
  private static final Map<String, Set<FunctionType>> ABSTRACTS = new HashMap<>();
  private static final Set<Class<?>> INTERFACE_TEMP = new HashSet<>();
  private static final Stack<Class<?>> INTERFACE_STACK = new Stack<>();
  private static final Class[] EMPTY_CLASSES = new Class[0];
  public static final ILocal[] LOCALS_EMP = new ILocal[0];
  public static final HashSet<FunctionType> EMP_SET = new HashSet<>();
  public static final HashMap<String, Object> EMP_MAP = new HashMap<>();
  public static final String ANY = "ANY";
  private final JavaHandleHelper helper;

  private final HashMap<ClassImplements<?>, Class<?>> classPool = new HashMap<>();
  private final HashMap<Class<?>, DataPool> classPoolsMap = new HashMap<>();
  private final HashMap<Class<?>, HashMap<FunctionType, Constructor<?>>> constructors = new HashMap<>();

  /**
   * 创建一个实例，并传入其要使用的{@linkplain JavaHandleHelper java行为支持器}，子类引用此构造器可能直接设置默认的行为支持器而无需外部传入
   */
  protected DynamicMaker(JavaHandleHelper helper) {
    this.helper = helper;
  }

  /**
   * @deprecated DynamicMaker的预制创建已转移到baseimpl模块DynamicFactory，此API将被移除
   */
  @Deprecated
  public static DynamicMaker getDefault() {
    throw new IllegalHandleException("please use dynamilize.DynamicFactory in module JavaDynamilizer.baseimpl");
  }

  /**
   * 构造一个派生自{@link Object}的全委托动态实例
   * <p>给出的{@linkplain AspectInterface 切面接口}表会决定这个实例被动态化的行为，若为null（<strong>非空数组，空数组表明不执行委托</strong>）则为全委托。
   *
   * @see DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)
   */
  public DynamicObject newInstance(DynamicClass dynamicClass) {
    return newInstance(Object.class, dynamicClass);
  }

  /**
   * 从给出的参数列表构造一个全委托动态实例
   * <p>给出的{@linkplain AspectInterface 切面接口}表会决定这个实例被动态化的行为，若为null（<strong>非空数组，空数组表明不执行委托</strong>）则为全委托。
   *
   * @see DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)
   */
  @SuppressWarnings("rawtypes")
  public DynamicObject newInstance(Class<?>[] interfaces, DynamicClass dynamicClass) {
    return newInstance(Object.class, interfaces, (Class<?>[]) null, dynamicClass);
  }

  /**
   * 用给出的接口列表构造动态实例
   * <p>给出的{@linkplain AspectInterface 切面接口}表会决定这个实例被动态化的行为，若为null（<strong>非空数组，空数组表明不执行委托</strong>）则为全委托。
   *
   * @see DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)
   */
  @SuppressWarnings("rawtypes")
  public DynamicObject newInstance(Class<?>[] interfaces, Class<?>[] aspects, DynamicClass dynamicClass) {
    return newInstance(Object.class, interfaces, aspects, dynamicClass);
  }

  /**
   * 用给出的构造函数参数构造全委托动态类的实例，参数表必须可以在委托的java类型中存在匹配的可用构造器。实例无额外接口，类型委托由参数确定
   * <p>给出的{@linkplain AspectInterface 切面接口}表会决定这个实例被动态化的行为，若为null（<strong>非空数组，空数组表明不执行委托</strong>）则为全委托。
   *
   * @see DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)
   */
  public <T> DynamicObject<T> newInstance(Class<T> base, DynamicClass dynamicClass, Object... args) {
    return newInstance(base, EMPTY_CLASSES, dynamicClass, null, args);
  }

  /**
   * 用给出的构造函数参数构造动态类的实例，参数表必须可以在委托的java类型中存在匹配的可用构造器。实例无额外接口，类型委托由参数确定
   * <p>给出的{@linkplain AspectInterface 切面接口}表会决定这个实例被动态化的行为，若为null（<strong>非空数组，空数组表明不执行委托</strong>）则为全委托。
   *
   * @see DynamicMaker#newInstance(Class, Class[], Class[], DynamicClass, Object...)
   */
  public <T> DynamicObject<T> newInstance(Class<T> base, Class<?>[] aspects, DynamicClass dynamicClass, Object... args) {
    return newInstance(base, EMPTY_CLASSES, aspects, dynamicClass, args);
  }

  /**
   * 用给出的构造函数参数构造动态类的实例，参数表必须可以在委托的java类型中存在匹配的可用构造器。实例实现给出的接口列表，类型委托由参数确定
   * <p>给出的{@linkplain AspectInterface 切面接口}表会决定这个实例被动态化的行为，若为null（<strong>非空数组，空数组表明不执行委托</strong>）则为全委托。
   * <p>详见{@link AspectInterface}
   *
   * <p><strong>注意，在您使用切面接口限定委托范围时，必须保证所有的抽象方法（来自抽象类委托及实现的接口）都在切面范围内，否则会抛出错误</strong>
   *
   * @param base         执行委托的java类型，这将决定此实例可分配到的类型
   * @param interfaces   实例实现的接口列表
   * @param aspects      切面接口列表
   * @param dynamicClass 用于实例化的动态类型
   * @param args         构造函数实参
   * @return 构造出的动态实例
   * @throws IllegalHandleException 若构造函数实参无法匹配到相应的构造器，或者有抽象方法未被处理
   * @see AspectInterface
   */
  @SuppressWarnings("unchecked")
  public <T> DynamicObject<T> newInstance(Class<T> base, Class<?>[] interfaces, Class<?>[] aspects, DynamicClass dynamicClass, Object... args) {
    checkBase(base);

    Class<? extends T> clazz = getDynamicBase(base, interfaces, aspects);
    try {
      List<Object> argsLis = new ArrayList<>(Arrays.asList(
          dynamicClass,
          genPool(clazz, dynamicClass),
          classPoolsMap.get(clazz)
      ));
      argsLis.addAll(Arrays.asList(args));

      Constructor<?> cstr = null;
      for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
        FunctionType t;
        if ((t = FunctionType.from(constructor)).match(argsLis.toArray())) {
          cstr = constructor;
          break;
        }
        t.recycle();
      }
      if (cstr == null)
        throw new NoSuchMethodError("no matched constructor found with parameter " + Arrays.toString(args));

      FunctionType type = FunctionType.inst(cstr.getParameterTypes());
      Constructor c = cstr;
      DynamicObject<T> inst = (DynamicObject<T>) constructors.computeIfAbsent(clazz, e -> new HashMap<>())
          .computeIfAbsent(type, t -> {
            helper.makeAccess(c);
            return c;
          }).newInstance(argsLis.toArray());

      type.recycle();

      return inst;
    } catch (Throwable e) {
      throw new IllegalHandleException(e);
    }
  }

  /**
   * 获取此maker的{@linkplain JavaHandleHelper java行为支持器}
   */
  public JavaHandleHelper getHelper() {
    return helper;
  }

  /**
   * 检查委托基类的可用性，基类若为final或者基类为接口则抛出异常
   */
  protected <T> void checkBase(Class<T> base) {
    if (base.isInterface())
      throw new IllegalHandleException("interface cannot use to derive a dynamic class, please write it in parameter \"interfaces\" to implement this interface");

    if (Modifier.isFinal(base.getModifiers()))
      throw new IllegalHandleException("cannot derive a dynamic class with a final class");
  }

  /**
   * 生成动态类型相应的数据池，数据池完成动态类型向上对超类的迭代逐级分配数据池信息，同时对委托产生的动态类型生成所有方法和字段的函数/变量入口并放入数据池
   *
   * @param base         动态委托类
   * @param dynamicClass 描述行为的动态类型
   * @return 生成的动态类型数据池
   */
  protected <T> DataPool genPool(Class<? extends T> base, DynamicClass dynamicClass) {
    DataPool basePool = classPoolsMap.computeIfAbsent(base, clazz -> {
      AtomicBoolean immutable = new AtomicBoolean();
      DataPool res = new DataPool(null) {
        @Override
        public void setFunction(String name, Function<?, ?> function, Class<?>... argsType) {
          if (immutable.get())
            throw new IllegalHandleException("immutable pool");

          super.setFunction(name, function, argsType);
        }

        @Override
        public void setVariable(IVariable var) {
          if (immutable.get())
            throw new IllegalHandleException("immutable pool");

          super.setVariable(var);
        }
      };

      Class<?> curr = clazz;
      while (curr != null) {
        if (curr.getAnnotation(DynamicType.class) != null) {
          for (Method method : curr.getDeclaredMethods()) {
            DynamicMethod callSuper = method.getAnnotation(DynamicMethod.class);
            if (callSuper != null) {
              String signature = FunctionType.signature(method.getName(), method.getParameterTypes());
              res.setFunction(
                  method.getName(),
                  (self, args) -> {
                    try {
                      return ((SuperInvoker) self).invokeSuper(signature, args.args());
                    } catch (NoSuchMethodException e) {
                      throw new RuntimeException(e);
                    }
                  },
                  method.getParameterTypes()
              );
            }
          }
          curr = curr.getSuperclass();
        }

        for (Field field : curr.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers()) || isInternalField(field.getName())) continue;

          helper.makeAccess(field);
          res.setVariable(helper.genJavaVariableRef(field, res));
        }
        curr = curr.getSuperclass();
      }

      immutable.set(true);

      return res;
    });

    return dynamicClass.genPool(basePool);
  }

  private static boolean isInternalField(String name) {
    return INTERNAL_FIELD.contains(name);
  }

  /**
   * 根据委托基类和实现的接口获取生成的动态类型实例的类型，类型会生成并放入池，下一次获取会直接从池中取出该类型
   *
   * @param base       委托的基类
   * @param interfaces 需要实现的接口列表
   */
  @SuppressWarnings("unchecked")
  protected <T> Class<? extends T> getDynamicBase(Class<T> base, Class<?>[] interfaces, Class<?>[] aspects) {
    return (Class<? extends T>) classPool.computeIfAbsent(new ClassImplements<>(base, interfaces, aspects), e -> {
      Class<?> c = base;

      while (c != null) {
        helper.makeAccess(c);
        c = c.getSuperclass();
      }

      return generateClass(handleBaseClass(base), interfaces, aspects);
    });
  }

  /**
   * 由基类与接口列表建立动态类的打包名称，打包名称具有唯一性（或者足够高的离散性，不应出现频繁的碰撞）和不变性
   *
   * @param baseClass  基类
   * @param interfaces 接口列表
   * @return 由基类名称与全部接口名称的哈希值构成的打包名称
   */
  public static <T> String getDynamicName(Class<T> baseClass, Class<?>... interfaces) {
    return ensurePackage(baseClass.getName()) + "$dynamic$" + FunctionType.typeNameHash(interfaces);
  }

  private static String ensurePackage(String name) {
    if (name.startsWith("java.")) {
      return name.replaceFirst("java\\.", "lava.");
    }
    return name;
  }

  /**
   * 对动态委托产生的类型进行继续委托创建代码的方法，应当将首要处理过程向上传递给超类。
   * 与{@link DynamicMaker#makeClassInfo(Class, Class[], Class[])}的行为相似，但需要将部分行为适应到已经具备委托行为的超类
   */
  @SuppressWarnings({"unchecked"})
  protected <T> ClassInfo<? extends T> makeClassInfoOnDynmaic(Class<T> baseClass, Class<?>[] interfaces, Class<?>[] aspects) {
    LinkedHashSet<ClassInfo<?>> inter = new LinkedHashSet<>();
    inter.add(asType(DynamicObject.class));
    inter.add(asType(SuperInvoker.class));

    for (Class<?> i : interfaces) {
      inter.add(asType(i));
    }

    INTERFACE_STACK.clear();
    INTERFACE_TEMP.clear();
    HashMap<String, HashSet<FunctionType>> aspectPoints = new HashMap<>();
    if (aspects != null) {
      for (Class<?> i : aspects) {
        inter.add(asType(i));
        INTERFACE_STACK.push(i);
      }

      while (!INTERFACE_STACK.isEmpty()) {
        Class<?> i = INTERFACE_STACK.pop();
        if (i.getAnnotation(AspectInterface.class) == null)
          throw new IllegalHandleException("aspect interfaces must has AspectInterface annotated, but " + i + " doesn't");

        if (INTERFACE_TEMP.add(i)) {
          for (Class<?> ai : i.getInterfaces()) {
            INTERFACE_STACK.push(ai);
          }

          for (Method method : i.getMethods()) {
            if (method.isDefault())
              throw new IllegalHandleException("aspect interface must be full-abstract, but method " + method + " in " + i + " was implemented");

            aspectPoints.computeIfAbsent(method.getName(), e -> new HashSet<>()).add(FunctionType.from(method));
          }
        }
      }
    } else {
      aspectPoints.put(ANY, null);
    }

    ClassInfo<? extends T> classInfo = new ClassInfo<>(
        Modifier.PUBLIC,
        ensurePackage(baseClass.getName()) + "$" + FunctionType.typeNameHash(interfaces),
        asType(baseClass),
        inter.toArray(new ClassInfo[0])
    );
    FieldInfo<HashMap> methodIndex = classInfo.declareField(
        Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
        "$methodIndex$",
        HASH_MAP_TYPE,
        null
    );

    CodeBlock<Void> clinit = classInfo.getClinitBlock();
    ILocal<HashMap> caseIndex = clinit.local(HASH_MAP_TYPE);
    clinit.newInstance(
        asType(HashMap.class).getConstructor(),
        caseIndex
    );
    clinit.assign(null, caseIndex, methodIndex);

    HashMap<IMethod<?, ?>, Integer> callSuperCaseMap = new HashMap<>();

    // public <init>(*parameters*){
    //   super(*parameters*);
    // }
    for (Constructor<?> cstr : baseClass.getDeclaredConstructors()) {
      if ((cstr.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0) continue;
      if (Modifier.isFinal(cstr.getModifiers())) continue;

      ArrayList<IClass<?>> args = new ArrayList<>();
      for (Class<?> type : cstr.getParameterTypes()) {
        args.add(ClassInfo.asType(type));
      }
      IClass<?>[] argArr = args.toArray(new IClass[0]);
      IMethod<?, Void> constructor = classInfo.superClass().getConstructor(argArr);

      CodeBlock<Void> code = classInfo.declareConstructor(Modifier.PUBLIC, Parameter.trans(argArr));
      code.invokeSuper(code.getThis(), constructor, null, code.getParamList().toArray(new ILocal<?>[0]));
    }

    OVERRIDES.clear();
    FINALS.clear();

    //排除已被标识为final的方法，由于继续委托的类型不向上迭代，提前将final方法排除
    Class<?> curr = baseClass;
    while (curr != null) {
      for (Method method : curr.getDeclaredMethods()) {
        filterMethod(method);
      }

      curr = curr.getSuperclass();
    }

    OVERRIDES.clear();

    //仅处理新实现的接口以及切面接口
    ArrayList<Class<?>> lis = new ArrayList<>(Arrays.asList(interfaces));
    if (aspects != null) lis.addAll(Arrays.asList(aspects));

    for (Class<?> interf : lis) {
      ClassInfo<?> typeClass = asType(interf);
      for (Method method : interf.getDeclaredMethods()) {
        if (!filterMethod(method)) continue;

        String methodName = method.getName();
        ClassInfo<?> returnType = asType(method.getReturnType());

        MethodInfo<?, ?> superMethod = !Modifier.isAbstract(method.getModifiers()) || (interf.isInterface() && method.isDefault()) ? typeClass.getMethod(
            returnType,
            methodName,
            Arrays.stream(method.getParameterTypes()).map(ClassInfo::asType).toArray(ClassInfo[]::new)
        ) : null;

        if (!aspectPoints.containsKey(ANY) && !aspectPoints.getOrDefault(method.getName(), EMP_SET).contains(FunctionType.from(method))) {
          if (superMethod == null)
            throw new IllegalHandleException("method " + method + " in " + interf + " was abstract, but no aspects handle this action");

          continue;
        }

        if (superMethod != null) callSuperCaseMap.put(superMethod, callSuperCaseMap.size());

        String typeF = methodName + "$" + FunctionType.typeNameHash(method.getParameterTypes());
        FieldInfo<FunctionType> funType = classInfo.declareField(
            Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
            typeF,
            FUNCTION_TYPE_TYPE,
            null
        );

        // private static final FunctionType FUNCTION_TYPE$*name*;
        // static {
        //   ...
        //   FUNCTION_TYPE$*signature* = FunctionType.as(*paramTypes*);
        //   methodIndex.put(*signature*, *index*);
        //   ...
        // }
        genCinit(method, clinit, funType, methodIndex, callSuperCaseMap.get(superMethod));

        // @DynamicMethod
        // public *returnType* *name*(*parameters*){
        //   *[return]* this.invokeFunc(FUNCTION_TYPE$*signature* ,"*name*", parameters);
        // }
        invokeProxy(classInfo, method, methodName, returnType, funType);
      }
    }

    //switch super
    // public Object invokeSuper(String signature, Object... args);{
    //   Integer ind = methodIndex.get(signature);
    //   if(ind == null) return super.invokeSuper(signature, args)
    //   switch(ind){
    //     ...
    //     case *index*: super.*method*(args[0], args[1],...); break;
    //     ...
    //   }
    // }
    {
      CodeBlock<Object> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "invokeSuper",
          OBJECT_TYPE,
          new ClassInfo[]{
              ClassInfo.asType(NoSuchMethodException.class)
          },
          Parameter.trans(
              STRING_TYPE,
              OBJECT_TYPE.asArray()
          )
      );

      IMethod<T, Object> superCaller = (IMethod<T, Object>) classInfo.superClass().getMethod(code.owner().returnType(), code.owner().name(),
          code.owner().parameters().stream().map(Parameter::getType).toArray(IClass[]::new));

      Label end = code.label();
      code.assign(null, methodIndex, stack(HASH_MAP_TYPE));
      code.assign(code.getRealParam(0), stack(OBJECT_TYPE));
      code.loadConstant(stack(INT_TYPE), -1);
      code.invokeStatic(VALUE_OF, stack(INTEGER_CLASS_TYPE), stack(INT_TYPE));

      code.invoke(
          stack(HASH_MAP_TYPE),
          MAP_GET_DEF,
          stack(OBJECT_TYPE),
          stack(OBJECT_TYPE)
      );

      code.cast(stack(OBJECT_TYPE), stack(INTEGER_CLASS_TYPE));
      code.invoke(
          stack(INTEGER_CLASS_TYPE),
          INTEGER_CLASS_TYPE.getMethod(INT_TYPE, "intValue"),
          stack(INT_TYPE)
      );

      ISwitch<Integer> iSwitch = code.switchDef(stack(INT_TYPE), end);

      ILocal<Object[]> args = code.getRealParam(1);
      makeSwitch(classInfo, callSuperCaseMap, code, iSwitch, args);

      code.markLabel(end);
      code.assign(code.getThis(), stack(classInfo));

      code.invokeSuper(stack(classInfo), superCaller, stack(superCaller.returnType()), code.getParamList().toArray(new ILocal[0]));
      code.returnValue(stack(OBJECT_TYPE));
    }

    return classInfo;
  }

  /**
   * 创建动态实例类型的类型标识，这应当覆盖所有委托目标类的方法和实现的接口中的方法，若超类的某一成员方法不是抽象的，需保留对超类方法的入口，
   * 再重写本方法，对超类方法的入口需要有一定的标识以供生成基类数据池的引用函数时使用。
   * <p>对于给定的基类和接口列表，生成的动态实例基类的名称是唯一的（或者足够的离散以至于几乎不可能碰撞）。
   * <p>关于此方法实现需要完成的工作，请参阅{@link DynamicObject}中的描述
   *
   * @param baseClass  委托基类
   * @param interfaces 实现的接口列表
   * @return 完成了所有必要描述的类型标识
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected <T> ClassInfo<? extends T> makeClassInfo(Class<T> baseClass, Class<?>[] interfaces, Class<?>[] aspects) {
    if (baseClass.getAnnotation(DynamicType.class) != null)
      return makeClassInfoOnDynmaic(baseClass, interfaces, aspects);

    LinkedHashSet<ClassInfo<?>> inter = new LinkedHashSet<>();
    inter.add(asType(DynamicObject.class));
    inter.add(asType(SuperInvoker.class));

    for (Class<?> i : interfaces) {
      inter.add(asType(i));
    }

    INTERFACE_STACK.clear();
    INTERFACE_TEMP.clear();
    HashMap<String, HashSet<FunctionType>> aspectPoints = new HashMap<>();
    if (aspects != null) {
      for (Class<?> i : aspects) {
        inter.add(asType(i));
        INTERFACE_STACK.push(i);
      }

      while (!INTERFACE_STACK.isEmpty()) {
        Class<?> i = INTERFACE_STACK.pop();
        if (!i.isInterface())
          throw new IllegalHandleException("aspects must be interface, but find class: " + i);

        if (i.getAnnotation(AspectInterface.class) == null)
          throw new IllegalHandleException("aspect interfaces must has AspectInterface annotated, but " + i + " doesn't");

        if (INTERFACE_TEMP.add(i)) {
          for (Class<?> ai : i.getInterfaces()) {
            INTERFACE_STACK.push(ai);
          }

          for (Method method : i.getMethods()) {
            if (method.isDefault())
              throw new IllegalHandleException("aspect interface must be full-abstract, but method " + method + " in " + i + " was implemented");

            aspectPoints.computeIfAbsent(method.getName(), e -> new HashSet<>()).add(FunctionType.from(method));
          }
        }
      }
    } else {
      aspectPoints.put(ANY, null);
    }

    ClassInfo<? extends T> classInfo = new ClassInfo<>(
        Modifier.PUBLIC,
        getDynamicName(baseClass, interfaces),
        asType(baseClass),
        inter.toArray(new ClassInfo[0])
    );

    FieldInfo<DynamicClass> dyType = classInfo.declareField(
        Modifier.PRIVATE | Modifier.FINAL,
        "$dynamic_type$",
        DYNAMIC_CLASS_TYPE,
        null
    );
    FieldInfo<DataPool> dataPool = classInfo.declareField(
        Modifier.PRIVATE | Modifier.FINAL,
        "$datapool$",
        DATA_POOL_TYPE,
        null
    );
    FieldInfo<HashMap<String, Object>> varPool = (FieldInfo) classInfo.declareField(
        Modifier.PRIVATE | Modifier.FINAL,
        "$varValuePool$",
        HASH_MAP_TYPE,
        null
    );
    FieldInfo<DataPool.ReadOnlyPool> basePoolPointer = classInfo.declareField(
        Modifier.PRIVATE | Modifier.FINAL,
        "$superbasepointer$",
        READONLY_POOL_TYPE,
        null
    );
    FieldInfo<HashMap> methodIndex = classInfo.declareField(
        Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
        "$methodIndex$",
        HASH_MAP_TYPE,
        null
    );

    CodeBlock<Void> clinit = classInfo.getClinitBlock();
    ILocal<HashMap> caseIndex = clinit.local(HASH_MAP_TYPE);
    clinit.newInstance(
        asType(HashMap.class).getConstructor(),
        caseIndex
    );
    clinit.assign(null, caseIndex, methodIndex);

    HashMap<IMethod<?, ?>, Integer> callSuperCaseMap = new HashMap<>();

    // public <init>(DynamicClass $dyC$, DataPool $datP$, DataPool.ReadOnlyPool $basePool$, *parameters*){
    //   this.$dynamic_type$ = $dyC$;
    //   this.$datapool$ = $datP$;
    //   this.$varValuePool$ = new HashMap<>();
    //   super(*parameters*);
    //   this.$superbasepointer$ = $datapool$.getReader(this);
    //
    //   this.$datapool$.init(this, *parameters*);
    // }
    for (Constructor<?> cstr : baseClass.getDeclaredConstructors()) {
      if ((cstr.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0) continue;
      if (Modifier.isFinal(cstr.getModifiers())) continue;

      List<Parameter<?>> params = new ArrayList<>(Arrays.asList(Parameter.as(
          0, DynamicClass.class, "$dyc$",
          0, DataPool.class, "$datP$",
          0, DataPool.class, "$basePool$"
      )));
      List<Parameter<?>> superParams = Arrays.asList(Parameter.asParameter(cstr.getParameters()));
      params.addAll(superParams);

      IMethod<?, Void> constructor = classInfo.superClass().getConstructor(superParams.stream().map(Parameter::getType).toArray(IClass[]::new));

      CodeBlock<Void> code = classInfo.declareConstructor(Modifier.PUBLIC, params.toArray(new Parameter[0]));
      List<ILocal<?>> l = code.getParamList();
      ILocal<T> self = code.getThis();

      ILocal<DynamicClass> dyC = code.getParam(1);
      ILocal<DataPool> datP = code.getParam(2);
      code.assign(self, dyC, dyType);
      code.assign(self, datP, dataPool);

      code.assign(self, stack(classInfo));
      code.newInstance(HASH_MAP_TYPE.getConstructor(), stack(HASH_MAP_TYPE));
      code.assign(stack(classInfo), stack(HASH_MAP_TYPE), varPool);

      code.invokeSuper(self, constructor, null, l.subList(3, l.size()).toArray(LOCALS_EMP));

      code.assign(self, stack(classInfo));
      code.invoke(code.getParam(3), GET_READER, stack(READONLY_POOL_TYPE), self);
      code.assign(stack(classInfo), stack(READONLY_POOL_TYPE), basePoolPointer);

      ILocal<Object[]> argList = code.local(OBJECT_TYPE.asArray());
      code.loadConstant(stack(INT_TYPE), cstr.getParameterCount());
      code.invoke(null, GET_LIST, argList, stack(INT_TYPE));
      if (cstr.getParameterCount() > 0) {
        for (int i = 3; i < code.getParamList().size(); i++) {
          code.assign(argList, stack(OBJECT_TYPE.asArray()));
          code.loadConstant(stack(INT_TYPE), i - 3);
          code.arrayPut(stack(OBJECT_TYPE.asArray()), stack(INT_TYPE), code.getRealParam(i));
        }
      }
      code.invoke(datP, INIT, null, self, argList);

      code.invoke(null, RECYCLE_LIST, null, argList);
    }

    OVERRIDES.clear();
    FINALS.clear();
    INTERFACE_TEMP.clear();

    ArrayList<Class<?>> lis = new ArrayList<>(Arrays.asList(interfaces));
    if (aspects != null) lis.addAll(Arrays.asList(aspects));

    for (Class<?> ic : lis) {
      INTERFACE_STACK.push(ic);
      INTERFACE_TEMP.add(ic);
    }

    Class<?> curr = baseClass;
    while (curr != null || !INTERFACE_STACK.empty()) {
      if (curr != null) {
        for (Class<?> i : curr.getInterfaces()) {
          if (INTERFACE_TEMP.add(i)) INTERFACE_STACK.push(i);
        }
      } else curr = INTERFACE_STACK.pop();

      ClassInfo<?> typeClass = asType(curr);
      for (Method method : curr.getDeclaredMethods()) {
        if (!filterMethod(method)) continue;

        String methodName = method.getName();
        ClassInfo<?> returnType = asType(method.getReturnType());

        MethodInfo<?, ?> superMethod = !Modifier.isAbstract(method.getModifiers()) || (curr.isInterface() && method.isDefault()) ? typeClass.getMethod(
            returnType,
            methodName,
            Arrays.stream(method.getParameterTypes()).map(ClassInfo::asType).toArray(ClassInfo[]::new)
        ) : null;

        if (!aspectPoints.containsKey(ANY) && !aspectPoints.getOrDefault(method.getName(), EMP_SET).contains(FunctionType.from(method))) {
          if (superMethod == null)
            throw new IllegalHandleException("method " + method + " in " + curr + " was abstract, but no aspects handle this action");

          continue;
        }

        if (superMethod != null) callSuperCaseMap.put(superMethod, callSuperCaseMap.size());

        String typeF = methodName + "$" + FunctionType.typeNameHash(method.getParameterTypes());
        FieldInfo<FunctionType> funType = classInfo.declareField(
            Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
            typeF,
            FUNCTION_TYPE_TYPE,
            null
        );

        // private static final FunctionType FUNCTION_TYPE$*name*;
        // static {
        //   ...
        //   FUNCTION_TYPE$*signature* = FunctionType.as(*paramTypes*);
        //   methodIndex.put(*signature*, *index*);
        //   ...
        // }
        genCinit(method, clinit, funType, methodIndex, callSuperCaseMap.get(superMethod));

        // @DynamicMethod
        // public *returnType* *name*(*parameters*){
        //   *[return]* this.invokeFunc(FUNCTION_TYPE$*signature* ,"*name*", parameters);
        // }
        invokeProxy(classInfo, method, methodName, returnType, funType);
      }

      if (!curr.isInterface()) {
        curr = curr.getSuperclass();
      } else curr = null;
    }

    // public Object invokeSuper(String signature, Object... args);{
    //   switch(methodIndex.get(signature)){
    //     ...
    //     case *index*: super.*method*(args[0], args[1],...); break;
    //     ...
    //   }
    // }
    genCallSuper(classInfo, methodIndex, callSuperCaseMap);

    // public DataPool.ReadOnlyPool baseSuperPool(){
    //   return this.$superbasepointer$;
    // }
    {
      CodeBlock<DataPool.ReadOnlyPool> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "baseSuperPointer",
          READONLY_POOL_TYPE
      );
      code.assign(code.getThis(), basePoolPointer, stack(READONLY_POOL_TYPE));
      code.returnValue(stack(READONLY_POOL_TYPE));
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
      code.assign(code.getThis(), dyType, stack(DYNAMIC_CLASS_TYPE));
      code.returnValue(stack(DYNAMIC_CLASS_TYPE));
    }

    // public <T> T varValueGet(String name){
    //   return this.$varValuePool$.get(name);
    // }
    {
      CodeBlock<Object> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "varValueGet",
          OBJECT_TYPE,
          Parameter.trans(STRING_TYPE)
      );
      code.assign(code.getThis(), varPool, stack(HASH_MAP_TYPE));
      code.invoke(stack(HASH_MAP_TYPE), MAP_GET, stack(OBJECT_TYPE), code.getRealParam(0));
      code.returnValue(stack(OBJECT_TYPE));
    }

    // public <T> varValueSet(String name, Object value){
    //   return this.$varValuePool$.put(name, value);
    // }
    {
      CodeBlock<Void> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "varValueSet",
          VOID_TYPE,
          Parameter.trans(
              STRING_TYPE,
              OBJECT_TYPE
          )
      );
      code.assign(code.getThis(), varPool, stack(HASH_MAP_TYPE));
      code.invoke(stack(HASH_MAP_TYPE), MAP_PUT, null, code.getRealParam(0), code.getRealParam(1));
    }

    // public IVariable getVariable(String name){
    //   return this.$datapool$.getVariable(name);
    // }
    {
      CodeBlock<IVariable> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "getVariable",
          VAR_TYPE,
          Parameter.as(0, STRING_TYPE, "name")
      );
      code.assign(code.getThis(), dataPool, stack(DATA_POOL_TYPE));
      code.invoke(stack(DATA_POOL_TYPE), GET_VAR, stack(VAR_TYPE), code.getParam(1));
      code.returnValue(stack(VAR_TYPE));
    }

    // public <T> void setVariable(IVariable var){
    //   this.$datapool$.setVariable(var);
    // }
    {
      CodeBlock<Void> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "setVariable",
          VOID_TYPE,
          Parameter.as(0, VAR_TYPE, "var")
      );
      code.assign(code.getThis(), dataPool, stack(DATA_POOL_TYPE));
      code.invoke(stack(DATA_POOL_TYPE), SET_VAR, null, code.getParam(1));
      code.returnVoid();
    }

    // public IFunctionEntry getFunc(String name, FunctionType type){
    //   return this.$datapool$.select(name, type);
    // }
    {
      CodeBlock<IFunctionEntry> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "getFunc",
          FUNC_ENTRY_TYPE,
          Parameter.as(
              0, STRING_TYPE, "name",
              0, FUNCTION_TYPE_TYPE, "type"
          )
      );
      code.assign(code.getThis(), dataPool, stack(DATA_POOL_TYPE));

      code.invoke(stack(DATA_POOL_TYPE), SELECT, stack(FUNC_ENTRY_TYPE), code.getParam(1), code.getParam(2));
      code.returnValue(stack(FUNC_ENTRY_TYPE));
    }

    // public <R> void setFunc(String name, Function<Self, R> func, Class<?>... argTypes){
    //   this.$datapool$.set(name, func, argTypes);
    // }
    {
      CodeBlock<Void> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "setFunc",
          VOID_TYPE,
          Parameter.as(
              0, STRING_TYPE, "name",
              0, FUNCTION_TYPE, "func",
              0, CLASS_TYPE.asArray(), "argTypes"
          )
      );

      code.assign(code.getThis(), dataPool, stack(DATA_POOL_TYPE));
      code.invoke(stack(DATA_POOL_TYPE), SETFUNC, null, code.getParam(1), code.getParam(2), code.getParam(3));
    }

    // public <R> void setFunc(String name, Function<Self, R> func, Class<?>... argTypes){
    //   this.$datapool$.set(name, func, argTypes);
    // }
    {
      CodeBlock<Void> code = classInfo.declareMethod(
          Modifier.PUBLIC,
          "setFunc",
          VOID_TYPE,
          Parameter.as(
              0, STRING_TYPE, "name",
              0, SUPER_GET_FUNC_TYPE, "func",
              0, CLASS_TYPE.asArray(), "argTypes"
          )
      );

      ILocal<DataPool> pool = code.local(DATA_POOL_TYPE);
      code.assign(code.getThis(), dataPool, pool);

      code.invoke(pool, SETFUNC2, null, code.getParam(1), code.getParam(2), code.getParam(3));
    }

    AnnotationType<DynamicType> dycAnno = AnnotationType.asAnnotationType(DynamicType.class);
    dycAnno.annotateTo(classInfo, null);

    return classInfo;
  }

  @SuppressWarnings("unchecked")
  private <T> void genCallSuper(ClassInfo<? extends T> classInfo, FieldInfo<HashMap> methodIndex, HashMap<IMethod<?, ?>, Integer> callSuperCaseMap) {
    CodeBlock<Object> code = classInfo.declareMethod(
        Modifier.PUBLIC,
        "invokeSuper",
        OBJECT_TYPE,
        new ClassInfo[]{
            ClassInfo.asType(NoSuchMethodException.class)
        },
        Parameter.trans(
            STRING_TYPE,
            OBJECT_TYPE.asArray()
        )
    );

    Label end = code.label();
    code.assign(null, methodIndex, stack(HASH_MAP_TYPE));
    code.assign(code.getRealParam(0), stack(OBJECT_TYPE));
    code.loadConstant(stack(INT_TYPE), -1);
    code.invokeStatic(VALUE_OF, stack(INTEGER_CLASS_TYPE), stack(INT_TYPE));

    code.invoke(
        stack(HASH_MAP_TYPE),
        MAP_GET_DEF,
        stack(OBJECT_TYPE),
        stack(OBJECT_TYPE)
    );

    code.cast(stack(OBJECT_TYPE), stack(INTEGER_CLASS_TYPE));
    code.invoke(
        stack(INTEGER_CLASS_TYPE),
        INTEGER_CLASS_TYPE.getMethod(INT_TYPE, "intValue"),
        stack(INT_TYPE)
    );

    ISwitch<Integer> iSwitch = code.switchDef(stack(INT_TYPE), end);

    ILocal<Object[]> args = code.getRealParam(1);
    makeSwitch(classInfo, callSuperCaseMap, code, iSwitch, args);

    code.markLabel(end);

    ILocal<String> msg = code.local(STRING_TYPE);
    code.loadConstant(stack(STRING_TYPE), "no such method signature with ");
    code.operate(stack(STRING_TYPE), IOperate.OPCode.ADD, code.getRealParam(0), msg);
    code.newInstance(
        NOSUCH_METHOD.getConstructor(STRING_TYPE),
        stack(NOSUCH_METHOD),
        msg
    );
    code.thr(stack(NOSUCH_METHOD));
  }

  private static void genCinit(Method method, CodeBlock<Void> clinit, FieldInfo<FunctionType> funType, FieldInfo<HashMap> methodIndex, Integer callSuperCaseMap) {
    String signature = FunctionType.signature(method);
    clinit.loadConstant(stack(INT_TYPE), method.getParameterCount());
    clinit.newArray(
        CLASS_TYPE,
        stack(CLASS_TYPE.asArray()),
        stack(INT_TYPE)
    );

    Class<?>[] paramTypes = method.getParameterTypes();
    for (int i = 0; i < paramTypes.length; i++) {
      clinit.assign(stack(CLASS_TYPE.asArray()), stack(CLASS_TYPE.asArray()));
      clinit.loadConstant(stack(INT_TYPE), i);
      clinit.loadConstant(stack(CLASS_TYPE), paramTypes[i]);

      clinit.arrayPut(stack(CLASS_TYPE.asArray()), stack(INT_TYPE), stack(CLASS_TYPE));
    }

    clinit.invoke(
        null,
        TYPE_INST,
        stack(FUNCTION_TYPE_TYPE),
        stack(CLASS_TYPE.asArray())
    );

    clinit.assign(null, stack(FUNCTION_TYPE_TYPE), funType);

    clinit.assign(null, methodIndex, stack(HASH_MAP_TYPE));
    clinit.loadConstant(stack(STRING_TYPE), signature);

    clinit.loadConstant(stack(INT_TYPE), callSuperCaseMap);
    clinit.invoke(
        null,
        VALUE_OF,
        stack(INTEGER_CLASS_TYPE),
        stack(INT_TYPE)
    );

    clinit.invoke(
        stack(HASH_MAP_TYPE),
        MAP_PUT,
        null,
        stack(OBJECT_TYPE)
    );
  }

  @SuppressWarnings("unchecked")
  private static <T> void invokeProxy(ClassInfo<? extends T> classInfo, Method method, String methodName, ClassInfo<?> returnType, FieldInfo<FunctionType> funType) {
    CodeBlock<?> code = classInfo.declareMethod(
        Modifier.PUBLIC,
        methodName,
        returnType,
        Parameter.asParameter(method.getParameters())
    );
    AnnotationDef<DynamicMethod> anno = new AnnotationDef<>(
        ClassInfo.asType(DynamicMethod.class).asAnnotation(EMP_MAP),
        code.owner(),
        EMP_MAP
    );
    code.owner().addAnnotation(anno);

    code.loadConstant(stack(INT_TYPE), method.getParameterCount());
    code.invoke(null, GET_LIST, stack(OBJECT_TYPE.asArray()), stack(INT_TYPE));

    if (method.getParameterCount() > 0) {
      for (int i = 0; i < code.getParamList().size(); i++) {
        code.assign(stack(OBJECT_TYPE), stack(OBJECT_TYPE.asArray()));
        code.loadConstant(stack(INT_TYPE), i);
        code.arrayPut(stack(OBJECT_TYPE.asArray()), stack(INT_TYPE), code.getRealParam(i));
      }
    }

    ILocal<Object[]> argList = code.local(OBJECT_TYPE.asArray());
    code.assign(stack(OBJECT_TYPE.asArray()), argList);

    if (returnType != VOID_TYPE) {
      code.assign(code.getThis(), stack(classInfo));
      code.assign(null, funType, stack(FUNCTION_TYPE_TYPE));
      code.loadConstant(stack(STRING_TYPE), method.getName());
      code.assign(argList, stack(OBJECT_TYPE.asArray()));

      code.invoke(stack(classInfo), INVOKE, stack(OBJECT_TYPE), stack(OBJECT_TYPE));
      code.invoke(null, RECYCLE_LIST, null, argList);
      code.cast(stack(OBJECT_TYPE), stack(returnType));
      code.returnValue(stack((IClass) returnType));
    } else {
      code.assign(code.getThis(), stack(classInfo));
      code.assign(null, funType, stack(FUNCTION_TYPE_TYPE));
      code.loadConstant(stack(STRING_TYPE), method.getName());
      code.assign(argList, stack(OBJECT_TYPE.asArray()));

      code.invoke(stack(classInfo), INVOKE, null, stack(OBJECT_TYPE));
      code.invoke(null, RECYCLE_LIST, null, argList);
    }
  }

  private static boolean filterMethod(Method method) {
    //对于已经被声明为final的方法将被添加到排除列表
    if (Modifier.isFinal(method.getModifiers())) {
      DynamicMaker.FINALS.computeIfAbsent(method.getName(), e -> new HashSet<>()).add(FunctionType.from(method));
      return false;
    }

    // 如果方法是静态的，或者方法不对子类可见则不重写此方法
    if (Modifier.isStatic(method.getModifiers())) return false;
    if ((method.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0) return false;

    return !DynamicMaker.FINALS.computeIfAbsent(method.getName(), e -> new HashSet<>()).contains(FunctionType.from(method))
        && DynamicMaker.OVERRIDES.computeIfAbsent(method.getName(), e -> new HashSet<>()).add(FunctionType.from(method));
  }

  protected void makeSwitch(IClass<?> owner, HashMap<IMethod<?, ?>, Integer> callSuperCaseMap, CodeBlock<Object> code, ISwitch<Integer> iSwitch, ILocal<Object[]> args) {
    for (Map.Entry<IMethod<?, ?>, Integer> entry : callSuperCaseMap.entrySet()) {
      Label l = code.label();
      code.markLabel(l);

      iSwitch.addCase(entry.getValue(), l);

      IMethod<?, ?> superMethod = entry.getKey();
      if (Modifier.isInterface(superMethod.owner().modifiers())) {
        IClass<?> c = code.owner().owner().superClass();
        boolean found = false;
        t:
        while (c != null && c != OBJECT_TYPE) {
          for (IClass<?> interf : c.interfaces()) {
            if (interf == superMethod.owner()) {
              found = true;
              c = interf;
              break t;
            }
          }

          c = c.superClass();
        }

        if (found) {
          c.getMethod(
              superMethod.returnType(),
              superMethod.name(),
              superMethod.parameters().stream().map(Parameter::getType).toArray(IClass[]::new)
          );
        }
      }

      code.assign(code.getThis(), stack(owner));
      for (int in = 0; in < superMethod.parameters().size(); in++) {
        code.assign(args, stack(OBJECT_TYPE.asArray()));
        code.loadConstant(stack(INT_TYPE), in);
        code.arrayGet(stack(OBJECT_TYPE.asArray()), stack(INT_TYPE), stack(OBJECT_TYPE));
        code.cast(stack(OBJECT_TYPE), stack(superMethod.parameters().get(in).getType()));
      }

      if (superMethod.returnType() != VOID_TYPE) {
        code.invokeSuper(stack(owner), superMethod, stack(OBJECT_TYPE), stack(OBJECT_TYPE));
        code.returnValue(stack(OBJECT_TYPE));
      } else {
        code.invokeSuper(stack(owner), superMethod, null, stack(OBJECT_TYPE));
        code.loadConstant(stack(OBJECT_TYPE), null);
        code.returnValue(stack(OBJECT_TYPE));
      }
    }
  }

  /**
   * 对委托基类的映射处理，通常来说直接返回类型本身，对此方法的覆写在需要进行包私有访问时才需要
   * <br>具体来说，该映射在委托包私有方法时需要创建一串将包私有方法访问修饰符提升的类层次结构，分别去访问基类的各个超类所在包的保护域
   * <br><strong>默认的实现是非包私有委托的</strong>
   *
   * @param baseClass 进行动态委托的基类
   * @return 对基类进行映射处理的类，默认情况下就是类本身
   */
  protected <T> Class<? extends T> handleBaseClass(Class<T> baseClass) {
    return baseClass;
  }

  /**
   * 生成委托自基类并实现了给出的接口列表的类型，而类的行为描述请参考{@link DynamicMaker#makeClassInfo(Class, Class[], Class[])}，类型描述会在此方法产出。
   * <p>该方法需要做的事通常是将makeClassInfo获得的类型标识进行生成并加载其表示的java类型
   *
   * @param baseClass  委托基类
   * @param interfaces 实现的接口列表
   * @return 对全方法进行动态委托的类型
   */
  protected abstract <T> Class<? extends T> generateClass(Class<T> baseClass, Class<?>[] interfaces, Class<?>[] aspects);

  /**
   * 动态委托类型标识，由此工厂生成的动态委托类型都会具有此注解标识
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface DynamicType {
  }

  /**
   * 动态方法标识，所有被动态委托的方法会携带此注解，用于标识动态类行为的基础入口点
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface DynamicMethod {
  }

  public interface SuperInvoker {
    Object invokeSuper(String signature, Object... args) throws NoSuchMethodException;
  }
}
