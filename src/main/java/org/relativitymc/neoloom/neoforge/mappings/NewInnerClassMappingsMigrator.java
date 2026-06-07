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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import org.relativitymc.neoloom.neoforge.NFRTMergedMinecraftProvider;

/**
 * With some forge patches, new inner classes can be introduced.
 * This migrator will attempt to generate correct mappings for them.
 */
public final class NewInnerClassMappingsMigrator implements MappingsMigrator {
	private NewMappings newClassMappings;

	@Override
	public long setup(Project project, NFRTMergedMinecraftProvider minecraftProvider, Path cache, Path rawMappings) throws IOException {
		Path cacheFile = cache.resolve("new-inner-class-migrator.json");

		if (!minecraftProvider.refreshDeps() && Files.exists(cacheFile)) {
			try (BufferedReader reader = Files.newBufferedReader(cacheFile)) {
				NewMappings data = new Gson().fromJson(reader, new TypeToken<NewMappings>() {
				});
				newClassMappings = data.deepCopy();
			}
		} else {
			Files.deleteIfExists(cacheFile);
			LoomGradleExtension extension = LoomGradleExtension.get(project);

			newClassMappings = prepareCache(project.getLogger(), rawMappings, minecraftProvider.getFullClasspath()).sortNames();

			Files.writeString(cacheFile, new Gson().toJson(newClassMappings), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		return newClassMappings.hashCode();
	}

	@Override
	public void migrate(Project project, List<MappingsEntry> entries) throws IOException {
		for (MappingsEntry entry : entries) {
			MemoryMappingTree mappings = new MemoryMappingTree();

			try (BufferedReader reader = Files.newBufferedReader(entry.path())) {
				MappingReader.read(reader, mappings);
			}

			mappings.visitHeader();
			String[] namespaces = newClassMappings.namespaces;
			mappings.visitNamespaces(namespaces[0], List.of(Arrays.copyOfRange(namespaces, 1, namespaces.length)));

			for (String[] newNames : newClassMappings.newNames) {
				if (mappings.visitClass(newNames[0])) {
					for (int i = 1, dstNamesLength = namespaces.length; i < dstNamesLength; i++) {
						String dstName = newNames[i];
						int nsIdx = i - 1;
						mappings.visitDstName(MappedElementKind.CLASS, nsIdx, dstName);
					}

					mappings.visitElementContent(MappedElementKind.CLASS);
				}
			}

			mappings.visitEnd();

			try (Writer writer = Files.newBufferedWriter(entry.path(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				mappings.accept(new Tiny2FileWriter(writer, false));
			}
		}
	}

	private NewMappings prepareCache(Logger logger, Path rawMappings, List<Path> jars) throws IOException {
		MemoryMappingTree mappings = new MemoryMappingTree();
		String patchedNs = MappingsNamespace.OFFICIAL.toString();

		try (BufferedReader reader = Files.newBufferedReader(rawMappings)) {
			MappingReader.read(reader, new MappingSourceNsSwitch(mappings, patchedNs));
		}

		int patchedNsIndex = mappings.getNamespaceId(patchedNs);

		List<String> classNames = jars.stream()
				.map(NewInnerClassMappingsMigrator::readClassNames)
				.flatMap(Collection::stream)
				.sorted()
				.distinct()
				.toList();

		int namespaceCount = mappings.getDstNamespaces().size();
		Set<String>[] mappedNames = new Set[namespaceCount];

		for (int i = 0; i < mappedNames.length; i++) {
			int nsIndex = i;
			Set<String> mappedName = mappings.getClasses().stream()
					.map(classMapping -> Objects.requireNonNull(classMapping.getName(nsIndex)))
					.collect(Collectors.toCollection(HashSet::new));
			mappedNames[i] = mappedName;
		}

		List<String[]> newClassNames = new ArrayList<>();

		for (String className : classNames) {
			if (mappings.getClass(className) == null) {
				String parentName = className.substring(0, className.indexOf('$'));
				String childName = className.substring(className.indexOf('$') + 1);
				MappingTree.ClassMapping parentMapping = mappings.getClass(parentName);

				if (parentMapping != null) {
					String[] mapped = new String[namespaceCount];
					boolean hasNewMapping = false;

					for (int nsIndex = 0; nsIndex < namespaceCount; nsIndex++) {
						String remappedParentName = parentMapping.getName(nsIndex);

						if (remappedParentName == null) {
							throw new IllegalStateException(String.format("Class %s have incomplete mapping for namespace %s", parentName, mappings.getDstNamespaces().get(nsIndex)));
						}

						String remappedName = remappedParentName + "$" + childName;

						if (mappedNames[nsIndex].contains(remappedName)) {
							// https://github.com/MinecraftForge/MinecraftForge/blob/b027a92dd287d6810a9fdae4d4b1e1432d7dc9cc/patches/minecraft/net/minecraft/Util.java.patch#L8
							mapped[nsIndex] = remappedName + "_UNBREAK";
						} else {
							mapped[nsIndex] = remappedName;
						}

						hasNewMapping |= !className.equals(mapped[nsIndex]);
					}

					if (hasNewMapping) {
						// add to used names
						for (int nsIndex = 0; nsIndex < namespaceCount; nsIndex++) {
							mappedNames[nsIndex].add(mapped[nsIndex]);
						}

						String[] dst = new String[namespaceCount + 1];
						dst[0] = className;
						System.arraycopy(mapped, 0, dst, 1, namespaceCount);
						newClassNames.add(dst);
					}
				}
			}
		}

		newClassNames.sort(Comparator.comparing(strings -> strings[0]));

		String[] encNs = new String[namespaceCount + 1];
		encNs[0] = Objects.requireNonNull(mappings.getSrcNamespace());
		System.arraycopy(mappings.getDstNamespaces().toArray(String[]::new), 0, encNs, 1, namespaceCount);
		return new NewMappings(encNs, newClassNames);
	}

	public static Set<String> readClassNames(Path jar) {
		Set<String> set = new HashSet<>();

		try (FileSystemUtil.Delegate system = FileSystemUtil.getJarFileSystem(jar, false)) {
			Iterator<Path> iterator = Files.walk(system.get().getPath("/")).iterator();

			while (iterator.hasNext()) {
				Path path = iterator.next();
				String name = path.toString();
				if (name.startsWith("/")) name = name.substring(1);

				if (!Files.isDirectory(path) && name.contains("$") && name.endsWith(".class")) {
					String className = name.substring(0, name.length() - 6);
					set.add(className);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return set;
	}

	/**
	 * This includes all namespaces, src namespaces at index 0.
	 */
	public record NewMappings(String[] namespaces, List<String[]> newNames) {
		public NewMappings deepCopy() {
			return new NewMappings(namespaces.clone(), newNames.stream().map(String[]::clone).collect(Collectors.toCollection(ArrayList::new)));
		}

		public NewMappings sortNames() {
			return new NewMappings(namespaces.clone(), newNames.stream().map(String[]::clone).sorted(Comparator.comparing(strings -> strings[0])).collect(Collectors.toCollection(ArrayList::new)));
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass()) return false;
			NewMappings that = (NewMappings) o;
			if (!Objects.deepEquals(namespaces, that.namespaces)) return false;
			if (newNames.size() != that.newNames.size()) return false;

			for (int i = 0, newNamesSize = newNames.size(); i < newNamesSize; i++) {
				if (!Arrays.deepEquals(newNames.get(i), that.newNames.get(i))) return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			int result = 1;
			result = 31 * result + Objects.hashCode(Arrays.hashCode(namespaces));

			for (String[] newName : newNames) {
				result = 31 * result + Arrays.hashCode(newName);
			}

			return result;
		}
	}
}
