package dynamilize.annoprocessor;

import com.google.auto.service.AutoService;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import dynamilize.annotations.DynamilizeClass;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

@AutoService(Processor.class)
public class ClassDynamilizeProcessor extends BaseProcessor{
  @Override
  public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
    Type RawType = new Type.ClassType(
        DynamicObjectType.tsym.owner.type,
        List.of(new Type.WildcardType(symtab.objectType.tsym.type, BoundKind.UNBOUND, DynamicObjectType.tsym)),
        DynamicObjectType.tsym
    );

    Type ThisType = ((Type) elements.getTypeElement("dynamilize.runtimeannos.This").asType());
    Type SuperType = ((Type) elements.getTypeElement("dynamilize.runtimeannos.Super").asType());
    Type ExcludeType = ((Type) elements.getTypeElement("dynamilize.runtimeannos.Exclude").asType());
    Type ProducterType = ((Type) elements.getTypeElement("dynamilize.Initializer.Producer").asType());
    Type functionType = (Type) elements.getTypeElement("dynamilize.FunctionType").asType();

    for (TypeElement anno : set) {
      for (Element element : roundEnvironment.getElementsAnnotatedWith(anno)) {
        JCTree.JCClassDecl root = (JCTree.JCClassDecl) trees.getTree(element);
        JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) trees.getPath(root.sym).getCompilationUnit();

        if (element.getEnclosingElement().getKind() != ElementKind.PACKAGE && !Flags.isStatic(root.sym))
          throw new RuntimeException("Dynamilize class must be top level class or static enclosed class");

        Type superClass = root.sym.getSuperclass();
        if (superClass.equals(symtab.objectType.tsym.type)) superClass = null;

        DynamilizeClass annotation = element.getAnnotation(DynamilizeClass.class);
        String className = annotation.className().equals("<none>")? root.sym.type.tsym.getQualifiedName().toString(): annotation.className();
        String helperField = annotation.helperField();
        if (helperField.isBlank() || Character.isDigit(helperField.trim().charAt(0)) || helperField.contains("-"))
          throw new IllegalArgumentException("illegal field name: " + helperField);

        Symbol.VarSymbol makeHelper = null;
        for (JCTree def : root.defs) {
          if (def instanceof JCTree.JCVariableDecl f){
            if (f.name.equals(names.fromString("INSTANCE")) || f.name.equals(names.fromString("NAME"))) throw new RuntimeException("used field name: " + f.getName());
            else if (f.name.equals(names.fromString(helperField))){
              if (!Flags.isStatic(f.sym))
                throw new RuntimeException("helper field must be static");

              f.sym.appendAttributes(List.of(new Attribute.Compound(ExcludeType, List.nil())));
              JCTree.JCAnnotation an = maker.Annotation(maker.Type(ExcludeType), List.nil());
              an.type = ExcludeType;

              f.mods.annotations = f.mods.annotations.append(an);

              makeHelper = f.sym;
              break;
            }
          }
        }
        if (makeHelper == null)
          throw new RuntimeException("helper store field was not found, please add a field to store helper (now set helper field name: \"" + helperField + "\")");

        Symbol.VarSymbol NAME = new Symbol.VarSymbol(
            Flags.STATIC | Flags.PUBLIC | Flags.FINAL | Flags.HASINIT,
            names.fromString("NAME"),
            symtab.stringType.tsym.type.constType(className),
            root.sym
        );
        NAME.setDeclarationAttributes(List.of(new Attribute.Compound(ExcludeType, List.nil())));
        Symbol.VarSymbol INSTANCE = new Symbol.VarSymbol(
            Flags.STATIC | Flags.PUBLIC | Flags.FINAL | Flags.HASINIT,
            names.fromString("INSTANCE"),
            DynamicClassType,
            root.sym
        );
        INSTANCE.setDeclarationAttributes(List.of(new Attribute.Compound(ExcludeType, List.nil())));

        root.sym.members_field.enter(NAME);
        root.sym.members_field.enter(INSTANCE);

        ArrayList<JCTree> list = new ArrayList<>(root.defs);
        boolean methodDecled = false;
        boolean foundHelper = false;
        for (int i = 0; i < list.size(); i++) {
          if (list.get(i) instanceof JCTree.JCVariableDecl v && v.name.equals(names.fromString(helperField))) foundHelper = true;
          else if (list.get(i) instanceof JCTree.JCMethodDecl m && !m.sym.isConstructor()) methodDecled = true;

          if (methodDecled && foundHelper){
            maker.at(list.get(i).getEndPosition(unit.endPositions));

            List<JCTree.JCExpression> args = List.of(
                maker.Literal(className),
                maker.ClassLiteral(root.sym.type),
                superClass != null? maker.Select(maker.Type(superClass), names.fromString("INSTANCE")): maker.Literal(TypeTag.BOT, null),
                maker.Ident(makeHelper)
            );
            JCTree.JCVariableDecl nameDecl = maker.VarDef(NAME, maker.Literal(className));
            JCTree.JCAnnotation an = maker.Annotation(maker.Type(ExcludeType), List.nil());
            an.type = ExcludeType;
            nameDecl.mods.annotations = List.of(an);

            JCTree.JCVariableDecl instDecl = maker.VarDef(INSTANCE, maker.Apply(
                List.nil(),
                maker.Select(maker.Type(DynamicClassType), names.fromString("visit")),
                args
            ));
            JCTree.JCAnnotation ana = maker.Annotation(maker.Type(ExcludeType), List.nil());
            ana.type = ExcludeType;
            instDecl.mods.annotations = List.of(ana);

            list.add(i, instDecl);
            list.add(i, nameDecl);
            break;
          }
        }
        root.defs = List.from(list);

        root.accept(new TreeScanner(){
          final HashMap<Symbol.MethodSymbol, Symbol.VarSymbol> signatures = new HashMap<>();

          @Override
          public void visitClassDef(JCTree.JCClassDecl tree) {
            if (tree == root) super.visitClassDef(tree);//不处理任何内部类
            else if (!Flags.isStatic(tree.sym)) throw new RuntimeException("dynamilized class cannot contains a non-static internal class");
          }

          @Override
          public void visitMethodDef(JCTree.JCMethodDecl meth) {
            if (!Flags.isStatic(meth.sym) && (Flags.PUBLIC & meth.mods.flags) != 0 && !meth.sym.isConstructor()){
              meth.mods.flags = meth.mods.flags | Flags.STATIC;
              meth.mods.annotations = List.nil();
              meth.sym.resetAnnotations();

              Symbol.VarSymbol self = new Symbol.VarSymbol(Flags.PARAMETER, names.fromString("self"), RawType, meth.sym);
              Symbol.VarSymbol sup = new Symbol.VarSymbol(Flags.PARAMETER, names.fromString("sup"), ReadOnlyPoolType, meth.sym);

              self.setDeclarationAttributes(List.of(new Attribute.Compound(ThisType, List.nil())));
              sup.setDeclarationAttributes(List.of(new Attribute.Compound(SuperType, List.nil())));

              meth.sym.params = meth.sym.params.prepend(sup);
              meth.sym.params = meth.sym.params.prepend(self);

              JCTree.JCVariableDecl s = maker.VarDef(self, null);
              JCTree.JCVariableDecl su = maker.VarDef(sup, null);
              meth.params = meth.params.prepend(su);
              meth.params = meth.params.prepend(s);

              meth.sym.type = new Type.MethodType(List.from(meth.params.map(d -> d.sym.type)), meth.sym.getReturnType(), meth.sym.getThrownTypes(), meth.sym.type.tsym);

              meth.body.accept(new TreeTranslator() {
                @Override
                public void visitIdent(JCTree.JCIdent tree) {
                  if (tree.name.equals(names._super) || tree.name.equals(names._this)){
                    super.visitIdent(tree);
                    return;
                  }

                  TreePath path = trees.getPath(unit, tree);
                  Symbol sym = trees.getElement(path);

                  if (sym != null && sym.getKind() != ElementKind.PARAMETER
                  && (sym instanceof Symbol.MethodSymbol || (sym instanceof Symbol.VarSymbol v && v.owner instanceof Symbol.ClassSymbol)) && !Flags.isStatic(sym)) {
                    JCTree.JCFieldAccess res = maker.Select(maker.Ident(names._this), sym.name);
                    res.sym = sym;
                    result = res;
                  }
                  else super.visitIdent(tree);
                }
              });
              meth.body.accept(new TreeTranslator(){
                @Override
                public void visitApply(JCTree.JCMethodInvocation tree) {
                  if (tree.meth instanceof JCTree.JCFieldAccess ac
                      && ac.name.equals(names.fromString("super$")) && tree.args.isEmpty()){
                    JCTree.JCIdent res = maker.Ident(sup);
                    res.sym = ac.sym;
                    result = res;
                  }
                  else super.visitApply(tree);
                }

                @Override
                public void visitAssign(JCTree.JCAssign tree) {
                  if (tree.lhs instanceof JCTree.JCFieldAccess ac && isSelfRef(ac)){
                    result = maker.Apply(
                        List.nil(),
                        maker.Select(maker.Ident(self), names.fromString("setVar")),
                        List.of(maker.Literal(ac.name.toString()), tree.rhs)
                    );
                  }
                  else super.visitAssign(tree);
                }

                @Override
                public void visitAssignop(JCTree.JCAssignOp tree) {
                  if (tree.lhs instanceof JCTree.JCFieldAccess ac && ac.sym instanceof Symbol.VarSymbol v && isSelfRef(ac)) {
                    Symbol.ParamSymbol arg = new Symbol.ParamSymbol(0, names.fromString("val"), v.type, meth.sym);

                    tree.lhs = maker.Ident(arg);

                    result = maker.Apply(
                        List.nil(),
                        maker.Select(maker.Ident(self), names.fromString("calculateVar")),
                        List.of(
                            maker.Literal(ac.name.toString()),
                            maker.Lambda(
                                List.of(maker.Param(arg.name, arg.type, meth.sym)),
                                tree
                            )
                        )
                    );
                  }
                  else super.visitAssignop(tree);
                }

                @Override
                public void visitUnary(JCTree.JCUnary tree) {
                  if (tree.arg instanceof JCTree.JCFieldAccess ac && ac.sym instanceof Symbol.VarSymbol v && isSelfRef(ac)) {
                    Symbol.ParamSymbol arg = new Symbol.ParamSymbol(0, names.fromString("val"), v.type, meth.sym);

                    tree.arg = maker.Ident(arg);

                    result = maker.Apply(
                        List.nil(),
                        maker.Select(maker.Ident(self), names.fromString("calculateVar")),
                        List.of(
                            maker.Literal(ac.name.toString()),
                            maker.Lambda(
                                List.of(maker.Param(arg.name, arg.type, meth.sym)),
                                tree
                            )
                        )
                    );
                  }
                  else super.visitUnary(tree);
                }
              });
              meth.body.accept(new TreeTranslator(){
                @Override
                public void visitSelect(JCTree.JCFieldAccess tree) {
                  if (tree.sym instanceof Symbol.VarSymbol var && isSelfRef(tree)) {
                    Object def = switch(var.type.getTag()){
                      case BYTE, INT, DOUBLE, FLOAT, LONG, SHORT, CHAR -> 0;
                      case BOOLEAN -> false;
                      default -> null;
                    };

                    result = maker.Apply(
                        List.of(maker.Type(var.type)),
                        maker.Select(maker.Ident(self), names.fromString("getVar")),
                        List.from(
                            def != null? new JCTree.JCExpression[]{
                                maker.Literal(tree.name.toString()),
                                maker.Literal(var.type.getTag(), def)
                            }:
                            new JCTree.JCExpression[]{
                                maker.Literal(tree.name.toString())
                            }
                        )
                    );
                  }
                  else super.visitSelect(tree);

                }
              });
              meth.body.accept(new TreeScanner(){
                @Override
                public void visitApply(JCTree.JCMethodInvocation tree){
                  if (tree.meth instanceof JCTree.JCFieldAccess ac && ac.sym instanceof Symbol.MethodSymbol m && !m.isConstructor()
                  && !m.owner.type.equals(DynamicObjectType) && isSelfRef(ac)){
                    Symbol.VarSymbol signature = annotation.signatureMethods()? signatures.computeIfAbsent(m, met -> {
                      Symbol.VarSymbol sym = new Symbol.VarSymbol(
                          Flags.PRIVATE | Flags.STATIC | Flags.FINAL,
                          names.fromString("signature#" + signatures.size()),
                          functionType,
                          root.sym
                      );
                      sym.setDeclarationAttributes(List.of(new Attribute.Compound(ExcludeType, List.nil())));

                      ArrayList<JCTree.JCExpression> list = new ArrayList<>();
                      for (Symbol.VarSymbol param : m.params) {
                        list.add(maker.ClassLiteral(new Type.ClassType(param.type, List.nil(), param.type.tsym)));
                      }
                      root.defs = root.defs.prepend(maker.VarDef(
                          sym,
                          maker.Apply(
                              List.nil(),
                              maker.Select(
                                  maker.Type(functionType),
                                  names.fromString("inst")
                              ),
                              List.from(list)
                          )
                      ));

                      return sym;
                    }): null;

                    tree.args = tree.args.prepend(maker.Literal(ac.name.toString()));
                    tree.typeargs = List.of(maker.Type(m.getReturnType()));
                    if (signature != null) tree.args = tree.args.prepend(maker.Ident(signature));
                    ac.name = names.fromString("invokeFunc");
                  }

                  super.visitApply(tree);
                }

                @Override
                public void visitIdent(JCTree.JCIdent tree) {
                  if (tree.name.equals(names._this)) tree.name = self.name;
                  else if (tree.name.equals(names._super)) tree.name = sup.name;
                }
              });
            }
            else if (meth.sym != null && meth.sym.getAnnotationMirrors().stream().noneMatch(e -> e.type.equals(ExcludeType))){
              meth.sym.appendAttributes(List.of(new Attribute.Compound(ExcludeType, List.nil())));
              JCTree.JCAnnotation anno = maker.Annotation(maker.Type(ExcludeType), List.nil());
              anno.type = ExcludeType;
              meth.mods.annotations = meth.mods.annotations.append(anno);
            }
          }
        });

        for (JCTree def : root.defs) {
          if (def instanceof JCTree.JCVariableDecl field){
            if (!Flags.isStatic(field.sym)){
              field.mods.flags = (field.mods.flags | Flags.STATIC | Flags.PUBLIC) & ~(Flags.PRIVATE | Flags.PROTECTED);

              if (!(field.init instanceof JCTree.JCLiteral) && field.init != null){
                field.sym.type = new Type.ClassType(
                    new Type.ClassType(ProducterType.tsym.owner.type, List.nil(), ProducterType.tsym.owner.type.tsym),
                    List.of(new Type.WildcardType(field.sym.type, BoundKind.UNBOUND, ProducterType.tsym)),
                    ProducterType.tsym
                );
                field.vartype = maker.Type(field.sym.type);
                field.type = field.sym.type;
                field.init = maker.Lambda(
                    List.nil(),
                    field.init
                );
              }
            }
            else if (field.sym != null && field.sym.getAnnotationMirrors().stream().noneMatch(e -> e.type.equals(ExcludeType))){
              field.sym.appendAttributes(List.of(new Attribute.Compound(ExcludeType, List.nil())));
              JCTree.JCAnnotation a = maker.Annotation(maker.Type(ExcludeType), List.nil());
              a.type = ExcludeType;
              field.mods.annotations = field.mods.annotations.append(a);
            }
          }
        }

        //Static blocks aren't actually that pretty... What do you think about?
        /*root.defs = root.defs.append(maker.Block(Flags.STATIC, List.of(
            maker.Exec(
                maker.Apply(
                    List.nil(),
                    maker.Select(maker.Ident(INSTANCE), names.fromString("visitClass")),
                    List.of(maker.ClassLiteral(root.sym.type), maker.Ident(makeHelper))
                )
            )
        )));*/

        genLog(anno, unit);
      }
    }
    return super.process(set, roundEnvironment);
  }

  private boolean isSelfRef(JCTree.JCFieldAccess ac) {
    return ac.selected instanceof JCTree.JCIdent i
        && (i.name.equals(names._this) || i.name.equals(names._super));
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Set.of(DynamilizeClass.class.getCanonicalName());
  }
}
