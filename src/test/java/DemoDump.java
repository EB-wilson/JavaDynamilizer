import org.objectweb.asm.*;

public class DemoDump implements Opcodes{

  public static byte[] dump() throws Exception{

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    RecordComponentVisitor recordComponentVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(V17, ACC_PUBLIC|ACC_SUPER, "Demo", null, "java/lang/Object", null);

    classWriter.visitSource("Demo.java", null);

    {
      fieldVisitor = classWriter.visitField(ACC_PUBLIC, "name", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(8, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(PUTFIELD, "Demo", "name", "Ljava/lang/String;");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(9, label2);
      methodVisitor.visitInsn(RETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable("this", "LDemo;", null, label0, label3, 0);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "run", "(I)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(12, label0);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
      methodVisitor.visitVarInsn(LSTORE, 2);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(13, label1);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitInsn(I2L);
      methodVisitor.visitVarInsn(LLOAD, 2);
      methodVisitor.visitInsn(LCMP);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(IFLE, label2);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(14, label3);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label4 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label4);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(16, label2);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{Opcodes.LONG}, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(LLOAD, 2);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(J)V", false);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(18, label4);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(LLOAD, 2);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
      methodVisitor.visitInsn(LCMP);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(IFGE, label5);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(19, label6);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("io");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(21, label5);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLocalVariable("this", "LDemo;", null, label0, label7, 0);
      methodVisitor.visitLocalVariable("i", "I", null, label0, label7, 1);
      methodVisitor.visitLocalVariable("time", "J", null, label1, label7, 2);
      methodVisitor.visitMaxs(4, 4);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}