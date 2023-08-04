package dynamilize.annoprocessor;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
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

@AutoService(Processor.class)
public class DynamicContextProcessor extends BaseProcessor {
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Type DynamicMakerType = ((Type) elements.getTypeElement("dynamilize.DynamicMaker").asType());
    Type DynamicClassType = ((Type) elements.getTypeElement("dynamilize.DynamicClass").asType());
    Type DynamicObjectType = ((Type) elements.getTypeElement("dynamilize.DynamicObject").asType());

    for(TypeElement element : annotations) {
      for (Element c : roundEnv.getElementsAnnotatedWith(element)) {
        JCTree.JCClassDecl root = (JCTree.JCClassDecl) trees.getTree(c);
        JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) trees.getPath(c).getCompilationUnit();

        root.accept(new TreeScanner(){
          HashMap<TypeCompound, Symbol.VarSymbol> constLists = new HashMap<>();
          Symbol.VarSymbol contextMaker;

          @Override
          public void visitVarDef(JCTree.JCVariableDecl tree) {
            RawAnnotationMirrors newDynamicAnno = getAnnotationParams(tree.mods.annotations, NewDynamic.class, unit, Map.of("refDynamicMaker", "<none>", "impls", new Class[0], "aspects", new Class[]{void.class}));
            if (newDynamicAnno != null){
              if (newDynamicAnno.getString("refDynamicMaker").equals("<none>") && contextMaker == null)
                throw new RuntimeException("not found usable DynamicMaker in context, please identify a field or variable of type DynamicMaker or its subclass, and add annotation @ContextMaker to it");

              if (!(tree.init instanceof JCTree.JCNewClass) && !(tree.init instanceof JCTree.JCTypeCast c && c.expr instanceof JCTree.JCNewClass))
                throw new RuntimeException("annotate @NewDynamic on field or variable require initial it by create instance");

              JCTree.JCTypeCast c = tree.init instanceof JCTree.JCTypeCast ca? ca: null;
              JCTree.JCNewClass n = (JCTree.JCNewClass) (c != null? c.expr: tree.init);

              JCTree.JCExpression baseClassRef = n.clazz;
              String dyc = newDynamicAnno.getString("dynClassName");
              JCTree[] inters = newDynamicAnno.getArrTree("impls");
              JCTree[] asp = newDynamicAnno.getArrTree("aspects");
              inters = inters == null? new JCTree[0]: inters;
              asp = asp == null? new JCTree[0]: asp;

              ArrayList<JCTree.JCExpression> args = new ArrayList<>();
              args.add(maker.Select(baseClassRef, names._class));

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

              args.addAll(n.args);

              JCTree.JCExpression invoke;
              if (contextMaker != null){
                invoke = maker.Apply(
                  List.nil(),
                  maker.Select(maker.Ident(contextMaker), names.fromString("newInstance")),
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

              tree.init = c == null || !isAssignable(DynamicObjectType, c.type)? maker.Apply(
                  List.nil(),
                  maker.Select(
                      invoke,
                      names.fromString("objSelf")
                  ),
                  List.nil()
              ): invoke;
            }
            else if (getAnnotationParams(tree.mods.annotations, ContextMaker.class, unit) != null) {
              if (tree.vartype.type.getKind() != TypeKind.DECLARED || !isAssignable(DynamicMakerType, tree.vartype.type))
                throw new RuntimeException("illegal annotation using! @ContextMaker can only annotate on var and field that type was DynamicMaker or it subclass");

              contextMaker = tree.sym;
            }

            super.visitVarDef(tree);
          }
        });

        genLog(element, root);
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
}
