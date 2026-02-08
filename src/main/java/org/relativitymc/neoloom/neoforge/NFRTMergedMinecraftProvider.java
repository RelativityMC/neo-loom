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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Constants;

import dev.architectury.loom.tool.ForgeToolValueSource;
import dev.architectury.loom.tool.JavaExecutableFetcher;

public class NFRTMergedMinecraftProvider extends MergedMinecraftProvider implements NFRTMinecraftProvider {
	private final ConfigContext configContext;
	private final MinecraftMetadataProvider metadataProvider;

	private Path minecraftMergedJar;
	private Path minecraftMergedSources;

	public NFRTMergedMinecraftProvider(MinecraftMetadataProvider metadataProvider, ConfigContext configContext) {
		super(metadataProvider, configContext);
		this.configContext = configContext;
		this.metadataProvider = metadataProvider;
	}

	@Override
	public void provide() throws Exception {
		initFiles();

		verifyJavaVersion();

		final NFRTMinecraftLibraryProvider libraryProvider = new NFRTMinecraftLibraryProvider(this, configContext.project());

		Properties artifactManifest = new Properties();
		libraryProvider.collectArtifactManifest(artifactManifest);

		File tmpDir = getProject().getLayout().getBuildDirectory().dir("tmp/neoformruntime").get().getAsFile();
		tmpDir.mkdirs();
		File artifactManifestFile = new File(tmpDir, "nfrt_artifact_manifest.properties");

		try (var out = new BufferedOutputStream(new FileOutputStream(artifactManifestFile))) {
			artifactManifest.store(out, "");
		} catch (IOException e) {
			throw new GradleException("Failed to write NFRT artifact manifest: " + e, e);
		}

		ForgeToolValueSource.exec(getProject(), settings -> {
			settings.getExecClasspath().from(getProject().getConfigurations().getByName(Constants.Configurations.NFRT_TOOL));
			settings.getMainClass().set("net.neoforged.neoform.runtime.cli.Main");
			MinecraftVersionMeta.JavaVersion javaVersion = this.metadataProvider.getVersionMeta().javaVersion();
			Provider<String> javaToolchainExecutable;

			if (javaVersion != null) {
				javaToolchainExecutable = JavaExecutableFetcher.getJavaToolchainExecutable(getProject(), javaVersion.majorVersion());
			} else {
				javaToolchainExecutable = JavaExecutableFetcher.getJavaToolchainExecutable(getProject());
			}

			settings.getExecutable().set(javaToolchainExecutable);

			settings.args("run");
			settings.args("--home-dir", new File(getProject().getGradle().getGradleUserHomeDir(), "caches/neoformruntime").getAbsolutePath());
			settings.args("--work-dir", getProject().getLayout().getBuildDirectory().dir("tmp/neoformruntime").get().getAsFile().getAbsolutePath());
			settings.args("--java-executable", JavaExecutableFetcher.getJavaToolchainExecutable(getProject(), 21).get()); // TODO remove when neoforge fixes https://github.com/neoforged/NeoForge/issues/2956

			if (getExtension().refreshDeps()) {
				settings.args("--disable-cache");
			}

			settings.args("--artifact-manifest", artifactManifestFile.getAbsolutePath());
			settings.args("--warn-on-artifact-manifest-miss");

			settings.args("--neoforge", neoForgeNotation() + ":userdev");
			settings.args("--dist", "joined");
			settings.args("--write-result", "gameJarWithNeoForge:" + this.minecraftMergedJar.toAbsolutePath().toString());
			settings.args("--write-result", "gameSourcesWithNeoForge:" + this.minecraftMergedSources.toAbsolutePath().toString());
		});

		libraryProvider.provide();
	}

	@Override
	protected void initFiles() {
		// no super call
		this.minecraftMergedJar = path("minecraft-merged.jar");
		this.minecraftMergedSources = path("minecraft-merged-sources.jar");
	}

	@Override
	public Path getMergedJar() {
		return this.minecraftMergedJar;
	}

	@Override
	public List<Path> getMinecraftJars() {
		return List.of(this.minecraftMergedJar);
	}

	@Override
	public File getMinecraftClientJar() {
		throw new UnsupportedOperationException("handled by NFRT");
	}

	@Override
	public @Nullable File getMinecraftExtractedServerJar() {
		throw new UnsupportedOperationException("handled by NFRT");
	}

	@Override
	public File getMinecraftServerJar() {
		throw new UnsupportedOperationException("handled by NFRT");
	}

	@Override
	public @Nullable BundleMetadata getServerBundleMetadata() {
		throw new UnsupportedOperationException("handled by NFRT");
	}

	@Override
	public File workingDir() {
		return neoForgeWorkingDirectory(configContext.project(), minecraftVersion(), neoForgeDependency());
	}

	protected @NonNull String neoForgeNotation() {
		return neoForgeDependency().getGroup() + ":" + neoForgeDependency().getName() + ":" + neoForgeDependency().getVersion();
	}

	protected ExternalModuleDependency neoForgeDependency() {
		return Objects.requireNonNull(Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getNeoForgeDependency(), "NeoForge version not setup");
	}
}
