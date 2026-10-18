package io.github.billstark001.minedriver.agent;

import com.google.gson.JsonObject;
import io.github.billstark001.minedriver.protocol.Json;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

/** Immutable per-launch names. Minecraft is never loaded to resolve transformer inheritance. */
final class RuntimeNames {
  private record Member(String name, String descriptor) {}

  private record Names(
      String target,
      List<String> parents,
      Map<Member, String> fields,
      Map<Member, String> methods,
      Map<Member, String> reverseFields,
      Map<Member, String> reverseMethods) {}

  private static volatile RuntimeNames current = new RuntimeNames();
  private final Map<String, Names> classes = new HashMap<>();
  private final Map<String, String> reverseClasses = new HashMap<>();
  private final java.util.concurrent.ConcurrentMap<Method, String> methodNames =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.ConcurrentMap<Field, String> fieldNames =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final Remapper canonicalTypes =
      new Remapper() {
        @Override
        public String map(String name) {
          return canonical(name);
        }
      };
  private String minecraft;
  private String namespace = "mojang";

  static RuntimeNames current() {
    return current;
  }

  static void install(Path file) throws IOException {
    current = file == null ? new RuntimeNames() : read(Json.read(file).getAsJsonObject());
  }

  static RuntimeNames read(JsonObject data) throws IOException {
    if (!data.has("format") || data.get("format").getAsInt() != 1)
      throw new IOException("Unsupported runtime mapping format");
    var names = new RuntimeNames();
    names.minecraft = data.get("minecraft").getAsString();
    names.namespace = data.get("namespace").getAsString();
    for (var item : data.getAsJsonArray("classes")) {
      JsonObject c = item.getAsJsonObject();
      String source = c.get("source").getAsString(), target = c.get("target").getAsString();
      var parents = new ArrayList<String>();
      c.getAsJsonArray("parents").forEach(parent -> parents.add(parent.getAsString()));
      var fields = members(c, "fields", false);
      var methods = members(c, "methods", false);
      names.classes.put(
          source,
          new Names(
              target,
              List.copyOf(parents),
              fields,
              methods,
              members(c, "fields", true),
              members(c, "methods", true)));
      names.reverseClasses.put(target, source);
    }
    return names;
  }

  private static Map<Member, String> members(JsonObject c, String key, boolean reverse)
      throws IOException {
    var result = new HashMap<Member, String>();
    for (var item : c.getAsJsonArray(key)) {
      var row = item.getAsJsonArray();
      String source = row.get(0).getAsString(),
          descriptor = row.get(1).getAsString(),
          target = row.get(2).getAsString();
      String previous =
          result.put(new Member(reverse ? target : source, descriptor), reverse ? source : target);
      if (previous != null && !previous.equals(reverse ? source : target))
        throw new IOException("Ambiguous member mapping in " + c.get("source"));
    }
    return Map.copyOf(result);
  }

  boolean mapped() {
    return !classes.isEmpty();
  }

  String minecraft() {
    return minecraft;
  }

  String namespace() {
    return namespace;
  }

  String actual(String canonical) {
    var c = classes.get(canonical);
    return c == null ? canonical : c.target();
  }

  String canonical(String actual) {
    return reverseClasses.getOrDefault(actual, actual);
  }

  String className(Class<?> type) {
    if (!mapped()) return type.getName();
    return canonical(Type.getInternalName(type)).replace('/', '.');
  }

  Remapper remapper(boolean toRuntime) {
    return new Remapper() {
      @Override
      public String map(String name) {
        return toRuntime ? actual(name) : canonical(name);
      }

      @Override
      public String mapMethodName(String owner, String name, String descriptor) {
        return member(owner, name, descriptor, true, toRuntime);
      }

      @Override
      public String mapFieldName(String owner, String name, String descriptor) {
        return member(owner, name, descriptor, false, toRuntime);
      }

      @Override
      public String mapRecordComponentName(String owner, String name, String descriptor) {
        return mapFieldName(owner, name, descriptor);
      }
    };
  }

  private String member(
      String owner, String name, String descriptor, boolean method, boolean toRuntime) {
    String source = toRuntime ? owner : canonical(owner);
    String desc = toRuntime ? descriptor : canonicalDescriptor(descriptor);
    String mapped = find(source, new Member(name, desc), method, !toRuntime, new HashSet<>());
    return mapped == null ? name : mapped;
  }

  private String find(
      String owner, Member member, boolean method, boolean reverse, Set<String> visited) {
    if (!visited.add(owner)) return null;
    Names c = classes.get(owner);
    if (c == null) return null;
    Map<Member, String> entries =
        method
            ? (reverse ? c.reverseMethods() : c.methods())
            : (reverse ? c.reverseFields() : c.fields());
    String value = entries.get(member);
    if (value != null) return value;
    for (String parent : c.parents()) {
      value = find(parent, member, method, reverse, visited);
      if (value != null) return value;
    }
    return null;
  }

  private String canonicalDescriptor(String descriptor) {
    return descriptor.startsWith("(")
        ? canonicalTypes.mapMethodDesc(descriptor)
        : canonicalTypes.mapDesc(descriptor);
  }

  String methodName(Method method) {
    if (!mapped()) return method.getName();
    return methodNames.computeIfAbsent(
        method,
        value ->
            reflectionName(
                value.getDeclaringClass(),
                value.getName(),
                canonicalDescriptor(Type.getMethodDescriptor(value)),
                true,
                new HashSet<>()));
  }

  String fieldName(Field field) {
    if (!mapped()) return field.getName();
    return fieldNames.computeIfAbsent(
        field,
        value ->
            reflectionName(
                value.getDeclaringClass(),
                value.getName(),
                canonicalDescriptor(Type.getDescriptor(value.getType())),
                false,
                new HashSet<>()));
  }

  private String reflectionName(
      Class<?> type, String name, String descriptor, boolean method, Set<Class<?>> visited) {
    if (type == null || !visited.add(type)) return name;
    String mapped =
        find(
            canonical(Type.getInternalName(type)),
            new Member(name, descriptor),
            method,
            true,
            new HashSet<>());
    if (mapped != null) return mapped;
    for (Class<?> parent : type.getInterfaces()) {
      mapped = reflectionName(parent, name, descriptor, method, visited);
      if (!mapped.equals(name)) return mapped;
    }
    return reflectionName(type.getSuperclass(), name, descriptor, method, visited);
  }

  String fieldName(Class<?> owner, String canonicalName) {
    if (!mapped()) return canonicalName;
    Names c = classes.get(canonical(Type.getInternalName(owner)));
    if (c != null)
      for (var field : c.fields().entrySet())
        if (field.getKey().name().equals(canonicalName)) return field.getValue();
    return canonicalName;
  }
}
