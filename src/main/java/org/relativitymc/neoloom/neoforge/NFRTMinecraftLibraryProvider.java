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

package org.relativitymc.neoloom.neoforge;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library;

import org.relativitymc.neoloom.neoforge.meta.MinecraftDistribution;
import org.relativitymc.neoloom.neoforge.meta.OperatingSystem;

public class NFRTMinecraftLibraryProvider extends MinecraftLibraryProvider {
	private static final String FML_LOADER_GROUP = "net.minecraftforge";
	private static final String FML_LOADER_NAME = "fmlloader";
	private static final String FANCYML_LOADER_GROUP = "net.neoforged.fancymodloader";
	private static final String FANCYML_LOADER_NAME = "loader";

	private final Project project;
	private final MinecraftProvider minecraftProvider;

	private final ModuleDependency neoForge;
	private final String neoForgeNotation;

	private final ModuleDependency runTypesConfigDependency;
	private final ModuleDependency modulePathDependency;
	private final ModuleDependency gameLibrariesDependency;

	public NFRTMinecraftLibraryProvider(NFRTMergedMinecraftProvider minecraftProvider, Project project) {
		super(minecraftProvider, project);
		this.project = project;
		this.minecraftProvider = minecraftProvider;

		this.neoForge = project.getDependencyFactory().create(minecraftProvider.neoForgeNotation());
		this.neoForgeNotation = minecraftProvider.neoForgeNotation() + ":userdev";

		this.runTypesConfigDependency = neoForge.copy().capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-config"));
		this.modulePathDependency = neoForge.copy().capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-module-path"))
				.exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
		this.gameLibrariesDependency = neoForge.copy().capabilities(c -> c.requireCapability("net.neoforged:neoforge-dependencies"));
	}

	public List<Configuration> getNFRTDeps() {
		List<Configuration> list = new ArrayList<>();

		Configuration modDevBundle = this.project.getConfigurations().detachedConfiguration(
				this.neoForge.copy().capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-bundle"))
		);
		modDevBundle.setCanBeConsumed(false);
		modDevBundle.setCanBeResolved(true);
		list.add(modDevBundle);

		Configuration neoforgeDep = this.project.getConfigurations().detachedConfiguration(this.neoForge);
		neoforgeDep.setCanBeConsumed(false);
		neoforgeDep.setCanBeResolved(true);
		neoforgeDep.attributes(attributes -> {
			attributes.attribute(Category.CATEGORY_ATTRIBUTE, this.project.getObjects().named(Category.CATEGORY_ATTRIBUTE.getType(), Category.DOCUMENTATION));
			attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, this.project.getObjects().named(DocsType.DOCS_TYPE_ATTRIBUTE.getType(), DocsType.SOURCES));
		});
		list.add(neoforgeDep);

		Configuration compileClasspath = this.project.getConfigurations().detachedConfiguration(this.gameLibrariesDependency);
		compileClasspath.setCanBeConsumed(false);
		compileClasspath.setCanBeResolved(true);
		compileClasspath.attributes(attributes -> {
			attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.USAGE_ATTRIBUTE.getType(), Usage.JAVA_API));
			attributes.attribute(MinecraftDistribution.ATTRIBUTE, project.getObjects().named(MinecraftDistribution.ATTRIBUTE.getType(), MinecraftDistribution.CLIENT));
			attributes.attribute(OperatingSystem.ATTRIBUTE, project.getObjects().named(OperatingSystem.ATTRIBUTE.getType(), OperatingSystem.getCurrent()));
		});
		list.add(compileClasspath);

		Configuration runtimeClasspath = this.project.getConfigurations().detachedConfiguration(this.neoForge, this.gameLibrariesDependency);
		runtimeClasspath.setCanBeConsumed(false);
		runtimeClasspath.setCanBeResolved(true);
		runtimeClasspath.attributes(attributes -> {
			attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.USAGE_ATTRIBUTE.getType(), Usage.JAVA_RUNTIME));
			attributes.attribute(MinecraftDistribution.ATTRIBUTE, project.getObjects().named(MinecraftDistribution.ATTRIBUTE.getType(), MinecraftDistribution.CLIENT));
			attributes.attribute(OperatingSystem.ATTRIBUTE, project.getObjects().named(OperatingSystem.ATTRIBUTE.getType(), OperatingSystem.getCurrent()));
		});
		list.add(runtimeClasspath);

		return list;
	}

	public Path resolveUniversalJar() {
		Configuration neoforgeDep = this.project.getConfigurations().detachedConfiguration(this.neoForge);
		Set<File> resolve = neoforgeDep.resolve();

		if (resolve.size() != 1) {
			throw new GradleException("NeoForge universal jar resolved to multiple jars: " + Arrays.toString(resolve.toArray()));
		}

		return resolve.iterator().next().toPath();
	}

	public Path resolveFMLJar() {
		Configuration gameLib = this.project.getConfigurations().detachedConfiguration(this.gameLibrariesDependency);
		gameLib.attributes(attributes -> {
			attributes.attribute(MinecraftDistribution.ATTRIBUTE, project.getObjects().named(MinecraftDistribution.ATTRIBUTE.getType(), MinecraftDistribution.CLIENT));
			attributes.attribute(OperatingSystem.ATTRIBUTE, project.getObjects().named(OperatingSystem.ATTRIBUTE.getType(), OperatingSystem.getCurrent()));
		});
		for (ResolvedArtifact artifact : gameLib.getResolvedConfiguration().getResolvedArtifacts()) {
			ModuleVersionIdentifier id = artifact.getModuleVersion().getId();

			if (isFML(id)) {
				return artifact.getFile().toPath();
			}
		}
		throw new GradleException("No FML in neoforge dependencies");
	}

	private static boolean isFML(ModuleVersionIdentifier id) {
		String group = id.getGroup();
		String name = id.getName();
		return isFML(group, name);
	}

	private static boolean isFML(String group, String name) {
		return (FML_LOADER_GROUP.equals(group) && FML_LOADER_NAME.equals(name))
				|| (FANCYML_LOADER_GROUP.equals(group) && FANCYML_LOADER_NAME.equals(name));
	}

	public void collectArtifactManifest(Properties properties) {
		for (Configuration configuration : this.getNFRTDeps()) {
			for (ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
				ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
				StringBuilder mavenNotation = new StringBuilder().append(id.getGroup()).append(":").append(id.getName()).append(":").append(id.getVersion());

				if (artifact.getClassifier() != null) {
					mavenNotation.append(":").append(artifact.getClassifier());
				}

				if (artifact.getExtension() != null) {
					mavenNotation.append("@").append(artifact.getExtension());
				}

				properties.put(mavenNotation.toString(), artifact.getFile().getAbsolutePath());
			}
		}
	}

	@Override
	public void provide() {
		final LoomGradleExtension extension = LoomGradleExtension.get(project);
		final MinecraftJarConfiguration jarConfiguration = extension.getMinecraftJarConfiguration().get();

		// TODO transform FML

		resolveGameLibraries(Library.Target.COMPILE, MinecraftDistribution.CLIENT, this.gameLibrariesDependency); // note: add this.neoForge if final jar doesnt have it
		resolveGameLibraries(Library.Target.COMPILE, MinecraftDistribution.SERVER, this.gameLibrariesDependency);
		resolveGameLibraries(Library.Target.RUNTIME, MinecraftDistribution.CLIENT, this.gameLibrariesDependency, this.modulePathDependency);
		resolveGameLibraries(Library.Target.RUNTIME, MinecraftDistribution.SERVER, this.gameLibrariesDependency, this.modulePathDependency);

		if (extension.isCollectingDependencyVerificationMetadata()) {
			resolveAllLibraries();
		}
	}

	private void resolveGameLibraries(Library.Target target, String distribution, ModuleDependency... dependencies) {
		String usage = switch (target) {
		case RUNTIME -> Usage.JAVA_RUNTIME;
		case COMPILE -> Usage.JAVA_API;
		default -> throw new UnsupportedOperationException();
		};
		Configuration configuration = this.project.getConfigurations().detachedConfiguration(dependencies);
		configuration.attributes(attributes -> {
			attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.USAGE_ATTRIBUTE.getType(), usage));
			attributes.attribute(MinecraftDistribution.ATTRIBUTE, project.getObjects().named(MinecraftDistribution.ATTRIBUTE.getType(), distribution));
			attributes.attribute(OperatingSystem.ATTRIBUTE, project.getObjects().named(OperatingSystem.ATTRIBUTE.getType(), OperatingSystem.getCurrent()));
		});
		ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
		List<Library> list = resolvedConfiguration.getResolvedArtifacts().stream()
				.map(artifact -> {
					final ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
					return new Library(id.getGroup(), id.getName(), id.getVersion(), artifact.getClassifier(), Library.Target.COMPILE);
				})
				.filter(library -> !isFML(library.group(), library.name()))
				.toList();
		List<Library> processed = this.processLibraries(list);

		if (distribution.equals(MinecraftDistribution.CLIENT)) {
			processed.forEach(this::applyClientLibrary);
		} else if (distribution.equals(MinecraftDistribution.SERVER)) {
			processed.forEach(this::applyServerLibrary);
		} else {
			throw new UnsupportedOperationException();
		}
	}
}
