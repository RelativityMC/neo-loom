package org.relativitymc.neoloom.neoforge;

import dev.architectury.loom.tool.ForgeToolValueSource;

import dev.architectury.loom.tool.JavaExecutableFetcher;

import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MergedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider;

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Constants;

import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class NFRTMergedMinecraftProvider extends MergedMinecraftProvider {
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
			settings.args("--java-executable", javaToolchainExecutable.get());

			if (getExtension().refreshDeps()) {
				settings.args("--disable-cache");
			}

			settings.args("--neoforge", "net.neoforged:neoforge:" + neoForgeVersion() + ":userdev");
			settings.args("--dist", "joined");
			settings.args("--write-result", "gameJarWithNeoForge:" + this.minecraftMergedJar.toAbsolutePath().toString());
			settings.args("--write-result", "gameSourcesWithNeoForge:" + this.minecraftMergedSources.toAbsolutePath().toString());
		});

		final MinecraftLibraryProvider libraryProvider = new MinecraftLibraryProvider(this, configContext.project());
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
		return neoForgeWorkingDirectory(configContext.project(), minecraftVersion(), neoForgeVersion());
	}

	protected String neoForgeVersion() {
		return Objects.requireNonNull(Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getNeoForgeVersion(), "NeoForge version not setup");
	}
}
