/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package org.relativitymc.neoloom.neoforge.modmeta;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.fmj.FabricModJson;
import net.fabricmc.loom.util.fmj.FabricModJsonSource;
import net.fabricmc.loom.util.fmj.ModMetadataFabricModJson;

import dev.architectury.at.AccessTransform;
import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.io.AccessTransformFormats;
import dev.architectury.loom.metadata.ModMetadata;

public class NFGeneratedMetaDependency {
	private static final String MOD_ID_PREFIX_AT = "neo_loom_generated_metadata_accesstransformer:";
	private static final String MOD_ID_PREFIX_IJ = "neo_loom_generated_metadata_interfaceinjection:";
	private static final String AW_PATH = "generated.accesswidener";
	private static final String AT_KEY = "at-v0-";
	private static final String IJ_KEY = "ij-v0-";

	public static List<FabricModJson> create(Project project) {
		List<FabricModJson> result = new ArrayList<>();
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		MappingsNamespace productionNamespace = extension.getProductionNamespaceEnum().get();

		File cacheDir = new File(extension.getFiles().getRootProjectPersistentCache(), "neo-loom-generated-ct");
		cacheDir.mkdirs();

		Function<String, @Nullable ClassNode> classesCache = getClassesCache(project);

		for (ResolvedArtifact artifact : project.getConfigurations().getByName(Constants.Configurations.NEOFORGE_ACCESS_TRANSFORMERS).getResolvedConfiguration().getResolvedArtifacts()) {
			String mavenNotation = getMavenNotation(artifact);

			byte[] atBytes;

			try {
				atBytes = Files.readAllBytes(artifact.getFile().toPath());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			byte[] cachedCt = tryReadFromCache(cacheDir, AT_KEY, atBytes);

			if (cachedCt != null) {
				result.add(new ModMetadataFabricModJson(
						new MetaModMetadata(MOD_ID_PREFIX_AT + mavenNotation),
						new MetaModSource(cachedCt)
				));
			} else {
				ClassTweakerWriter classTweaker = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
				classTweaker.visitHeader(productionNamespace.toString());
				AccessTransformSet set;

				try (var reader = new InputStreamReader(new ByteArrayInputStream(atBytes))) {
					set = AccessTransformFormats.FML.read(reader);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				if (!set.getClasses().isEmpty()) {
					convertAt2Aw(project, set, classTweaker, mavenNotation, classesCache);
					byte[] awContent = classTweaker.getOutputAsString().getBytes(StandardCharsets.UTF_8);
					result.add(new ModMetadataFabricModJson(
							new MetaModMetadata(MOD_ID_PREFIX_AT + mavenNotation),
							new MetaModSource(awContent)
					));
					storeCache(cacheDir, AT_KEY, atBytes, awContent);
				}
			}
		}

		for (ResolvedArtifact artifact : project.getConfigurations().getByName(Constants.Configurations.NEOFORGE_INTERFACE_INJECTIONS).getResolvedConfiguration().getResolvedArtifacts()) {
			String mavenNotation = getMavenNotation(artifact);

			byte[] ijBytes;

			try {
				ijBytes = Files.readAllBytes(artifact.getFile().toPath());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			byte[] cachedCt = tryReadFromCache(cacheDir, IJ_KEY, ijBytes);

			ClassTweakerWriter classTweaker = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
			classTweaker.visitHeader(productionNamespace.toString());

			JsonObject json;

			try (var reader = new InputStreamReader(new ByteArrayInputStream(ijBytes))) {
				json = LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (cachedCt != null) {
				result.add(new ModMetadataFabricModJson(
						new MetaModMetadata(MOD_ID_PREFIX_IJ + mavenNotation),
						new MetaModSource(cachedCt)
				));
			} else {
				boolean hasInjections = false;

				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					String className = entry.getKey();
					JsonArray elements = entry.getValue().getAsJsonArray();

					for (JsonElement element : elements) {
						String iface = element.getAsString();
						classTweaker.visitInjectedInterface(className, NeoForgeInterfaceInjectionUtils.neoForge2ClassTweaker(iface), true);
						hasInjections = true;
					}
				}

				if (hasInjections) {
					byte[] awContent = classTweaker.getOutputAsString().getBytes(StandardCharsets.UTF_8);
					result.add(new ModMetadataFabricModJson(
							new MetaModMetadata(MOD_ID_PREFIX_IJ + mavenNotation),
							new MetaModSource(awContent)
					));
					storeCache(cacheDir, IJ_KEY, ijBytes, awContent);
				}
			}
		}

		return List.copyOf(result);
	}

	private static Function<String, @Nullable ClassNode> getClassesCache(Project project) {
		LinkedHashMap<String, ClassNode> cache = new LinkedHashMap<>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, ClassNode> eldest) {
				return this.size() > 256;
			}
		};
		Function<String, ClassNode> readClass0 = readClass0(project);
		return className -> cache.computeIfAbsent(className, readClass0);
	}

	private static Function<String, ClassNode> readClass0(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		MappingsNamespace productionNamespace = extension.getProductionNamespaceEnum().get();
		List<Path> minecraftJars = extension.getMinecraftJars(productionNamespace);
		return className -> {
			for (Path minecraftJar : minecraftJars) {
				byte[] unpacked = null;

				try {
					unpacked = ZipUtils.unpack(minecraftJar, className + ".class");
				} catch (NoSuchFileException e) {
					// fallthrough
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				if (unpacked == null) {
					return null;
				} else {
					ClassNode classNode = new ClassNode();
					new ClassReader(unpacked).accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					return classNode;
				}
			}

			return null;
		};
	}

	private static byte @Nullable [] tryReadFromCache(File cacheDir, String key, byte[] input) {
		String hash = getHash(key, input);
		File cachedFile = new File(cacheDir, key + hash);
		File cachedHashFile = new File(cacheDir, key + hash + ".sha256");

		if (cachedFile.isFile() && cachedHashFile.isFile()) {
			try {
				byte[] cachedContent = Files.readAllBytes(cachedFile.toPath());
				String expectedCachedHash = Files.readString(cachedHashFile.toPath(), StandardCharsets.UTF_8);
				String foundCachedHash = getHash("content", cachedContent);

				if (expectedCachedHash.equals(foundCachedHash)) {
					return cachedContent;
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		return null;
	}

	private static void storeCache(File cacheDir, String key, byte[] input, byte[] output) {
		String hash = getHash(key, input);
		File cachedFile = new File(cacheDir, key + hash);
		File cachedHashFile = new File(cacheDir, key + hash + ".sha256");
		String contentHash = getHash("content", output);

		try {
			Files.write(cachedFile.toPath(), output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			Files.write(cachedHashFile.toPath(), contentHash.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String getHash(String key, byte[] input) {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		digest.update(key.getBytes(StandardCharsets.UTF_8));
		digest.update(input);
		digest.update(key.getBytes(StandardCharsets.UTF_8));
		return bytesToHex(digest.digest());
	}

	private static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);

		for (byte b : hash) {
			String hex = Integer.toHexString(b & 0xff);

			if (hex.length() == 1) {
				hexString.append('0');
			}

			hexString.append(hex);
		}

		return hexString.toString();
	}

	private static @NonNull String getMavenNotation(ResolvedArtifact artifact) {
		ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
		StringBuilder mavenNotationBuilder = new StringBuilder().append(id.getGroup()).append(":").append(id.getName()).append(":").append(id.getVersion());

		if (artifact.getClassifier() != null) {
			mavenNotationBuilder.append(":").append(artifact.getClassifier());
		}

		if (artifact.getExtension() != null) {
			mavenNotationBuilder.append("@").append(artifact.getExtension());
		}

		String mavenNotation = mavenNotationBuilder.toString();
		return mavenNotation;
	}

	private static void convertAt2Aw(Project project, AccessTransformSet set, ClassTweakerVisitor classTweaker, String mavenNotation, Function<String, @Nullable ClassNode> classesCache) {
		for (Map.Entry<String, AccessTransformSet.Class> classEntry : set.getClasses().entrySet()) {
			String className = classEntry.getKey();
			AccessTransformSet.Class classValue = classEntry.getValue();

			AccessWidenerVisitor visitor = Objects.requireNonNull(classTweaker.visitAccessWidener(className));

			ClassNode classNode = classesCache.apply(className);

			if (classNode == null) {
				project.getLogger().warn("At2Aw: {}: could not find class {}", mavenNotation, className);
			}

			if (!classValue.get().isEmpty()) {
				switch (classValue.get().getAccess()) {
				case NONE -> {
				}
				case PROTECTED -> visitor.visitClass(AccessWidenerVisitor.AccessType.EXTENDABLE, true);
				case PUBLIC -> visitor.visitClass(AccessWidenerVisitor.AccessType.ACCESSIBLE, true);
				default -> project.getLogger().warn("At2Aw: {}: unimplemented class access for {}: {}", mavenNotation, className, classValue.get().getAccess());
				}

				switch (classValue.get().getFinal()) {
				case NONE -> {
				}
				case REMOVE -> visitor.visitClass(AccessWidenerVisitor.AccessType.EXTENDABLE, true);
				default -> project.getLogger().warn("At2Aw: {}: unimplemented class final for {}: {}", mavenNotation, className, classValue.get().getFinal());
				}
			}

			if (!classValue.allFields().isEmpty()) {
				project.getLogger().warn("At2Aw: {}: unimplemented wildcard field widener for {}", mavenNotation, className);
			}

			if (!classValue.allMethods().isEmpty()) {
				project.getLogger().warn("At2Aw: {}: unimplemented wildcard method widener for {}", mavenNotation, className);
			}

			for (Map.Entry<String, AccessTransform> fieldEntry : classValue.getFields().entrySet()) {
				String fieldName = fieldEntry.getKey();
				AccessTransform transform = fieldEntry.getValue();

				if (transform.isEmpty()) {
					continue;
				}

				if (classNode != null) {
					FieldNode fieldNode1 = classNode.fields.stream()
							.filter(fieldNode -> fieldName.equals(fieldNode.name))
							.findAny().orElse(null);

					if (fieldNode1 != null) {
						switch (transform.getAccess()) {
						case NONE -> {
						}
						case PROTECTED, PUBLIC -> visitor.visitField(fieldName, fieldNode1.desc, AccessWidenerVisitor.AccessType.ACCESSIBLE, true);
						default -> project.getLogger().warn("At2Aw: {}: unimplemented field access for {} {}: {}", mavenNotation, className, fieldName, classValue.get().getAccess());
						}

						switch (transform.getFinal()) {
						case NONE -> {
						}
						case REMOVE -> visitor.visitField(fieldName, fieldNode1.desc, AccessWidenerVisitor.AccessType.MUTABLE, true);
						default -> project.getLogger().warn("At2Aw: {}: unimplemented field final for {} {}: {}", mavenNotation, className, fieldName, classValue.get().getAccess());
						}
					} else {
						project.getLogger().warn("At2Aw: {}: could not find field of field widener for {} {} {}", mavenNotation, className, fieldName, transform);
					}
				} else {
					project.getLogger().warn("At2Aw: {}: could not find class of field widener for {} {} {}", mavenNotation, className, fieldName, transform);
				}
			}

			for (Map.Entry<MethodSignature, AccessTransform> methodEntry : classValue.getMethods().entrySet()) {
				MethodSignature methodSignature = methodEntry.getKey();
				AccessTransform transform = methodEntry.getValue();

				if (!transform.isEmpty()) {
					switch (transform.getAccess()) {
					case NONE -> {
					}
					case PROTECTED -> visitor.visitMethod(methodSignature.getName(), methodSignature.getDescriptor().toString(), AccessWidenerVisitor.AccessType.EXTENDABLE, true);
					case PUBLIC -> visitor.visitMethod(methodSignature.getName(), methodSignature.getDescriptor().toString(), AccessWidenerVisitor.AccessType.ACCESSIBLE, true);
					default -> project.getLogger().warn("At2Aw: {}: unimplemented method access for {} {}: {}", mavenNotation, className, methodSignature, transform.getAccess());
					}

					switch (transform.getFinal()) {
					case NONE -> {
					}
					case REMOVE -> visitor.visitMethod(methodSignature.getName(), methodSignature.getDescriptor().toString(), AccessWidenerVisitor.AccessType.EXTENDABLE, true);
					default -> project.getLogger().warn("At2Aw: {}: unimplemented method final for {} {}: {}", mavenNotation, className, methodSignature, transform.getFinal());
					}
				}
			}
		}
	}

	public record MetaModMetadata(String modId) implements ModMetadata {
		@Override
		public Set<String> getIds() {
			return Set.of(modId);
		}

		@Override
		public Set<String> getAccessWideners() {
			return Set.of(AW_PATH);
		}

		@Override
		public Set<String> getAccessTransformers() {
			return Set.of();
		}

		@Override
		public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
			return List.of();
		}

		@Override
		public String getFileName() {
			return modId;
		}

		@Override
		public List<String> getMixinConfigs() {
			return List.of();
		}

		@Override
		public List<String> getNeoEnumExtensions() {
			return List.of();
		}
	}

	private record MetaModSource(byte[] awContent) implements FabricModJsonSource {
		@Override
		public byte[] read(String path) throws IOException {
			if (!AW_PATH.equals(path)) {
				throw new FileNotFoundException(path);
			} else {
				return awContent.clone();
			}
		}
	}
}
