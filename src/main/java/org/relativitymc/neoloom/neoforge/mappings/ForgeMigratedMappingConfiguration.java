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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.gradle.api.Project;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsSpec;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.download.DownloadException;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.format.srg.TsrgFileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingConfiguration;

import dev.architectury.loom.util.Stopwatch;

import org.relativitymc.neoloom.neoforge.NFRTMergedMinecraftProvider;

public final class ForgeMigratedMappingConfiguration extends MappingConfiguration {
	private final List<MappingsMigrator> migrators = List.of(new FieldMappingsMigrator(), new MethodInheritanceMappingsMigrator(), new NewInnerClassMappingsMigrator());
	private final Path hashPath;
	private final Path rawTinyMappings;
	private long hash = 1;

	public ForgeMigratedMappingConfiguration(String mappingsIdentifier, Path mappingsWorkingDir) {
		super(mappingsIdentifier, mappingsWorkingDir);
		this.hashPath = mappingsWorkingDir.resolve("mappings-migrated.hash");
		this.rawTinyMappings = this.tinyMappings;
	}

	@Override
	protected void mergeExtraMappings(Project project) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		NFRTMergedMinecraftProvider minecraftProvider = (NFRTMergedMinecraftProvider) extension.getMinecraftProvider();

		MemoryMappingTree tree = new MemoryMappingTree();

		try (BufferedReader reader = Files.newBufferedReader(this.tinyMappings, StandardCharsets.UTF_8)) {
			Tiny2FileReader.read(reader, tree);
		}

		try {
			if (!tree.getSrcNamespace().equals(MappingsNamespace.OFFICIAL.toString())) {
				throw new IllegalArgumentException("Mapping must use official as source namespace, found " + tree.getSrcNamespace());
			}

			boolean didAnything = false;

			if (minecraftProvider.getCapabilities().requireMojangMappings) {
				mergeMojangMappings(extension, tree);
				didAnything |= true;
			}

			if (minecraftProvider.getCapabilities().requireSrg) {
				mergeSrgMappings(extension, tree, minecraftProvider.readTSRGMappings());
				didAnything |= true;
			}

			if (didAnything) {
				try (var writer = new Tiny2FileWriter(Files.newBufferedWriter(this.tinyMappings, StandardCharsets.UTF_8), false)) {
					tree.accept(writer);
				}
			}
		} catch (Throwable t) {
			try {
				Files.delete(this.tinyMappings);
			} catch (Throwable t1) {
				t.addSuppressed(t1);
			}

			throw t;
		}
	}

	private void mergeSrgMappings(LoomGradleExtension extension, MemoryMappingTree tree, byte[] bytes) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
			MappingDstNsReorder nsReorder = new MappingDstNsReorder(
					tree,
					List.of(MappingsNamespace.SRG.toString())
			);
			MappingNsRenamer nsRenamer = new MappingNsRenamer(
					nsReorder,
					Map.of(
							"obf", MappingsNamespace.OFFICIAL.toString(),
							"srg", MappingsNamespace.SRG.toString()
					)
			);
			TsrgFileReader.read(reader, "obf", "srg", nsRenamer);
		}
	}

	private void mergeMojangMappings(LoomGradleExtension extension, MemoryMappingTree tree) throws IOException {
		final MinecraftVersionMeta versionInfo = extension.getMinecraftProvider().getVersionInfo();
		final MinecraftVersionMeta.Download clientDownload = versionInfo.download(MojangMappingsSpec.MANIFEST_CLIENT_MAPPINGS);
		final MinecraftVersionMeta.Download serverDownload = versionInfo.download(MojangMappingsSpec.MANIFEST_SERVER_MAPPINGS);

		if (clientDownload == null) {
			throw new RuntimeException("Failed to find official mojang mappings for " + extension.getMetadataProvider().getMinecraftVersion());
		}

		Path mojmapDir = this.mappingsWorkingDir().resolve("..").resolve("neo-loom-mojmaps");
		Files.createDirectories(mojmapDir);

		final Path clientMappings = mojmapDir.resolve("client.txt");
		final Path serverMappings = mojmapDir.resolve("server.txt");

		try {
			extension.download(clientDownload.url())
					.sha1(clientDownload.sha1())
					.downloadPath(clientMappings);

			extension.download(serverDownload.url())
					.sha1(serverDownload.sha1())
					.downloadPath(serverMappings);
		} catch (DownloadException e) {
			throw new UncheckedIOException("Failed to download mappings", e);
		}

		if (!tree.getDstNamespaces().contains(MappingsNamespace.INTERMEDIARY.toString())) {
			readMojangMappings(tree, clientMappings, serverMappings);
			return;
		}

		// Create a mapping tree with src: official dst: named, intermediary
		MemoryMappingTree mappingTree = new MemoryMappingTree();
		tree.accept(mappingTree);

		readMojangMappings(mappingTree, clientMappings, serverMappings);

		// The following code first switches the src namespace to intermediary dropping any entries that don't have an intermediary name
		// This removes any none root methods before switching it back to official
		var officialSwitch = new MappingSourceNsSwitch(tree, MappingsNamespace.OFFICIAL.toString(), false);
		var intermediarySwitch = new MappingSourceNsSwitch(officialSwitch, MappingsNamespace.INTERMEDIARY.toString(), true);
		mappingTree.accept(intermediarySwitch);
	}

	private static void readMojangMappings(MemoryMappingTree mappingTree, Path clientMappings, Path serverMappings) throws IOException {
		// Make official the source namespace
		var nsSwitch = new MappingSourceNsSwitch(mappingTree, MappingsNamespace.OFFICIAL.toString());

		// Read both server and client mappings
		try (BufferedReader clientBufferedReader = Files.newBufferedReader(clientMappings, StandardCharsets.UTF_8);
				BufferedReader serverBufferedReader = Files.newBufferedReader(serverMappings, StandardCharsets.UTF_8)) {
			ProGuardFileReader.read(clientBufferedReader, MappingsNamespace.MOJANG_MAPPINGS.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
			ProGuardFileReader.read(serverBufferedReader, MappingsNamespace.MOJANG_MAPPINGS.toString(), MappingsNamespace.OFFICIAL.toString(), nsSwitch);
		}
	}

	@Override
	protected void manipulateMappings(@NonNull Project project) throws IOException {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		this.hash = 1;

		this.tinyMappings = mappingsWorkingDir().resolve("mappings-migrated.tiny");

		for (MappingsMigrator migrator : this.migrators) {
			hash = hash * 31 + migrator.setup(project, (NFRTMergedMinecraftProvider) extension.getMinecraftProvider(), this.mappingsWorkingDir(), this.rawTinyMappings);
		}

		if (!isOutdated(extension)) {
			project.getLogger().info(":manipulated mappings are up to date");
			return;
		}

		Files.copy(this.rawTinyMappings, this.tinyMappings, StandardCopyOption.REPLACE_EXISTING);

		Stopwatch stopwatch = Stopwatch.createStarted();

		Files.writeString(this.hashPath, Long.toString(this.hash), StandardCharsets.UTF_8);

		for (MappingsMigrator migrator : this.migrators) {
			Path path = Files.createTempFile("mappings-working", ".tiny");
			Files.copy(this.tinyMappings, path, StandardCopyOption.REPLACE_EXISTING);

			List<MappingsMigrator.MappingsEntry> entries = List.of(new MappingsMigrator.MappingsEntry(path));
			migrator.migrate(project, entries);

			Files.copy(path, this.tinyMappings, StandardCopyOption.REPLACE_EXISTING);
			Files.deleteIfExists(path);
		}

		project.getLogger().info(":manipulated mappings in {}", stopwatch.stop());
	}

	private boolean isOutdated(LoomGradleExtension extension) throws IOException {
		if (extension.refreshDeps()) return true;
		if (Files.notExists(this.tinyMappings)) return true;
		if (Files.notExists(this.hashPath)) return true;
		String hashStr = Files.readString(hashPath, StandardCharsets.UTF_8);
		return !Long.toString(this.hash).equals(hashStr);
	}
}
