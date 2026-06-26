/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.task.launch;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.classpathgroups.ClasspathGroup;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.service.ClasspathGroupService;
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.loom.util.ZipUtils;

import dev.architectury.loom.util.collection.Multimap;
import dev.architectury.loom.metadata.ForgeModMetadata;

import org.relativitymc.neoloom.neoforge.NFRTMinecraftProvider;
import org.relativitymc.neoloom.neoforge.meta.ForgeUserdevConfiguration;
import org.relativitymc.neoloom.neoforge.util.NameUtil;

@DisableCachingByDefault
public abstract class GenerateDLIConfigTask extends AbstractLoomTask {
	@Input
	protected abstract Property<String> getVersionInfoJson();

	@Input
	protected abstract Property<String> getMinecraftVersion();

	@Input
	protected abstract Property<Boolean> getSplitSourceSets();

	@Input
	protected abstract Property<Boolean> getPlainConsole();

	@Input
	protected abstract Property<Boolean> getANSISupportedIDE();

	@Input
	protected abstract Property<String> getLog4jConfigPaths();

	@Input
	@Optional
	protected abstract Property<String> getClientGameJarPath();

	@Input
	@Optional
	protected abstract Property<String> getCommonGameJarPath();

	@Input
	protected abstract Property<String> getAssetsDirectoryPath();

	@Input
	protected abstract Property<String> getNativesDirectoryPath();

	@Input
	protected abstract Property<String> getProductionNamespace();

	@Input
	protected abstract Property<String> getDefaultMixinRemapType();

	@Input
	@Optional
	protected abstract MapProperty<String, List<String>> getForgeLaunchProgramArgs();

	@Input
	@Optional
	protected abstract MapProperty<String, Map<String, String>> getForgeLaunchProperties();

	@InputFile
	@PathSensitive(PathSensitivity.ABSOLUTE)
	@Optional
	public abstract RegularFileProperty getRemapClasspathFile();

	@OutputFile
	protected abstract RegularFileProperty getDevLauncherConfig();

	@Nested
	protected abstract Property<ClasspathGroupService.Options> getClasspathGroupOptions();

	@InputFiles
	@PathSensitive(PathSensitivity.NONE)
	@Optional
	public abstract ConfigurableFileCollection getRuntimeClasspathForForge();

	@OutputFile
	@Optional
	protected abstract RegularFileProperty getForgeLegacyClasspathFile();

	@Input
	@Optional
	protected abstract ListProperty<String> getForgeExtraMixinConfigs();

	public GenerateDLIConfigTask() {
		getVersionInfoJson().set(LoomGradlePlugin.GSON.toJson(getExtension().getMinecraftProvider().getVersionInfo()));
		getMinecraftVersion().set(getExtension().getMinecraftProvider().minecraftVersion());
		getSplitSourceSets().set(getExtension().areEnvironmentSourceSetsSplit());
		getANSISupportedIDE().set(ansiSupportedIde(getProject()));
		getPlainConsole().set(getProject().getGradle().getStartParameter().getConsoleOutput() == ConsoleOutput.Plain);
		getClasspathGroupOptions().set(ClasspathGroupService.create(getProject()));

		getLog4jConfigPaths().set(getAllLog4JConfigFiles(getProject()));

		if (getSplitSourceSets().get()) {
			getClientGameJarPath().set(getGameJarPath("client"));
			getCommonGameJarPath().set(getGameJarPath("common"));
		}

		getAssetsDirectoryPath().set(new File(getExtension().getFiles().getUserCache(), "assets").getAbsolutePath());
		getNativesDirectoryPath().set(getExtension().getFiles().getNativesDirectory(getProject()).getAbsolutePath());
		getDevLauncherConfig().set(getExtension().getFiles().getDevLauncherConfig());
		getProductionNamespace().set(getExtension().getProductionNamespaceEnum().map(MappingsNamespace::toString));
		getDefaultMixinRemapType().set(getExtension().getDefaultMixinRemapTypeEnum().map(remapType -> remapType.toString().toLowerCase(Locale.ROOT)));

		if (getExtension().getMinecraftProvider() instanceof NFRTMinecraftProvider provider) {
			String mergedJarName = getExtension().getMinecraftProvider().getJarPrefix() + "minecraft-merged";

			boolean[] requiresLegacyClasspath = new boolean[1];

			for (Map.Entry<String, ForgeUserdevConfiguration.LaunchConfiguration> entry : provider.getForgeUserdevConfiguration().launchConfigurations().entrySet()) {
				getForgeLaunchProgramArgs().put(entry.getKey(), entry.getValue().programArgs());
				getForgeLaunchProperties().put(
						entry.getKey(),
						entry.getValue().jvmProperties().entrySet().stream()
								.map(innerEntry -> {
									if ("{minecraft_classpath_file}".equals(innerEntry.getValue())) {
										requiresLegacyClasspath[0] = true;
										return Map.entry(innerEntry.getKey(), this.getForgeLegacyClasspathFile().getAsFile().get().getAbsolutePath());
									}

									return innerEntry;
								})
								.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
				);
			}

			if (requiresLegacyClasspath[0]) {
				Configuration runtimeClasspath = getProject().getConfigurations().detachedConfiguration(
						getProject().getConfigurations().getByName("runtimeClasspath").getAllDependencies().stream()
								.filter(dependency -> dependency.getName() == null || !dependency.getName().startsWith(mergedJarName)) // load merged jar as a mod as well
								.map(Dependency::copy)
								.toArray(Dependency[]::new)
				);
				getRuntimeClasspathForForge().from(runtimeClasspath);
				getForgeLegacyClasspathFile().set(new File(getExtension().getFiles().getProjectPersistentCache(), "forge_minecraft_classpath.txt"));
			}

			getForgeExtraMixinConfigs().set(getExtension().getForgeExtraMixinConfigs());
		}
	}

	@TaskAction
	public void run() throws IOException {
		final MinecraftVersionMeta versionInfo = LoomGradlePlugin.GSON.fromJson(getVersionInfoJson().get(), MinecraftVersionMeta.class);
		File assetsDirectory = new File(getAssetsDirectoryPath().get());

		if (versionInfo.assets().equals("legacy")) {
			assetsDirectory = new File(assetsDirectory, "/legacy/" + versionInfo.id());
		}

		if (this.getForgeLegacyClasspathFile().isPresent()) {
			String classpathFileContent = this.getRuntimeClasspathForForge().getFiles().stream()
					.filter(file -> {
						try {
							if (ZipUtils.isZip(file.toPath())) {
								if (ZipUtils.contains(file.toPath(), ForgeModMetadata.NEOFORGE_FILE_PATH)) return false;
								if (ZipUtils.contains(file.toPath(), ForgeModMetadata.FORGE_FILE_PATH)) return false;

								try (JarFile jarFile = new JarFile(file)) {
									Manifest manifest = jarFile.getManifest();

									if (manifest != null) {
										String fmlModType = manifest.getMainAttributes().getValue(new Attributes.Name("FMLModType"));
										if ("GAMELIBRARY".equals(fmlModType) || "MOD".equals(fmlModType)) return false;
									}
								}
							}

							return true;
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					})
					.map(File::getAbsolutePath)
					.collect(Collectors.joining("\n"));

			Files.writeString(this.getForgeLegacyClasspathFile().getAsFile().get().toPath(), classpathFileContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		final LaunchConfig launchConfig = new LaunchConfig()
				.property("fabric.development", "true")
				.property("log4j.configurationFile", getLog4jConfigPaths().get())
				.property("log4j2.formatMsgNoLookups", "true")
				.property("fabric.defaultModDistributionNamespace", getProductionNamespace().get())
				.property("fabric.defaultMixinRemapType", getDefaultMixinRemapType().get());

		if (!getForgeLaunchProgramArgs().isPresent() || getForgeLaunchProgramArgs().get().isEmpty()) {
			launchConfig
					.argument("client", "--assetIndex")
					.argument("client", versionInfo.assetIndex().fabricId(getMinecraftVersion().get()))
					.argument("client", "--assetsDir")
					.argument("client", assetsDirectory.getAbsolutePath());
		} else {
			for (Map.Entry<String, List<String>> entry : getForgeLaunchProgramArgs().get().entrySet()) {
				String id = NameUtil.mangleLaunchEnvName(entry.getKey());

				for (String arg : entry.getValue()) {
					if ("{asset_index}".equals(arg)) {
						arg = versionInfo.assetIndex().fabricId(getMinecraftVersion().get());
					} else if ("{assets_root}".equals(arg)) {
						arg = assetsDirectory.getAbsolutePath();
					}

					launchConfig.argument(id, arg);
				}
			}
		}

		if (getForgeExtraMixinConfigs().isPresent() && !getForgeExtraMixinConfigs().get().isEmpty()) {
			for (String mixinConfig : getForgeExtraMixinConfigs().get()) {
				launchConfig.argument("-mixin.config").argument(mixinConfig);
			}
		}

		if (getForgeLaunchProperties().isPresent() && !getForgeLaunchProperties().get().isEmpty()) {
			for (Map.Entry<String, Map<String, String>> outerEntry : getForgeLaunchProperties().get().entrySet()) {
				String id = NameUtil.mangleLaunchEnvName(outerEntry.getKey());

				for (Map.Entry<String, String> entry : outerEntry.getValue().entrySet()) {
					launchConfig.property(id, entry.getKey(), entry.getValue());
				}
			}
		}

		if (getRemapClasspathFile().isPresent()) {
			launchConfig.property("fabric.remapClasspathFile", getRemapClasspathFile().get().getAsFile().getAbsolutePath());
		}

		if (versionInfo.hasNativesToExtract()) {
			String nativesPath = getNativesDirectoryPath().get();

			launchConfig
					.property("client", "java.library.path", nativesPath)
					.property("client", "org.lwjgl.librarypath", nativesPath);
		}

		if (getSplitSourceSets().get()) {
			launchConfig.property("client", "fabric.gameJarPath.client", getClientGameJarPath().get());
			launchConfig.property("fabric.gameJarPath", getCommonGameJarPath().get());
		}

		try (ScopedServiceFactory serviceFactory = new ScopedServiceFactory()) {
			ClasspathGroupService classpathGroupService = serviceFactory.get(getClasspathGroupOptions());

			if (classpathGroupService.hasGroups()) {
				launchConfig.property("fabric.classPathGroups", classpathGroupService.getClasspathGroupsPropertyValue());
			}

			// setup fml mod classes
			{
				Multimap<String, String> modClasses = Multimap.setMultimap();

				for (ClasspathGroup group : classpathGroupService.getClasspathGroups()) {
					for (File file : classpathGroupService.getClasspath(group)) {
						modClasses.put(group.name(), file.getAbsolutePath());
					}
				}

				String value = modClasses.streamEntries()
						.map(entry -> entry.left() + "%%" + entry.right())
						.collect(Collectors.joining(File.pathSeparator));
				launchConfig.property("fml.modFolders", value);
			}
		}

		//Enable ansi by default for idea and vscode when gradle is not ran with plain console.
		if (getANSISupportedIDE().get() && !getPlainConsole().get()) {
			launchConfig.property("fabric.log.disableAnsi", "false");
		}

		Files.writeString(getDevLauncherConfig().getAsFile().get().toPath(), launchConfig.asString(), StandardCharsets.UTF_8);
	}

	private static String getAllLog4JConfigFiles(Project project) {
		return LoomGradleExtension.get(project).getLog4jConfigs().getFiles().stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(","));
	}

	private String getGameJarPath(String env) {
		MappedMinecraftProvider.Split split = (MappedMinecraftProvider.Split) getExtension().getNamedMinecraftProvider();

		return switch (env) {
		case "client" -> split.getClientOnlyJar().getPath().toAbsolutePath().toString();
		case "common" -> split.getCommonJar().getPath().toAbsolutePath().toString();
		default -> throw new UnsupportedOperationException();
		};
	}

	private static boolean ansiSupportedIde(Project project) {
		File rootDir = project.getRootDir();
		return new File(rootDir, ".vscode").exists()
				|| new File(rootDir, ".idea").exists()
				|| new File(rootDir, ".project").exists()
				|| (Arrays.stream(rootDir.listFiles()).anyMatch(file -> file.getName().endsWith(".iws")));
	}

	public static class LaunchConfig {
		private final Map<String, List<String>> values = new HashMap<>();

		public LaunchConfig property(String key, String value) {
			return property("common", key, value);
		}

		public LaunchConfig property(String side, String key, String value) {
			values.computeIfAbsent(side + "Properties", (s -> new ArrayList<>()))
					.add(String.format("%s=%s", key, value));
			return this;
		}

		public LaunchConfig argument(String value) {
			return argument("common", value);
		}

		public LaunchConfig argument(String side, String value) {
			values.computeIfAbsent(side + "Args", (s -> new ArrayList<>()))
					.add(value);
			return this;
		}

		public String asString() {
			StringJoiner stringJoiner = new StringJoiner("\n");

			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				stringJoiner.add(entry.getKey());

				for (String s : entry.getValue()) {
					stringJoiner.add("\t" + s);
				}
			}

			return stringJoiner.toString();
		}
	}
}
