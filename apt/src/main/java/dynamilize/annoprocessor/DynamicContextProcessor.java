package dynamilize.annoprocessor;

import com.google.auto.service.AutoService;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import dynamilize.annotations.ContextMaker;
import dynamilize.annotations.DynamicContext;
import dynamilize.annotations.NewDynamic;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@AutoService(Processor.class)
public class DynamicContextProcessor extends BaseProcessor {
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for(TypeElement element : annotations) {
      for (Element c : roundEnv.getElementsAnnotatedWith(element)) {
        JCTree.JCClassDecl root = (JCTree.JCClassDecl) trees.getTree(c);
        JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) trees.getPath(c).getCompilationUnit();

        final HashMap<TypeCompound, Symbol.VarSymbol> constLists = new HashMap<>();

        root.accept(new MakerContextScanner(unit){
          @Override
          public void visitNewClass(JCTree.JCNewClass tree) {
            scan(tree.encl);
            scan(tree.typeargs);
            scan(tree.clazz);
            scan(tree.args);
            //scan(tree.def); 不处理匿名块的内容
          }

          @Override
          public void visitVarDefSub(JCTree.JCVariableDecl var) {
            RawAnnotationMirrors newDynamicAnno = getAnnotationParams(var.mods.annotations, NewDynamic.class, unit, Map.of("refDynamicMaker", "<none>", "impls", new Class[0], "aspects", new Class[]{void.class}));

            if (newDynamicAnno != null){
              if (newDynamicAnno.getString("refDynamicMaker").equals("<none>") && getMaker() == null)
                throw new RuntimeException("can not find usable DynamicMaker in context, please identify a field or variable of type DynamicMaker or its subclass, and add annotation @ContextMaker to it");

              if (!(var.init instanceof JCTree.JCNewClass) && !(var.init instanceof JCTree.JCTypeCast c && c.expr instanceof JCTree.JCNewClass))
                throw new RuntimeException("annotate @NewDynamic on field or variable require initial it by create instance");

              JCTree.JCTypeCast c = var.init instanceof JCTree.JCTypeCast ca? ca: null;
              JCTree.JCNewClass n = (JCTree.JCNewClass) (c != null? c.expr: var.init);

              //TODO: make it workable
              if (n.def != null) throw new RuntimeException("anonymous extends is illegal in new dynamic object context");

              TreePath path = trees.getPath(unit, n);
              Symbol sym = trees.getElement(path);

              boolean isEncl = sym instanceof Symbol.MethodSymbol msy && msy.isConstructor() && sym.owner.isInner();
              if (isEncl && n.encl == null){
                n.encl = maker.Select(
                    maker.Type(sym.owner.owner.type),
                    names._this
                );
              }

              JCTree.JCExpression baseClassRef = maker.Select(n.clazz, names._class);

              String dyc = newDynamicAnno.getString("dynClassName");
              JCTree[] inters = newDynamicAnno.getArrTree("impls");
              JCTree[] asp = newDynamicAnno.getArrTree("aspects");
              inters = inters == null? new JCTree[0]: inters;
              asp = asp == null? new JCTree[0]: asp;

              ArrayList<JCTree.JCExpression> args = new ArrayList<>();
              args.add(baseClassRef);

              Attribute[] i = newDynamicAnno.getArr("impls");
              JCTree[] finalInters = inters;
              args.add(maker.Ident(constLists.computeIfAbsent(
                  new TypeCompound(Arrays.stream(i).map(e -> ((Attribute.Class) e).classType).toArray(Type[]::new)),
                  ts -> makeConstField(root, constLists.size(), finalInters)
              )));

              Attribute[] a = newDynamicAnno.getArr("aspects");
              if (a.length > 0 && ((Attribute.Class) a[0]).classType.getKind() != TypeKind.VOID) {
                JCTree[] finalAsp = asp;
                args.add(maker.Ident(constLists.computeIfAbsent(
                    new TypeCompound(Arrays.stream(a).map(e -> ((Attribute.Class) e).classType).toArray(Type[]::new)),
                    ts -> makeConstField(root, constLists.size(), finalAsp)
                )));
              }
              else args.add(maker.TypeCast(
                  maker.TypeArray(maker.Ident(symtab.classType.tsym)),
                  maker.Literal(TypeTag.BOT, null)
              ));

              args.add(maker.Apply(
                  List.nil(),
                  maker.Select(
                      maker.Type(DynamicClassType),
                      names.fromString("get")
                  ),
                  List.of(maker.Literal(dyc))
              ));

              if (isEncl) args.add(n.encl);
              args.addAll(n.args);

              JCTree.JCExpression invoke;
              if (getMaker() != null){
                invoke = maker.Apply(
                  List.nil(),
                  maker.Select(maker.Ident(getMaker()), names.fromString("newInstance")),
                  List.from(args)
                );
              }
              else {
                invoke = maker.Apply(
                    List.nil(),
                    maker.Select(
                        parsers.newParser(newDynamicAnno.getString("refDynamicMaker"), false, true, false).parseExpression(),
                        names.fromString("newInstance")
                    ),
                    List.from(args)
                );
              }

              var.init = c == null || !isAssignable(DynamicObjectType, c.type)? maker.Apply(
                  List.nil(),
                  maker.Select(
                      invoke,
                      names.fromString("objSelf")
                  ),
                  List.nil()
              ): invoke;
            }

            super.visitVarDefSub(var);
          }
        });

        genLog(element, unit);
      }
    }
    return super.process(annotations, roundEnv);
  }

  private Symbol.VarSymbol makeConstField(JCTree.JCClassDecl root, int id, JCTree[] aspects) {
    Symbol.VarSymbol sym = new Symbol.VarSymbol(
        Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL,
        names.fromString("$TYPE_LIST_CONST@" + id),
        types.makeArrayType(new Type.ClassType(symtab.classType.tsym.owner.type, List.nil(), symtab.classType.tsym)),
        root.sym
    );

    root.defs = root.defs.prepend(maker.VarDef(sym, maker.NewArray(
        maker.Ident(symtab.classType.tsym),
        List.nil(),
        List.from(Arrays.stream(aspects).map(t -> ((JCTree.JCExpression) t)).toList())
    )));

    return sym;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Set.of(DynamicContext.class.getCanonicalName());
  }
  
  private static class TypeCompound {
    private final Type[] types;
    private final int hash;

    private TypeCompound(Type[] types) {
      this.types = types;
      for (Type type : types) {
        if (!type.isInterface()) throw new IllegalArgumentException("interface only");
      }
      hash = Arrays.hashCode(types);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TypeCompound that = (TypeCompound) o;
      return hash == that.hash && Arrays.equals(types, that.types);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(hash);
      result = 31 * result + Arrays.hashCode(types);
      return result;
    }
  }

  class MakerContextScanner extends TreeScanner{
    final JCTree.JCCompilationUnit unit;

    boolean isStatic;

    LinkedList<AtomicReference<Symbol.VarSymbol>> contextMakerStack = new LinkedList<>();
    LinkedList<AtomicReference<Symbol.VarSymbol>> staticMakerStack = new LinkedList<>();

    MakerContextScanner(JCTree.JCCompilationUnit unit) {
      this.unit = unit;
    }

    public void setTopSym(Symbol.VarSymbol sym){
      if (isStatic){
        if (staticMakerStack.isEmpty()) throw new RuntimeException("no maker found");
        staticMakerStack.peek().set(sym);
      }
      if (contextMakerStack.isEmpty()) throw new RuntimeException("no maker found");
      contextMakerStack.peek().set(sym);
    }

    public Symbol.VarSymbol getMaker(){
      LinkedList<AtomicReference<Symbol.VarSymbol>> stack = isStatic? staticMakerStack: contextMakerStack;
      for (AtomicReference<Symbol.VarSymbol> ref : stack) {
        if (ref.get() != null) return ref.get();
      }

      return null;
    }

    @Override
    public final void visitClassDef(JCTree.JCClassDecl tree) {
      isStatic = false;
      contextMakerStack.push(new AtomicReference<>());
      staticMakerStack.push(new AtomicReference<>());
      visitClassDefSub(tree);
      contextMakerStack.pop();
      staticMakerStack.pop();
    }

    public void visitClassDefSub(JCTree.JCClassDecl tree){
      super.visitClassDef(tree);
    }

    @Override
    public final void visitBlock(JCTree.JCBlock tree) {
      boolean tmp = isStatic;
      isStatic = isStatic || ((tree.flags & Flags.STATIC) != 0);
      staticMakerStack.push(new AtomicReference<>());
      contextMakerStack.push(new AtomicReference<>());
      visitBlockSub(tree);
      staticMakerStack.pop();
      contextMakerStack.pop();
      isStatic = tmp;
    }

    public void visitBlockSub(JCTree.JCBlock tree) {
      super.visitBlock(tree);
    }

    @Override
    public final void visitMethodDef(JCTree.JCMethodDecl tree) {
      boolean tmp = isStatic;
      isStatic = tree.sym.isStatic();
      contextMakerStack.push(new AtomicReference<>());
      staticMakerStack.push(new AtomicReference<>());
      visitMethodDefSub(tree);
      contextMakerStack.pop();
      staticMakerStack.pop();
      isStatic = tmp;
    }

    public void visitMethodDefSub(JCTree.JCMethodDecl tree) {
      super.visitMethodDef(tree);
    }

    @Override
    public final void visitVarDef(JCTree.JCVariableDecl tree) {
      boolean tmp = isStatic;
      isStatic = (tree.sym != null && tree.sym.isStatic()) || tmp;
      if (getAnnotationParams(tree.mods.annotations, ContextMaker.class, unit) != null) {
        if (tree.vartype.type.getKind() != TypeKind.DECLARED || !isAssignable(DynamicMakerType, tree.vartype.type))
          throw new RuntimeException("illegal annotation using! @ContextMaker can only annotate on var and field that type was DynamicMaker or it subclass");

        setTopSym(tree.sym);
      }
      visitVarDefSub(tree);
      isStatic = tmp;
    }

    public void visitVarDefSub(JCTree.JCVariableDecl tree){
      super.visitVarDef(tree);
    }
  }
}
