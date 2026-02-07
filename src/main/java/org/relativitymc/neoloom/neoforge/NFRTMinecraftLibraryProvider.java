package org.relativitymc.neoloom.neoforge;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.relativitymc.neoloom.neoforge.meta.MinecraftDistribution;
import org.relativitymc.neoloom.neoforge.meta.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class NFRTMinecraftLibraryProvider extends MinecraftLibraryProvider {
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

		this.neoForge = project.getDependencyFactory().create("net.neoforged:neoforge:" + minecraftProvider.neoForgeVersion());
		this.neoForgeNotation = "net.neoforged:neoforge:" + minecraftProvider.neoForgeVersion() + ":userdev";

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

		Configuration runtimeClasspath = this.project.getConfigurations().detachedConfiguration(this.gameLibrariesDependency);
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

		Configuration configuration = this.project.getConfigurations().detachedConfiguration(this.gameLibrariesDependency, this.neoForge);
		configuration.attributes(attributes -> {
			attributes.attribute(MinecraftDistribution.ATTRIBUTE, project.getObjects().named(MinecraftDistribution.ATTRIBUTE.getType(), MinecraftDistribution.CLIENT));
			attributes.attribute(OperatingSystem.ATTRIBUTE, project.getObjects().named(OperatingSystem.ATTRIBUTE.getType(), OperatingSystem.getCurrent()));
		});
		ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
		for (ResolvedArtifact artifact : resolvedConfiguration.getResolvedArtifacts()) {
			final ModuleVersionIdentifier id = artifact.getModuleVersion().getId();

			// TODO transform FML

			this.applyDependencyBoth(new Library(id.getGroup(), id.getName(), id.getVersion(), artifact.getClassifier(), Library.Target.COMPILE));
		}

		if (extension.isCollectingDependencyVerificationMetadata()) {
			resolveAllLibraries();
		}
	}

	private void applyDependencyBoth(Library library) {
		this.applyClientLibrary(library);
		this.applyServerLibrary(library);
	}
}
