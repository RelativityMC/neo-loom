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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.util.Constants;
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

	public static List<FabricModJson> create(Project project) {
		List<FabricModJson> result = new ArrayList<>();
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		MappingsNamespace productionNamespace = extension.getProductionNamespaceEnum().get();

		for (ResolvedArtifact artifact : project.getConfigurations().getByName(Constants.Configurations.NEOFORGE_ACCESS_TRANSFORMERS).getResolvedConfiguration().getResolvedArtifacts()) {
			String mavenNotation = getMavenNotation(artifact);

			ClassTweakerWriter classTweaker = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
			classTweaker.visitHeader(productionNamespace.toString());
			AccessTransformSet set;

			try (var reader = new FileReader(artifact.getFile())) {
				set = AccessTransformFormats.FML.read(reader);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (!set.getClasses().isEmpty()) {
				convertAt2Aw(project, set, classTweaker, mavenNotation);
				result.add(new ModMetadataFabricModJson(
						new MetaModMetadata(MOD_ID_PREFIX_AT + mavenNotation),
						new MetaModSource(classTweaker.getOutputAsString())
				));
			}
		}

		for (ResolvedArtifact artifact : project.getConfigurations().getByName(Constants.Configurations.NEOFORGE_INTERFACE_INJECTIONS).getResolvedConfiguration().getResolvedArtifacts()) {
			String mavenNotation = getMavenNotation(artifact);

			ClassTweakerWriter classTweaker = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
			classTweaker.visitHeader(productionNamespace.toString());

			JsonObject json;

			try (var reader = new FileReader(artifact.getFile())) {
				json = LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			boolean hasInjections = false;

			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				String className = entry.getKey();
				JsonArray elements = entry.getValue().getAsJsonArray();

				for (JsonElement element : elements) {
					String iface = element.getAsString();
					classTweaker.visitInjectedInterface(className, iface, true);
					hasInjections = true;
				}
			}

			if (hasInjections) {
				result.add(new ModMetadataFabricModJson(
						new MetaModMetadata(MOD_ID_PREFIX_IJ + mavenNotation),
						new MetaModSource(classTweaker.getOutputAsString())
				));
			}
		}

		return List.copyOf(result);
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

	private static void convertAt2Aw(Project project, AccessTransformSet set, ClassTweakerVisitor classTweaker, String mavenNotation) {
		for (Map.Entry<String, AccessTransformSet.Class> classEntry : set.getClasses().entrySet()) {
			String className = classEntry.getKey();
			AccessTransformSet.Class classValue = classEntry.getValue();

			AccessWidenerVisitor visitor = Objects.requireNonNull(classTweaker.visitAccessWidener(className));

			if (!classValue.get().isEmpty()) {
				switch (classValue.get().getAccess()) {
				case NONE -> {
				}
				case PROTECTED -> visitor.visitClass(AccessWidenerVisitor.AccessType.EXTENDABLE, true);
				case PUBLIC -> visitor.visitClass(AccessWidenerVisitor.AccessType.ACCESSIBLE, true);
				default -> project.getLogger().info("At2Aw: {}: unimplemented class access for {}: {}", mavenNotation, className, classValue.get().getAccess());
				}

				switch (classValue.get().getFinal()) {
				case NONE -> {
				}
				case REMOVE -> visitor.visitClass(AccessWidenerVisitor.AccessType.EXTENDABLE, true);
				default -> project.getLogger().info("At2Aw: {}: unimplemented class final for {}: {}", mavenNotation, className, classValue.get().getFinal());
				}
			}

			if (!classValue.allFields().isEmpty()) {
				project.getLogger().info("At2Aw: {}: unimplemented wildcard field widener for {}", mavenNotation, className);
			}

			if (!classValue.allMethods().isEmpty()) {
				project.getLogger().info("At2Aw: {}: unimplemented wildcard method widener for {}", mavenNotation, className);
			}

			for (Map.Entry<String, AccessTransform> fieldEntry : classValue.getFields().entrySet()) {
				String fieldName = fieldEntry.getKey();
				AccessTransform transform = fieldEntry.getValue();

				// impossible without classpath, because AW requires descriptor
				project.getLogger().info("At2Aw: {}: unimplemented field widener for {} {} {}", mavenNotation, className, fieldName, transform);
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
					default -> project.getLogger().info("At2Aw: {}: unimplemented method access for {} {}: {}", mavenNotation, className, methodSignature, transform.getAccess());
					}

					switch (transform.getFinal()) {
					case NONE -> {
					}
					case REMOVE -> visitor.visitMethod(methodSignature.getName(), methodSignature.getDescriptor().toString(), AccessWidenerVisitor.AccessType.EXTENDABLE, true);
					default -> project.getLogger().info("At2Aw: {}: unimplemented method final for {} {}: {}", mavenNotation, className, methodSignature, transform.getFinal());
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

	private record MetaModSource(String awContent) implements FabricModJsonSource {
		@Override
		public byte[] read(String path) throws IOException {
			if (!AW_PATH.equals(path)) {
				throw new FileNotFoundException(path);
			} else {
				return awContent.getBytes(StandardCharsets.UTF_8);
			}
		}
	}
}
