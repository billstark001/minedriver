package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class ReflectCovarianceTest {
  private static final class Loader extends ClassLoader {
    Class<?> define(byte[] bytes) {
      return defineClass(null, bytes, 0, bytes.length);
    }
  }

  @Test
  void remappedCovariantMethodsDoNotNeedBridgeFlags() {
    var writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/Covariant", null, "java/lang/Object", null);
    for (String returnType : new String[] {"java/lang/Object", "java/lang/String"}) {
      var method =
          writer.visitMethod(
              Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
              "level",
              "()L" + returnType + ";",
              null,
              null);
      method.visitCode();
      method.visitLdcInsn(returnType);
      method.visitInsn(Opcodes.ARETURN);
      method.visitMaxs(1, 0);
      method.visitEnd();
    }
    writer.visitEnd();
    assertEquals(
        "java/lang/String", Reflect.call(new Loader().define(writer.toByteArray()), "level"));
  }

  public static final class Ambiguous {
    public static String pick(CharSequence value) {
      return "characters";
    }

    public static String pick(java.io.Serializable value) {
      return "serializable";
    }
  }

  @Test
  void unrelatedOverloadsStillFail() {
    assertEquals(
        "UNSUPPORTED_API",
        assertThrows(DriverException.class, () -> Reflect.call(Ambiguous.class, "pick", "value"))
            .code());
  }
}
