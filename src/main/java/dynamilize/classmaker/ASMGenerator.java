package dynamilize.classmaker;

import dynamilize.IllegalHandleException;
import dynamilize.classmaker.code.*;
import dynamilize.classmaker.code.annotation.AnnotatedElement;
import dynamilize.classmaker.code.annotation.IAnnotation;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;

/**基于ASM字节码操作框架实现的默认类型生成器，*/
public class ASMGenerator extends AbstractClassGenerator implements Opcodes{
  protected static final Map<Character, Map<Character, Integer>> CASTTABLE = new HashMap<>();

  static {
    HashMap<Character, Integer> map;
    map = new HashMap<>();
    CASTTABLE.put('I', map);
    map.put('I', I2L);
    map.put('F', I2F);
    map.put('D', I2D);
    map.put('B', I2B);
    map.put('C', I2C);
    map.put('S', I2S);
    map = new HashMap<>();
    CASTTABLE.put('L', map);
    map.put('I', L2I);
    map.put('F', L2F);
    map.put('D', L2D);
    map = new HashMap<>();
    CASTTABLE.put('F', map);
    map.put('I', F2I);
    map.put('L', F2L);
    map.put('D', F2D);
    map = new HashMap<>();
    CASTTABLE.put('D', map);
    map.put('I', D2I);
    map.put('L', D2L);
    map.put('F', D2F);
  }

  protected final ByteClassLoader classLoader;
  protected final int codeVersion;

  protected ClassWriter writer;

  protected IClass<?> currGenerating;

  protected MethodVisitor methodVisitor;
  protected FieldVisitor fieldVisitor;

  protected final Map<String, IField<?>> fieldMap = new HashMap<>();
  protected final Map<String, Object> staticInitial = new HashMap<>();

  protected final Map<String, Integer> localIndex = new HashMap<>();
  protected final Map<dynamilize.classmaker.code.Label, Label> labelMap = new HashMap<>();

  protected final Set<dynamilize.classmaker.code.Label> frameLabels = new HashSet<>();

  public ASMGenerator(ByteClassLoader classLoader, int codeVersion){
    this.classLoader = classLoader;
    this.codeVersion = codeVersion;
  }

  protected void initial(){
    writer = null;

    methodVisitor = null;
    fieldVisitor = null;
  }

  @Override
  public byte[] genByteCode(ClassInfo<?> classInfo){
    initial();
    writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

    visitClass(classInfo);

    return writer.toByteArray();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected  <T> Class<T> generateClass(ClassInfo<T> classInfo){
    classLoader.declareClass(classInfo.name(), genByteCode(classInfo));
    currGenerating = classInfo;

    try{
      return (Class<T>) classLoader.loadClass(classInfo.name(), false);
    }catch(ClassNotFoundException e){
      throw new IllegalHandleException(e);
    }
  }

  @Override
  public void visitClass(IClass<?> clazz){
    fieldMap.clear();
    staticInitial.clear();

    writer.visit(
        codeVersion,
        clazz.modifiers(),
        clazz.internalName(),
        null,
        clazz.superClass().internalName(),
        clazz.interfaces().stream().map(IClass::internalName).toArray(String[]::new)
    );

    visitAnnotation(clazz);

    super.visitClass(clazz);

    writer.visitEnd();
  }

  @Override
  public void visitLocal(ILocal<?> local){
    super.visitLocal(local);
    localIndex.put(local.name(), localIndex.size());
  }

  @Override
  public void visitMethod(IMethod<?, ?> method){
    methodVisitor = writer.visitMethod(
        method.modifiers(),
        method.name(),
        method.typeDescription(),
        null,
        null
    );
    visitAnnotation(method);

    localMap = new LinkedHashMap<>();

    labelMap.clear();
    frameLabels.clear();
    localIndex.clear();
    if(!Modifier.isAbstract(method.modifiers())){
      Label firstLine = new Label();
      methodVisitor.visitLabel(firstLine);

      for(ILocal<?> parameter: method.block().getParamAll()){
        localMap.put(parameter.name(), parameter);
        localIndex.put(parameter.name(), localIndex.size());
      }

      //仅仅在处理<clinit>方法时会进行，初始化静态变量
      if(method.name().equals("<clinit>")){
        for(Map.Entry<String, Object> entry: staticInitial.entrySet()){
          visitConstant(entry.getValue());

          IField<?> field = fieldMap.get(entry.getKey());
          methodVisitor.visitFieldInsn(
              PUTSTATIC,
              field.owner().internalName(),
              field.name(),
              field.type().realName());
        }
      }

      super.visitMethod(method);

      Label endLine = new Label();
      methodVisitor.visitLabel(endLine);

      int localCount = 0;
      for(Map.Entry<String, ILocal<?>> entry: localMap.entrySet()){
        methodVisitor.visitLocalVariable(
            entry.getKey(),
            entry.getValue().type().realName(),
            null,
            firstLine,
            endLine,
            localCount
        );

        IClass<?> type = entry.getValue().type();
        localCount += type.equals(ClassInfo.LONG_TYPE) || type.equals(ClassInfo.DOUBLE_TYPE)? 2: 1;
      }

      methodVisitor.visitMaxs(0, 0);//已设置自动计算模式，参数无意义
    }

    for(Parameter<?> parameter: method.parameters()){
      visitAnnotation(parameter);
    }

    methodVisitor.visitEnd();
  }

  @Override
  public void visitCodeBlock(ICodeBlock<?> block){
    for(dynamilize.classmaker.code.Label label: block.labelList()){
      labelMap.put(label, new Label());
    }

    if(block.owner().name().equals("<init>") && (!(block.codes().get(0) instanceof IInvoke<?>)
    || !((IInvoke<?>)block.codes().get(0)).method().name().equals("<init>"))){
      block.owner().owner().superClass().getConstructor();

      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          block.owner().owner().superClass().internalName(),
          "<init>",
          "()V",
          false
      );
    }

    super.visitCodeBlock(block);

    Element end = block.codes().isEmpty()? null: block.codes().get(block.codes().size() - 1);
    if(end != null && end.kind() == ElementKind.RETURN){
      IReturn<?> ret = (IReturn<?>) end;
      if((ret.returnValue() == null && block.owner().returnType().equals(ClassInfo.VOID_TYPE) )
      || (block.owner().returnType().isAssignableFrom(ret.returnValue().type()))) return;
    }

    if(block.owner().returnType() == ClassInfo.VOID_TYPE){
      methodVisitor.visitInsn(RETURN);
    }
    else throw new IllegalHandleException("method return a non-null value, but the method is not returning correctly");
  }

  @Override
  public void visitField(IField<?> field){
    fieldVisitor = writer.visitField(
        field.modifiers(),
        field.name(),
        field.type().realName(),
        null,
        field.initial() instanceof Number || field.initial() instanceof String? field.initial(): null
    );

    fieldMap.put(field.name(), field);
    if(field.initial() instanceof Array || field.initial() instanceof Enum<?>)
      staticInitial.put(field.name(), field.initial());

    visitAnnotation(field);
    fieldVisitor.visitEnd();
  }

  @Override
  public void visitInvoke(IInvoke<?> invoke){
    int invokeType = Modifier.isStatic(invoke.method().modifiers())? INVOKESTATIC:
        Modifier.isInterface(invoke.method().owner().modifiers())? INVOKEINTERFACE:
        invoke.method().name().equals("<init>") || invoke.callSuper()? INVOKESPECIAL:
        INVOKEVIRTUAL;
    
    if(!Modifier.isStatic(invoke.method().modifiers())){
      methodVisitor.visitVarInsn(ALOAD, localIndex.get(invoke.target().name()));
    }

    for(ILocal<?> arg: invoke.args()){
      methodVisitor.visitVarInsn(
          getLoadType(arg.type()),
          localIndex.get(arg.name())
      );
    }

    methodVisitor.visitMethodInsn(
        invokeType,
        invoke.method().owner().internalName(),
        invoke.method().name(),
        invoke.method().typeDescription(),
        Modifier.isInterface(invoke.method().owner().modifiers())
    );

    IClass<?> type = invoke.method().returnType();

    if(invoke.returnTo() == null){
      if(invoke.method().returnType() != ClassInfo.VOID_TYPE){
        methodVisitor.visitInsn(POP);
      }
    }
    else{
      if(!type.isPrimitive() && invoke.returnTo().type().isPrimitive()){
        warpAssign(invoke.returnTo().type());
      }

      methodVisitor.visitVarInsn(
          getStoreType(invoke.returnTo().type()),
          localIndex.get(invoke.returnTo().name())
      );
    }
  }

  protected void warpAssign(IClass<?> returnType){
    if(!returnType.isPrimitive())
      throw new IllegalHandleException("cannot warp non-primitive");

    if(returnType == ClassInfo.INT_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false
      );
    }
    if(returnType == ClassInfo.FLOAT_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Float");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false
      );
    }
    if(returnType == ClassInfo.BYTE_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Byte");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false
      );
    }
    if(returnType == ClassInfo.SHORT_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Short");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false
      );
    }
    if(returnType == ClassInfo.LONG_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false
      );
    }
    if(returnType == ClassInfo.DOUBLE_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false
      );
    }
    if(returnType == ClassInfo.BOOLEAN_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false
      );
    }
    if(returnType == ClassInfo.CHAR_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Character");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false
      );
    }
  }

  @Override
  public void visitGetField(IGetField<?, ?> getField){
    if(!Modifier.isStatic(getField.source().modifiers())){
      methodVisitor.visitVarInsn(ALOAD, localIndex.get(getField.inst().name()));
    }

    methodVisitor.visitFieldInsn(
        Modifier.isStatic(getField.source().modifiers())? GETSTATIC: GETFIELD,
        getField.source().owner().internalName(),
        getField.source().name(),
        getField.source().type().realName()
    );

    methodVisitor.visitVarInsn(
        getStoreType(getField.source().type()),
        localIndex.get(getField.target().name())
    );
  }

  @Override
  public void visitPutField(IPutField<?, ?> putField){
    if(!Modifier.isStatic(putField.target().modifiers())){
      methodVisitor.visitVarInsn(ALOAD, localIndex.get(putField.inst().name()));
    }

    methodVisitor.visitVarInsn(
        getLoadType(putField.source().type()),
        localIndex.get(putField.source().name())
    );

    methodVisitor.visitFieldInsn(
        Modifier.isStatic(putField.target().modifiers())? PUTSTATIC: PUTFIELD,
        putField.target().owner().internalName(),
        putField.target().name(),
        putField.target().type().realName()
    );
  }

  @Override
  public void visitLocalSet(ILocalAssign<?, ?> localSet){
    methodVisitor.visitVarInsn(
        getLoadType(localSet.source().type()),
        localIndex.get(localSet.source().name())
    );

    methodVisitor.visitVarInsn(
        getStoreType(localSet.target().type()),
        localIndex.get(localSet.target().name())
    );
  }

  @Override
  public void visitOperate(IOperate<?> operate){
    methodVisitor.visitVarInsn(
        getLoadType(operate.leftOpNumber().type()),
        localIndex.get(operate.leftOpNumber().name())
    );

    methodVisitor.visitVarInsn(
        getLoadType(operate.rightOpNumber().type()),
        localIndex.get(operate.rightOpNumber().name())
    );

    IClass<?> type = operate.leftOpNumber().type();
    int ilfd = getILFD(type);

    int opc = -1;
    switch(operate.opCode()){
      case ADD:
        opc = selectILFD(ilfd, IADD, LADD, FADD, DADD);
        break;
      case SUBSTRUCTION:
        opc = selectILFD(ilfd, ISUB, LSUB, FSUB, DSUB);
        break;
      case MULTI:
        opc = selectILFD(ilfd, IMUL, LMUL, FMUL, DMUL);
        break;
      case DIVISION:
        opc = selectILFD(ilfd, IDIV, LDIV, FDIV, DDIV);
        break;
      case REMAINING:
        opc = selectILFD(ilfd, IREM, LREM, FREM, DREM);
        break;
      case LEFTMOVE:
        opc = selectIL(ilfd, ISHL, LSHL);
        break;
      case RIGHTMOVE:
        opc = selectIL(ilfd, ISHR, LSHR);
        break;
      case UNSIGNMOVE:
        opc = selectIL(ilfd, IUSHR, LUSHR);
        break;
      case BITSAME:
        opc = selectIL(ilfd, IAND, LAND);
        break;
      case BITOR:
        opc = selectIL(ilfd, IOR, LOR);
        break;
      case BITXOR:
        opc = selectIL(ilfd, IXOR, LXOR);
    }

    methodVisitor.visitInsn(opc);
  }

  private int selectIL(int ilfd, int ishl, int lshl){
    int opc;
    switch(ilfd){
      case 1: opc = ishl; break;
      case 2: opc = lshl; break;
      default: throw new IllegalStateException("Unexpected value: " + ilfd);
    }
    return opc;
  }

  private int selectILFD(int ilfd, int iadd, int ladd, int fadd, int dadd){
    int opc;
    switch(ilfd){
      case 1: opc = iadd; break;
      case 2: opc = ladd; break;
      case 3: opc = fadd; break;
      case 4: opc = dadd; break;
      default: throw new IllegalStateException("Unexpected value: " + ilfd);
    }
    return opc;
  }

  private int getILFD(IClass<?> type){
    return type == ClassInfo.INT_TYPE || type == ClassInfo.BYTE_TYPE
            || type == ClassInfo.SHORT_TYPE || type == ClassInfo.BOOLEAN_TYPE
            || type == ClassInfo.CHAR_TYPE? 1:
        type == ClassInfo.LONG_TYPE? 2:
        type == ClassInfo.FLOAT_TYPE? 3:
        type == ClassInfo.DOUBLE_TYPE? 4: -1;
  }

  @Override
  public void visitCast(ICast cast){
    int opc = CASTTABLE.get(getTypeChar(cast.source().type(), true)).get(getTypeChar(cast.target().type(), false));
    methodVisitor.visitVarInsn(
        getLoadType(cast.source().type()),
        localIndex.get(cast.source().name())
    );

    methodVisitor.visitInsn(opc);

    methodVisitor.visitVarInsn(
        getStoreType(cast.target().type()),
        localIndex.get(cast.target().name())
    );
  }

  @Override
  public void visitGoto(IGoto iGoto){
    methodVisitor.visitJumpInsn(GOTO, labelMap.get(iGoto.target()));
  }
  
  @Override
  public void visitLabel(IMarkLabel label){
    methodVisitor.visitLabel(labelMap.get(label.label()));
  }

  @Override
  public void visitCompare(ICompare<?> compare){
    int opc = -1;
    switch(compare.comparison()){
      case EQUAL: opc = IF_ACMPEQ; break;
      case UNEQUAL: opc = IF_ACMPNE; break;
      case LESS: opc = IF_ICMPLT; break;
      case LESSOREQUAL: opc = IF_ICMPLE; break;
      case MORE: opc = IF_ICMPGT; break;
      case MOREOREQUAL: opc = IF_ICMPGE; break;
    }

    methodVisitor.visitVarInsn(
        getLoadType(compare.leftNumber().type()),
        localIndex.get(compare.leftNumber().name())
    );

    methodVisitor.visitVarInsn(
        getLoadType(compare.rightNumber().type()),
        localIndex.get(compare.rightNumber().name())
    );

    methodVisitor.visitJumpInsn(opc, labelMap.get(compare.ifJump()));
  }

  @Override
  public void visitCondition(ICondition condition){
    int opc = -1;
    switch(condition.condCode()){
      case EQUAL: opc = IFEQ; break;
      case UNEQUAL: opc = IFNE; break;
      case LESS: opc = IFLT; break;
      case LESSOREQUAL: opc = IFLE; break;
      case MORE: opc = IFGT; break;
      case MOREOREQUAL: opc = IFGE; break;
    }

    methodVisitor.visitVarInsn(
        getLoadType(condition.condition().type()),
        localIndex.get(condition.condition().name())
    );

    methodVisitor.visitJumpInsn(opc, labelMap.get(condition.ifJump()));
  }

  @Override
  public void visitArrayGet(IArrayGet<?> arrayGet){
    methodVisitor.visitVarInsn(
        ALOAD,
        localIndex.get(arrayGet.array().name())
    );

    IClass<?> componentType = arrayGet.array().type().componentType();

    int loadType;
    if(componentType == ClassInfo.INT_TYPE){loadType = IALOAD;}
    else if(componentType == ClassInfo.FLOAT_TYPE){loadType = FALOAD;}
    else if(componentType == ClassInfo.LONG_TYPE){loadType = LALOAD;}
    else if(componentType == ClassInfo.DOUBLE_TYPE){loadType = DALOAD;}
    else if(componentType == ClassInfo.BYTE_TYPE){loadType = BALOAD;}
    else if(componentType == ClassInfo.SHORT_TYPE){loadType = SALOAD;}
    else if(componentType == ClassInfo.BOOLEAN_TYPE){loadType = BALOAD;}
    else if(componentType == ClassInfo.CHAR_TYPE){loadType = CALOAD;}
    else {loadType = AALOAD;}

    methodVisitor.visitVarInsn(
        ILOAD,
        localIndex.get(arrayGet.index().name())
    );

    methodVisitor.visitInsn(loadType);

    methodVisitor.visitVarInsn(
        getStoreType(componentType),
        localIndex.get(arrayGet.getTo().name())
    );
  }

  @Override
  public void visitArrayPut(IArrayPut<?> arrayPut){
    methodVisitor.visitVarInsn(
        ALOAD,
        localIndex.get(arrayPut.array().name())
    );

    methodVisitor.visitVarInsn(
        ILOAD,
        localIndex.get(arrayPut.index().name())
    );

    IClass<?> componentType = arrayPut.array().type().componentType();

    int storeType;
    if(componentType == ClassInfo.INT_TYPE){storeType = IASTORE;}
    else if(componentType == ClassInfo.FLOAT_TYPE){storeType = FASTORE;}
    else if(componentType == ClassInfo.LONG_TYPE){storeType = LASTORE;}
    else if(componentType == ClassInfo.DOUBLE_TYPE){storeType = DASTORE;}
    else if(componentType == ClassInfo.BYTE_TYPE){storeType = BASTORE;}
    else if(componentType == ClassInfo.SHORT_TYPE){storeType = SASTORE;}
    else if(componentType == ClassInfo.BOOLEAN_TYPE){storeType = BASTORE;}
    else if(componentType == ClassInfo.CHAR_TYPE){storeType = CASTORE;}
    else {storeType = AASTORE;}

    methodVisitor.visitVarInsn(
        getLoadType(componentType),
        localIndex.get(arrayPut.value().name())
    );

    methodVisitor.visitInsn(storeType);
  }

  @Override
  public void visitReturn(IReturn<?> iReturn){
    if(iReturn.returnValue() == null){
      methodVisitor.visitInsn(RETURN);
    }
    else{
      int opc = -1;
      switch(getTypeChar(iReturn.returnValue().type(), true)){
        case 'I': opc = IRETURN; break;
        case 'L': opc = LRETURN; break;
        case 'F': opc = FRETURN; break;
        case 'D': opc = DRETURN; break;
        case 'A': opc = ARETURN; break;
      };

      methodVisitor.visitVarInsn(
          getLoadType(iReturn.returnValue().type()),
          localIndex.get(iReturn.returnValue().name())
      );

      if(opc == ARETURN){
        methodVisitor.visitTypeInsn(
            CHECKCAST,
            iReturn.returnValue().type().internalName()
        );
      }

      methodVisitor.visitInsn(opc);
    }
  }

  @Override
  public void visitInstanceOf(IInstanceOf instanceOf){
    methodVisitor.visitVarInsn(
        getLoadType(instanceOf.target().type()),
        localIndex.get(instanceOf.target().name())
    );

    methodVisitor.visitTypeInsn(
        INSTANCEOF,
        instanceOf.type().internalName()
    );

    methodVisitor.visitVarInsn(
        getStoreType(instanceOf.result().type()),
        localIndex.get(instanceOf.result().name())
    );
  }

  @Override
  public void visitNewInstance(INewInstance<?> newInstance){
    methodVisitor.visitTypeInsn(NEW, newInstance.type().internalName());
    methodVisitor.visitInsn(DUP);

    for(ILocal<?> local: newInstance.params()){
      methodVisitor.visitVarInsn(
          getLoadType(local.type()),
          localIndex.get(local.name())
      );
    }

    methodVisitor.visitMethodInsn(
        INVOKESPECIAL,
        newInstance.type().internalName(),
        newInstance.constructor().name(),
        newInstance.constructor().typeDescription(),
        false
    );
  }

  @Override
  public void visitOddOperate(IOddOperate<?> operate){
    methodVisitor.visitVarInsn(
        getLoadType(operate.operateNumber().type()),
        localIndex.get(operate.operateNumber().name())
    );

    IClass<?> type = operate.operateNumber().type();
    int ilfd = getILFD(type);

    int opc = -1;
    switch(operate.opCode()){
      case NEGATIVE:
        opc = selectILFD(ilfd, INEG, LNEG, FNEG, DNEG);
        break;
      case BITNOR:
        opc = selectIL(ilfd, IXOR, LXOR);
        break;
    }

    if(opc == IXOR){
      methodVisitor.visitInsn(ICONST_M1);
      methodVisitor.visitInsn(IXOR);
    }
    else if(opc == LXOR){
      methodVisitor.visitLdcInsn(-1L);
      methodVisitor.visitInsn(LXOR);
    }
    else{
      methodVisitor.visitInsn(opc);
    }
  }

  @Override
  public void visitConstant(ILoadConstant<?> loadConstant){
    visitConstant(loadConstant.constant());

    methodVisitor.visitVarInsn(
        getStoreType(loadConstant.constTo().type()),
        localIndex.get(loadConstant.constTo().name())
    );
  }

  @Override
  public void visitNewArray(INewArray<?> newArray){
    int dimension = newArray.arrayLength().size();
    if(dimension == 1){
      ILocal<?> len = newArray.arrayLength().get(0);
      methodVisitor.visitVarInsn(
          getLoadType(len.type()),
          localIndex.get(len.name())
      );

      methodVisitor.visitTypeInsn(
          newArray.arrayEleType().isPrimitive()? NEWARRAY: ANEWARRAY,
          newArray.arrayEleType().internalName()
      );
    }
    else if(dimension > 1){
      IClass<?> arrType = newArray.arrayEleType();
      for(int i = 0; i < dimension; i++){
        arrType = arrType.asArray();

        ILocal<?> len = newArray.arrayLength().get(i);
        methodVisitor.visitVarInsn(
            getLoadType(len.type()),
            localIndex.get(len.name())
        );
      }

      methodVisitor.visitMultiANewArrayInsn(arrType.realName(), dimension);
    }
    else throw new IllegalHandleException("illegal array dimension " + dimension);

    methodVisitor.visitVarInsn(
        ASTORE,
        localIndex.get(newArray.resultTo().name())
    );
  }

  protected void visitAnnotation(AnnotatedElement element){
    AnnotationVisitor annoVisitor;

    for(IAnnotation<?> annotation: element.getAnnotations()){
      IClass<?> annoType = annotation.annotationType().typeClass();
      Retention retention = annoType.getAnnotation(Retention.class);
      if(retention != null && retention.value() == RetentionPolicy.SOURCE) continue;

      boolean ret = retention != null && retention.value() == RetentionPolicy.RUNTIME;

      annoVisitor = element.isType(ElementType.TYPE) || element.isType(ElementType.ANNOTATION_TYPE)? writer.visitAnnotation(
          annoType.realName(), ret
      ): element.isType(ElementType.FIELD)? fieldVisitor.visitAnnotation(
          annoType.realName(), ret
      ): element.isType(ElementType.METHOD) || element.isType(ElementType.CONSTRUCTOR)? methodVisitor.visitAnnotation(
          annoType.realName(), ret
      ): element.isType(ElementType.PARAMETER) && element instanceof Parameter<?>? methodVisitor.visitParameterAnnotation(
          localIndex.get(((Parameter<?>)element).name()),
          annoType.realName(),
          ret
      ): null;

      if(annoVisitor == null)
        throw new IllegalHandleException("unknown annotation retention type");

      for(Map.Entry<String, Object> entry: annotation.pairs().entrySet()){
        Object value = entry.getValue();
        if(value.getClass().isEnum()){
          annoVisitor.visitEnum(
              entry.getKey(),
              ClassInfo.asType(value.getClass()).realName(),
              ((Enum<?>) value).name()
          );
        }
        else annoVisitor.visit(entry.getKey(), value);
      }

      annoVisitor.visitEnd();
    }
  }

  protected int getStoreType(IClass<?> type){
    return typeSelect(type, ASTORE, ISTORE, LSTORE, DSTORE, FSTORE);
  }

  protected int getLoadType(IClass<?> type){
    return typeSelect(type, ALOAD, ILOAD, LLOAD, DLOAD, FLOAD);
  }

  private static int typeSelect(IClass<?> type, int aload, int iload, int lload, int dload, int fload){
    return !type.isPrimitive()? aload :
        type == ClassInfo.INT_TYPE || type == ClassInfo.BYTE_TYPE
            || type == ClassInfo.SHORT_TYPE || type == ClassInfo.BOOLEAN_TYPE
            || type == ClassInfo.CHAR_TYPE? iload :
        type == ClassInfo.LONG_TYPE? lload :
        type == ClassInfo.DOUBLE_TYPE? dload :
        type == ClassInfo.FLOAT_TYPE? fload : -1;
  }

  protected void visitConstant(Object value){
    if(value.getClass().isArray()){
      Class<?> componentType = value.getClass().getComponentType();

      if(componentType.isPrimitive() || componentType == String.class || componentType.isEnum() || componentType.isArray()){
        int opc;
        int storeType;
        if(componentType == int.class){opc = T_INT; storeType = IASTORE;}
        else if(componentType == float.class){opc = T_FLOAT; storeType = FASTORE;}
        else if(componentType == long.class){opc = T_LONG; storeType = LASTORE;}
        else if(componentType == double.class){opc = T_DOUBLE; storeType = DASTORE;}
        else if(componentType == byte.class){opc = T_BYTE; storeType = BASTORE;}
        else if(componentType == short.class){opc = T_SHORT; storeType = SASTORE;}
        else if(componentType == boolean.class){opc = T_BOOLEAN; storeType = BASTORE;}
        else if(componentType == char.class){opc = T_CHAR; storeType = CASTORE;}
        else {opc = 0; storeType = AASTORE;}

        int len = Array.getLength(value);
        visitConstant(len);

        if(componentType.isPrimitive()){
          methodVisitor.visitIntInsn(NEWARRAY, opc);
        }
        else methodVisitor.visitTypeInsn(ANEWARRAY, ClassInfo.asType(value.getClass()).internalName());

        for(int i = 0; i < len; i++){
          methodVisitor.visitInsn(DUP);
          visitConstant(i);
          visitConstant(Array.get(value, i));

          methodVisitor.visitInsn(storeType);
        }
      }
    }
    else if(value instanceof String || value instanceof Float
    || value instanceof Long || value instanceof Double){
      if(value instanceof Long){
        long l = (long) value;
        if(l == 0){methodVisitor.visitInsn(LCONST_0); return;}
        else if(l == 1){methodVisitor.visitInsn(LCONST_1); return;}
      }
      else if(value instanceof Float){
        float f = (float) value;
        if(f == 0){methodVisitor.visitInsn(FCONST_0); return;}
        else if(f == 1){methodVisitor.visitInsn(FCONST_1); return;}
        else if(f == 2){methodVisitor.visitInsn(FCONST_2); return;}
      }
      else if(value instanceof Double){
        double d = (double) value;
        if(d == 0){methodVisitor.visitInsn(DCONST_0); return;}
        else if(d == 1){methodVisitor.visitInsn(DCONST_1); return;}
      }

      methodVisitor.visitLdcInsn(value);
    }
    else if(value instanceof Integer || value instanceof Byte
    || value instanceof Short || value instanceof Character){
      int v = (int) value;
      switch(v){
        case 0: methodVisitor.visitInsn(ICONST_0); return;
        case 1: methodVisitor.visitInsn(ICONST_1); return;
        case 2: methodVisitor.visitInsn(ICONST_2); return;
        case 3: methodVisitor.visitInsn(ICONST_3); return;
        case 4: methodVisitor.visitInsn(ICONST_4); return;
        case 5: methodVisitor.visitInsn(ICONST_5); return;
      }

      if(v <= Byte.MAX_VALUE && v >= Byte.MIN_VALUE){
        methodVisitor.visitIntInsn(BIPUSH, v);
      }
      else if(v <= Short.MAX_VALUE && v >= Short.MIN_VALUE){
        methodVisitor.visitIntInsn(SIPUSH, v);
      }
      else{
        methodVisitor.visitLdcInsn(value);
      }
    }
    else if(value instanceof Enum<?>){
      IClass<?> type = ClassInfo.asType(value.getClass());
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          type.internalName(),
          ((Enum<?>)value).name(),
          type.realName()
      );
    }
  }

  protected static char getTypeChar(IClass<?> primitive, boolean foldInt){
    if(!primitive.isPrimitive()) return 'A';

    char cha = 0;
    switch(primitive.realName()){
      case "C": cha = foldInt? 'I': 'C'; break;
      case "B": cha = foldInt? 'I': 'B'; break;
      case "S": cha = foldInt? 'I': 'S'; break;
      case "Z":
      case "I": cha = 'I'; break;
      case "J": cha = 'L'; break;
      case "F": cha = 'F'; break;
      case "D": cha = 'D'; break;
      default: throw new IllegalHandleException("unknown type name " + primitive.realName());
    }
    return cha;
  }
}
