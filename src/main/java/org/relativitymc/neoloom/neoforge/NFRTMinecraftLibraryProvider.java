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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.LoomVersions;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarConfiguration;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library;

import org.relativitymc.neoloom.neoforge.meta.ForgeUserdevConfiguration;

public class NFRTMinecraftLibraryProvider extends MinecraftLibraryProvider {
	private static final String FML_LOADER_GROUP = "net.minecraftforge";
	private static final String FML_LOADER_NAME = "fmlloader";
	private static final String FANCYML_LOADER_GROUP = "net.neoforged.fancymodloader";
	private static final String FANCYML_LOADER_NAME = "loader";

	private final Project project;
	private final MinecraftProvider minecraftProvider;

	private final ModuleDependency forgeUserdev;
	private final ForgeUserdevConfiguration forgeUserdevConfiguration;
	private final boolean isFancyML;

	private boolean dependencyResolved = false;
	private ExternalModuleDependency fmlDependency;

	public NFRTMinecraftLibraryProvider(NFRTMergedMinecraftProvider minecraftProvider, Project project) {
		super(minecraftProvider, project);
		this.project = project;
		this.minecraftProvider = minecraftProvider;

		this.forgeUserdev = minecraftProvider.forgeUserdevDependency();
		this.forgeUserdevConfiguration = ForgeUserdevConfiguration.fromUserdevJar(project.getConfigurations().detachedConfiguration(minecraftProvider.forgeUserdevDependency()).getSingleFile());

		this.isFancyML = this.forgeUserdevConfiguration.librariesNotations().stream()
				.map(notation -> Library.fromMaven(notation, Library.Target.COMPILE))
				.anyMatch(library -> FANCYML_LOADER_GROUP.equals(library.group()) && FANCYML_LOADER_NAME.equals(library.name()));
	}

	public void ensureResolved() {
		if (this.dependencyResolved) return;

		super.provide(); // resolve vanilla libraries

		List<Library> libraries = this.forgeUserdevConfiguration.librariesNotations().stream()
				.map(notation -> Library.fromMaven(notation, Library.Target.COMPILE))
				.toList();
		List<Library> processedLibraries = this.processLibraries(libraries);

		Configuration loaderDepsConfig = this.project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES);

		for (Library library : processedLibraries) {
			ExternalModuleDependency externalModuleDependency = this.project.getDependencyFactory().create(library.group(), library.name(), library.version(), library.classifier(), null);
			externalModuleDependency.setTransitive(false);

			if (isFML(library.group(), library.name())) {
				this.fmlDependency = externalModuleDependency;
				continue; // FML applied as minecraft jar
			}

			this.applyClientLibrary(library);
			this.applyServerLibrary(library);
			loaderDepsConfig.getDependencies().add(externalModuleDependency);
		}

		Objects.requireNonNull(this.fmlDependency, "No FML dependency found");

		if (!LoomGradleExtension.get(this.project).disableObfuscation()) {
			Library unprotectLoader = Library.fromMaven(this.isFancyML ? LoomVersions.UNPROTECT_FANCYMODLOADER10.mavenNotation() : LoomVersions.UNPROTECT_MODLAUNCHER.mavenNotation(), Library.Target.RUNTIME);
			Library unprotect = Library.fromMaven(LoomVersions.UNPROTECT.mavenNotation(), Library.Target.RUNTIME);
			this.applyClientLibrary(unprotectLoader);
			this.applyServerLibrary(unprotectLoader);
			this.applyClientLibrary(unprotect);
			this.applyServerLibrary(unprotect);
		}

		// apply MinecraftForge devlaunch workarounds
		if (!this.isFancyML) {
			Library forgeBootstrapLoom = Library.fromMaven(LoomVersions.FORGE_BOOTSTRAP_LOOM.mavenNotation(), Library.Target.RUNTIME);
			this.applyClientLibrary(forgeBootstrapLoom);
			this.applyServerLibrary(forgeBootstrapLoom);

			// FML rejects this module due to package conflicts
			Configuration loomDevDeps = this.project.getConfigurations().getByName(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
			loomDevDeps.exclude(
					Map.of(
							"group", "net.fabricmc",
							"module", "fabric-log4j-util"
					)
			);
		}

		this.dependencyResolved = true;
	}

	public List<Configuration> getNFRTDeps() {
		this.ensureResolved();

		List<Configuration> list = new ArrayList<>();

		list.add(this.project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_CLIENT_COMPILE_LIBRARIES));
		list.add(this.project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_CLIENT_RUNTIME_LIBRARIES));
		list.add(this.project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_NATIVES));
		list.add(this.project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_SERVER_COMPILE_LIBRARIES));
		list.add(this.project.getConfigurations().getByName(Constants.Configurations.MINECRAFT_SERVER_RUNTIME_LIBRARIES));
		list.add(this.project.getConfigurations().getByName(LoomGradleExtension.get(this.project).disableObfuscation() ? Constants.Configurations.LOCAL_RUNTIME : "modLocalRuntime"));
		list.add(this.project.getConfigurations().detachedConfiguration(
				this.forgeUserdev,
				this.fmlDependency,
				this.project.getDependencyFactory().create(this.forgeUserdevConfiguration.mcpNotation()),
				this.project.getDependencyFactory().create(this.forgeUserdevConfiguration.binPatcherNotation()),
				this.project.getDependencyFactory().create(this.forgeUserdevConfiguration.universalJarNotation()),
				this.project.getDependencyFactory().create(this.forgeUserdevConfiguration.sourcesNotation())
		));

		try {
			list.add(this.project.getConfigurations().detachedConfiguration(this.resolveMCPDependencies().toArray(Dependency[]::new)));
		} catch (Throwable t) {
			this.project.getLogger().warn("Failed to resolve MCP dependencies", t);
		}

		try {
			list.add(this.project.getConfigurations().detachedConfiguration(this.resolveNFRTRuntimeDependencies().toArray(Dependency[]::new)));
		} catch (Throwable t) {
			this.project.getLogger().warn("Failed to resolve NFRT runtime dependencies", t);
		}

		return list;
	}

	public List<Dependency> resolveMCPDependencies() {
		File mcpZip = this.project.getConfigurations().detachedConfiguration(this.project.getDependencyFactory().create(this.forgeUserdevConfiguration.mcpNotation())).getSingleFile();

		JsonObject jsonObject;

		try {
			jsonObject = ZipUtils.unpackGson(mcpZip.toPath(), "config.json", JsonObject.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<String> list = new ArrayList<>();

		list.addAll(
				jsonObject.get("functions").getAsJsonObject().entrySet().stream()
						.flatMap(entry -> {
							JsonObject mcpFunction = entry.getValue().getAsJsonObject();

							if (mcpFunction.has("version")) {
								return Stream.of(mcpFunction.get("version").getAsString());
							} else if (mcpFunction.has("classpath")) {
								return mcpFunction.get("classpath").getAsJsonArray().asList().stream()
										.map(JsonElement::getAsString);
							} else {
								throw new UnsupportedOperationException("Neither version nor classpath is found in mcp function: " + mcpFunction);
							}
						})
						.toList()
		);
		list.addAll(
				jsonObject.get("libraries").getAsJsonObject().entrySet().stream()
						.flatMap(entry -> entry.getValue().getAsJsonArray().asList().stream())
						.map(element -> element.getAsString())
						.toList()
		);

		return list.stream()
				.sorted()
				.distinct()
				.map(notation -> (Dependency) this.project.getDependencyFactory().create(notation))
				.toList();
	}

	public List<Dependency> resolveNFRTRuntimeDependencies() {
		for (ResolvedArtifact artifact : this.project.getConfigurations().getByName(Constants.Configurations.NFRT_TOOL).getResolvedConfiguration().getResolvedArtifacts()) {
			if ("jar".equals(artifact.getExtension()) || "zip".equals(artifact.getExtension())) {
				Path zipPath = artifact.getFile().toPath();

				if (ZipUtils.contains(zipPath, "tools.properties")) {
					Properties versions = new Properties();

					try (var in = new ByteArrayInputStream(ZipUtils.unpack(zipPath, "tools.properties"))) {
						versions.load(in);
					} catch (IOException e) {
						this.project.getLogger().warn("Failed to unpack tools.properties from {}", zipPath.toAbsolutePath().toString(), e);
					}

					return versions.values().stream()
							.map(o -> (String) o)
							.sorted()
							.distinct()
							.map(notation -> (Dependency) this.project.getDependencyFactory().create(notation))
							.toList();
				}
			}
		}

		return List.of();
	}

	public Path resolveUniversalJar() {
		Configuration neoforgeDep = this.project.getConfigurations().detachedConfiguration(this.project.getDependencyFactory().create(this.forgeUserdevConfiguration.universalJarNotation()));
		Set<File> resolve = neoforgeDep.resolve();

		if (resolve.size() != 1) {
			throw new GradleException("NeoForge universal jar resolved to multiple jars: " + Arrays.toString(resolve.toArray()));
		}

		return resolve.iterator().next().toPath();
	}

	public Path resolveFMLJar() {
		this.ensureResolved();

		for (ResolvedArtifact artifact : this.project.getConfigurations().detachedConfiguration(this.fmlDependency).getResolvedConfiguration().getResolvedArtifacts()) {
			ModuleVersionIdentifier id = artifact.getModuleVersion().getId();

			if (isFML(id.getGroup(), id.getName())) {
				return artifact.getFile().toPath();
			}
		}

		throw new GradleException("No FML in neoforge dependencies");
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

		this.ensureResolved();

		// resolveAllLibraries done in ensureResolved()
	}

	public ForgeUserdevConfiguration getForgeUserdevConfiguration() {
		return this.forgeUserdevConfiguration;
	}

	public boolean isFancyML() {
		return this.isFancyML;
	}
}
