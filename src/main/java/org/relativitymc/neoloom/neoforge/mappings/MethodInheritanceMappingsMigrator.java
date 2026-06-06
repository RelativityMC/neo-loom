/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024-2025 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.relativitymc.neoloom.neoforge.mappings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.Pair;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import org.relativitymc.neoloom.neoforge.NFRTMergedMinecraftProvider;

/**
 * With some forge patches, methods can inherit methods from a class that is not in the mappings.
 * This migrator will try to detect all the methods that are inherited from a class that is not in the mappings,
 * see if there are different names for the same method in the mappings, and remove them.
 */
public final class MethodInheritanceMappingsMigrator implements MappingsMigrator {
	private Set<Pair<String, String>> methodsToRemove;

	@Override
	public long setup(Project project, NFRTMergedMinecraftProvider minecraftProvider, Path cache, Path rawMappings) throws IOException {
		Path cacheFile = cache.resolve("method-inheritance-migrator.json");

		if (!minecraftProvider.refreshDeps() && Files.exists(cacheFile)) {
			try (BufferedReader reader = Files.newBufferedReader(cacheFile)) {
				List<Pair<String, String>> list = new Gson().fromJson(reader, new TypeToken<List<Pair<String, String>>>() {
				});
				methodsToRemove = new HashSet<>(list);
			}
		} else {
			Files.deleteIfExists(cacheFile);
			LoomGradleExtension extension = LoomGradleExtension.get(project);
			methodsToRemove = new HashSet<>();

			// for (Path jar : minecraftProvider.getMinecraftJars()) {
			// 	methodsToRemove.addAll(prepareCache(project.getLogger(), rawMappings, List.of(jar)));
			// }
			methodsToRemove.addAll(prepareCache(project.getLogger(), minecraftProvider.getOfficialNamespace().toString(), rawMappings, minecraftProvider.getFullClasspath()));

			Files.writeString(cacheFile, new Gson().toJson(methodsToRemove.stream().sorted(Comparator.comparing(p -> p.left() + "|" + p.right())).toList()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		return methodsToRemove.hashCode();
	}

	@Override
	public void migrate(Project project, List<MappingsEntry> entries) throws IOException {
		for (MappingsEntry entry : entries) {
			MemoryMappingTree mappings = new MemoryMappingTree();

			try (BufferedReader reader = Files.newBufferedReader(entry.path())) {
				MappingReader.read(reader, mappings);
			}

			for (MappingTree.ClassMapping classMapping : mappings.getClasses()) {
				// TODO: Change if/when MIO supports removals again
				for (MappingTree.MethodMapping method : List.copyOf(classMapping.getMethods())) {
					var key = new Pair<>(method.getName(MappingsNamespace.INTERMEDIARY.toString()), method.getDesc(MappingsNamespace.INTERMEDIARY.toString()));

					if (methodsToRemove.contains(key)) {
						classMapping.removeMethod(method.getSrcName(), method.getSrcDesc());
					}
				}
			}

			try (Writer writer = Files.newBufferedWriter(entry.path(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				mappings.accept(new Tiny2FileWriter(writer, false));
			}
		}
	}

	private Set<Pair<String, String>> prepareCache(Logger logger, String patchedNs, Path rawMappings, List<Path> jars) throws IOException {
		MemoryMappingTree mappings = new MemoryMappingTree();

		try (BufferedReader reader = Files.newBufferedReader(rawMappings)) {
			MappingReader.read(reader, new MappingSourceNsSwitch(mappings, patchedNs));
		}

		Map<String, ClassEntry> collected = collectClassesAndMethods(jars);
		Set<MethodInstance> methodInstances = new HashSet<>();

		for (ClassEntry classEntry : collected.values()) {
			methodInstances.addAll(classEntry.methods.values());
		}

		Set<Pair<String, String>> methodsToRemove = new HashSet<>();

		{
			int intermediaryId = mappings.getNamespaceId(MappingsNamespace.INTERMEDIARY.toString());
			Set<String> knownIntermediaryNames = new HashSet<>();

			for (MethodInstance methodInstance : methodInstances) {
				if (methodInstance.knownInstances.size() <= 1) continue;
				knownIntermediaryNames.clear();

				// find all possible mappings
				for (MethodKey key : methodInstance.knownInstances) {
					MappingTree.ClassMapping aClass = mappings.getClass(key.className());
					if (aClass == null) continue;
					MappingTree.MethodMapping aMethod = aClass.getMethod(key.name(), key.descriptor());
					if (aMethod == null) continue;
					String intermediaryName = aMethod.getName(intermediaryId);
					if (intermediaryName != null) knownIntermediaryNames.add(intermediaryName);
				}

				if (knownIntermediaryNames.size() >= 2) {
					// We should remove these names from the mappings
					// as the particular method is inherited by multiple different intermediary names
					String mappedDesc = mappings.mapDesc(methodInstance.descriptor, intermediaryId);

					for (String intermediaryName : knownIntermediaryNames) {
						methodsToRemove.add(new Pair<>(intermediaryName, mappedDesc));
						logger.lifecycle("Removing method {}{} from the mappings", intermediaryName, mappedDesc);
					}
				}
			}
		}

		return methodsToRemove;
	}

	private static Map<String, ClassEntry> collectClassesAndMethods(Iterable<Path> jars) throws IOException {
		Map<String, ClassEntry> classes = new HashMap<>();
		Visitor visitor = new Visitor(Constants.ASM_VERSION, classes);

		for (Path jar : jars) {
			try (FileSystemUtil.Delegate system = FileSystemUtil.getJarFileSystem(jar, false)) {
				for (Path fsPath : (Iterable<? extends Path>) Files.walk(system.get().getPath("/"))::iterator) {
					if (Files.isRegularFile(fsPath) && fsPath.toString().endsWith(".class")) {
						new ClassReader(Files.readAllBytes(fsPath)).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					}
				}
			}
		}

		// Populate class inheritance
		for (ClassEntry classEntry : classes.values()) {
			populate(classEntry, classEntry, classes);
		}

		return classes;
	}

	private static void populate(ClassEntry instance, ClassEntry current, Map<String, ClassEntry> classes) {
		for (String superClassName : current.superClasses.toArray(String[]::new)) {
			boolean registered = instance == current || instance.registerSuperClass(superClassName);

			if (registered) {
				ClassEntry superEntry = classes.get(superClassName);

				if (superEntry != null) {
					for (MethodInstance superMethodInstance : superEntry.methods.values()) {
						instance.mergeMethod(superMethodInstance);
					}

					populate(instance, superEntry, classes);
				}
			}
		}
	}

	private static class Visitor extends ClassVisitor {
		private final Map<String, ClassEntry> classes;
		private ClassEntry lastClass = null;

		Visitor(int api, Map<String, ClassEntry> classes) {
			super(api);
			this.classes = classes;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			if (this.classes.containsKey(name)) {
				throw new IllegalArgumentException("Duplicate class %s".formatted(name));
			}

			ClassEntry entry = new ClassEntry(name);
			this.lastClass = entry;
			this.classes.put(name, entry);
			entry.registerSuperClass(superName);

			for (String iface : interfaces) {
				entry.registerSuperClass(iface);
			}
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if ((access & Opcodes.ACC_STATIC) == 0 && (access & Opcodes.ACC_PRIVATE) == 0) {
				this.lastClass.registerMethod(new MethodKey(this.lastClass.className, name, descriptor));
			}

			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	private static class ClassEntry {
		private final String className;
		private final Set<String> superClasses = new HashSet<>();
		private final Map<MethodKey, MethodInstance> methods = new HashMap<>();

		private ClassEntry(String className) {
			this.className = className;
		}

		public boolean registerSuperClass(String name) {
			return this.superClasses.add(name);
		}

		public void registerMethod(MethodKey key) {
			MethodInstance instance = new MethodInstance(key.name(), key.descriptor());
			this.methods.put(key, instance);
			instance.registerInstance(key);
		}

		public void mergeMethod(MethodInstance other) {
			MethodKey key = new MethodKey(this.className, other.name, other.descriptor);
			MethodInstance instance = this.methods.get(key);

			if (instance != null) {
				other.mergeWith(instance);
				this.methods.put(key, other);
			}
		}
	}

	private static class MethodInstance {
		private final String name;
		private final String descriptor;
		private final Set<MethodKey> knownInstances = new HashSet<>();

		private MethodInstance(String name, String descriptor) {
			this.name = name;
			this.descriptor = descriptor;
		}

		public boolean registerInstance(MethodKey key) {
			return this.knownInstances.add(key);
		}

		public void mergeWith(MethodInstance other) {
			if (!this.name.equals(other.name) || !this.descriptor.equals(other.descriptor)) {
				throw new IllegalArgumentException("Mismatch method signature");
			}

			this.knownInstances.addAll(other.knownInstances);
		}
	}

	private record MethodKey(String className, String name, String descriptor) {
	}
}
