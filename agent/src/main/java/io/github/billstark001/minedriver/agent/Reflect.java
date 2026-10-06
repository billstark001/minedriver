package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Resolves compatible signatures, including primitives, and refuses ambiguous overloads. */
final class Reflect {
  private Reflect() {}

  static Class<?> type(ClassLoader loader, String name) {
    try {
      return Class.forName(name, true, loader);
    } catch (ClassNotFoundException error) {
      throw new DriverException("UNSUPPORTED_API", "Class unavailable: " + name, error);
    }
  }

  static Object call(Object target, String name, Object... arguments) {
    Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
    List<Method> matches = new ArrayList<>();
    List<Method> bridges = new ArrayList<>();
    for (Method method : type.getMethods()) {
      if (!method.getName().equals(name)) continue;
      if (target instanceof Class<?> && !Modifier.isStatic(method.getModifiers())) continue;
      if (matches(method.getParameterTypes(), arguments)) {
        if (method.isBridge()) bridges.add(method);
        else matches.add(method);
      }
    }
    // Java emits public visibility bridges for methods inherited from non-public parents.
    // Retain those only when there is no compatible non-bridge implementation.
    if (matches.isEmpty()) matches.addAll(bridges);
    if (matches.size() != 1)
      throw new DriverException(
          "UNSUPPORTED_API",
          type.getName()
              + "."
              + name
              + ": expected one compatible signature, found "
              + matches.size());
    try {
      Method method = matches.get(0);
      Object instance = target instanceof Class<?> ? null : target;
      if (!method.canAccess(instance) && !method.trySetAccessible())
        method = publicInterfaceMethod(type, method);
      return method.invoke(instance, arguments);
    } catch (InvocationTargetException error) {
      throw propagate(error.getCause());
    } catch (ReflectiveOperationException | SecurityException error) {
      throw new DriverException(
          "UNSUPPORTED_API", "Cannot invoke " + type.getName() + "." + name, error);
    }
  }

  private static Method publicInterfaceMethod(Class<?> type, Method method)
      throws NoSuchMethodException {
    for (Class<?> candidate : type.getInterfaces()) {
      try {
        return candidate.getMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException ignored) {
        // Try the next public contract.
      }
    }
    if (type.getSuperclass() != null) return publicInterfaceMethod(type.getSuperclass(), method);
    throw new NoSuchMethodException(method.toString());
  }

  /** Only for explicitly versioned native callbacks; never exposed as a general RPC command. */
  static Object callback(Object target, String name, Class<?>[] signature, Object... arguments) {
    try {
      Method method = target.getClass().getDeclaredMethod(name, signature);
      if (!method.trySetAccessible()) throw new IllegalAccessException(method.toString());
      return method.invoke(target, arguments);
    } catch (InvocationTargetException error) {
      throw propagate(error.getCause());
    } catch (ReflectiveOperationException error) {
      throw new DriverException("UNSUPPORTED_API", "Native callback unavailable: " + name, error);
    }
  }

  static Object create(Class<?> type, Object... arguments) {
    var matches = new ArrayList<Constructor<?>>();
    for (var constructor : type.getConstructors()) {
      if (matches(constructor.getParameterTypes(), arguments)) matches.add(constructor);
    }
    if (matches.size() != 1)
      throw new DriverException(
          "UNSUPPORTED_API",
          type.getName() + ": expected one compatible constructor, found " + matches.size());
    try {
      return matches.get(0).newInstance(arguments);
    } catch (InvocationTargetException error) {
      throw propagate(error.getCause());
    } catch (ReflectiveOperationException error) {
      throw new DriverException("UNSUPPORTED_API", "Cannot construct " + type.getName(), error);
    }
  }

  static boolean matches(Class<?>[] types, Object[] arguments) {
    if (types.length != arguments.length) return false;
    for (int i = 0; i < types.length; i++) {
      if (arguments[i] == null) {
        if (types[i].isPrimitive()) return false;
      } else if (!boxed(types[i]).isInstance(arguments[i])) return false;
    }
    return true;
  }

  private static Class<?> boxed(Class<?> type) {
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == float.class) return Float.class;
    if (type == boolean.class) return Boolean.class;
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == char.class) return Character.class;
    return type;
  }

  static boolean hasMethod(Object target, String name, int arity) {
    Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
    for (Method method : type.getMethods())
      if (method.getName().equals(name) && method.getParameterCount() == arity) return true;
    return false;
  }

  static Object field(Object object, String name) {
    Class<?> type = object instanceof Class<?> clazz ? clazz : object.getClass();
    for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
      try {
        var field = cursor.getDeclaredField(name);
        Object instance = object instanceof Class<?> ? null : object;
        if (!field.canAccess(instance) && !field.trySetAccessible()) break;
        return field.get(instance);
      } catch (NoSuchFieldException ignored) {
        // Search the superclass.
      } catch (IllegalAccessException error) {
        throw new DriverException(
            "UNSUPPORTED_API", "Field inaccessible: " + type.getName() + "." + name, error);
      }
    }
    throw new DriverException(
        "UNSUPPORTED_API", "Field unavailable: " + type.getName() + "." + name);
  }

  static Object optionalField(Object object, String name) {
    try {
      return field(object, name);
    } catch (DriverException unsupported) {
      if (!unsupported.code().equals("UNSUPPORTED_API")) throw unsupported;
      return null;
    }
  }

  static RuntimeException propagate(Throwable error) {
    if (error instanceof RuntimeException runtime) return runtime;
    if (error instanceof Error fatal) throw fatal;
    return new DriverException("GAME_EXCEPTION", error.toString(), error);
  }
}
