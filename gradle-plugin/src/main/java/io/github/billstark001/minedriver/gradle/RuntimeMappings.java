package io.github.billstark001.minedriver.gradle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.objectweb.asm.ClassReader;

/**
 * Joins the consuming project's actual Loom names to Mojang names through the official namespace.
 */
final class RuntimeMappings {
  private RuntimeMappings() {}

  static boolean mojangNamed(Path tiny) throws IOException {
    var tree = tree(tiny);
    int named = tree.getNamespaceId("named");
    if (!tree.getSrcNamespace().equals("official") || named < 0)
      throw new IOException("Expected Loom official -> named mappings: " + tiny);
    return tree.getClasses().stream()
        .anyMatch(c -> "net/minecraft/client/Minecraft".equals(c.getName(named)));
  }

  static Map<String, Object> generate(String version, Path tiny, Path mojang, Path client)
      throws IOException {
    var runtime = tree(tiny);
    if (!runtime.getSrcNamespace().equals("official") || runtime.getNamespaceId("named") < 0)
      throw new IOException("Expected Loom official -> named mappings: " + tiny);
    int named = runtime.getNamespaceId("named");
    var canonical = tree(mojang);
    int official = 0;
    var classes = new ArrayList<Object>();
    try (var jar = new JarFile(client.toFile())) {
      var hierarchy = new HashMap<String, List<String>>();
      for (var source : canonical.getClasses()) {
        String obfuscated = source.getName(official);
        var parents = new ArrayList<String>();
        var entry = jar.getJarEntry(obfuscated + ".class");
        if (entry != null) {
          try (var input = jar.getInputStream(entry)) {
            var reader = new ClassReader(input);
            if (reader.getSuperName() != null) parents.add(reader.getSuperName());
            parents.addAll(List.of(reader.getInterfaces()));
          }
        }
        hierarchy.put(obfuscated, parents);
      }
      for (var source : canonical.getClasses()) {
        String obfuscated = source.getName(official);
        var target = runtime.getClass(obfuscated);
        if (target == null) continue;
        String actual = target.getName(named);
        if (actual == null) actual = obfuscated;
        var fields = new ArrayList<Object>();
        for (var field : source.getFields()) {
          String name =
              member(
                  runtime,
                  hierarchy,
                  obfuscated,
                  field.getName(official),
                  field.getDesc(official),
                  false,
                  named,
                  new HashSet<>());
          fields.add(
              List.of(
                  field.getSrcName(),
                  field.getSrcDesc(),
                  name == null ? field.getName(official) : name));
        }
        var methods = new ArrayList<Object>();
        for (var method : source.getMethods()) {
          String name =
              member(
                  runtime,
                  hierarchy,
                  obfuscated,
                  method.getName(official),
                  method.getDesc(official),
                  true,
                  named,
                  new HashSet<>());
          methods.add(
              List.of(
                  method.getSrcName(),
                  method.getSrcDesc(),
                  name == null ? method.getName(official) : name));
        }
        var parents = new ArrayList<String>();
        for (String parent : hierarchy.get(obfuscated))
          parents.add(canonical.mapClassName(parent, official, -1));
        classes.add(
            Map.of(
                "source",
                source.getSrcName(),
                "target",
                actual,
                "parents",
                parents,
                "fields",
                fields,
                "methods",
                methods));
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("format", 1);
    result.put("minecraft", version);
    result.put("namespace", "named");
    result.put("classes", classes);
    return result;
  }

  // Tiny omits many overrides whose names are supplied by an interface or superclass.
  private static String member(
      MemoryMappingTree tree,
      Map<String, List<String>> hierarchy,
      String owner,
      String name,
      String descriptor,
      boolean method,
      int named,
      Set<String> visited) {
    if (!visited.add(owner)) return null;
    var type = tree.getClass(owner);
    if (type != null) {
      var entry = method ? type.getMethod(name, descriptor) : type.getField(name, descriptor);
      if (entry != null && entry.getName(named) != null) return entry.getName(named);
    }
    for (String parent : hierarchy.getOrDefault(owner, List.of())) {
      String mapped = member(tree, hierarchy, parent, name, descriptor, method, named, visited);
      if (mapped != null) return mapped;
    }
    return null;
  }

  static MemoryMappingTree tree(Path file) throws IOException {
    var tree = new MemoryMappingTree(true);
    MappingReader.read(file, tree);
    return tree;
  }
}
