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

package dev.architectury.loom.metadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.SourceSet;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

/**
 * Utilities for reading mod metadata files.
 */
public final class ModMetadataFiles {
	private static final Logger LOGGER = Logging.getLogger(ModMetadataFiles.class);
	private static final Map<String, Function<byte[], ModMetadataFile>> SINGLE_FILE_METADATA_TYPES = Map.of(
			ArchitecturyCommonJson.FILE_NAME, ArchitecturyCommonJson::of,
			ModsToml.FILE_PATH, onError(ModsToml::of, "Could not load mods.toml", () -> new ErroringModMetadataFile("mods.toml")),
			ModsToml.NEOFORGE_FILE_PATH, onError(ModsToml::of, "Could not load neoforge.mods.toml", () -> new ErroringModMetadataFile("neoforge.mods.toml"))
	);

	private static <A, B> Function<A, B> onError(Function<A, B> fn, String message, Supplier<B> onError) {
		return a -> {
			try {
				return fn.apply(a);
			} catch (Exception e) {
				LOGGER.info(message, e);
				return onError.get();
			}
		};
	}

	/**
	 * Reads the mod metadata file from a jar.
	 *
	 * @param jar the path to the jar file
	 * @return the mod metadata file, or {@code null} if not found
	 */
	public static @Nullable ModMetadataFile fromJar(Path jar) throws IOException {
		for (final String filePath : SINGLE_FILE_METADATA_TYPES.keySet()) {
			final byte @Nullable [] bytes = ZipUtils.unpackNullable(jar, filePath);

			if (bytes != null) {
				return SINGLE_FILE_METADATA_TYPES.get(filePath).apply(bytes);
			}
		}

		return null;
	}

	/**
	 * Reads the mod metadata file from a directory.
	 *
	 * @param directory the path to the directory
	 * @return the mod metadata file, or {@code null} if not found
	 */
	public static @Nullable ModMetadataFile fromDirectory(Path directory) throws IOException {
		for (final String filePath : SINGLE_FILE_METADATA_TYPES.keySet()) {
			final Path metadataPath = directory.resolve(filePath);

			if (Files.exists(metadataPath)) {
				return SINGLE_FILE_METADATA_TYPES.get(filePath).apply(Files.readAllBytes(metadataPath));
			}
		}

		return null;
	}

	/**
	 * Reads the first mod metadata file from source sets.
	 *
	 * @param project    the project owning the source sets
	 * @param sourceSets the source sets to read from
	 * @return the mod metadata file, or {@code null} if not found
	 */
	public static @Nullable ModMetadataFile fromSourceSets(Project project, SourceSet... sourceSets) throws IOException {
		for (final String filePath : SINGLE_FILE_METADATA_TYPES.keySet()) {
			final @Nullable File file = SourceSetHelper.findFirstFileInResource(filePath, project, sourceSets);

			if (file != null) {
				return SINGLE_FILE_METADATA_TYPES.get(filePath).apply(Files.readAllBytes(file.toPath()));
			}
		}

		return null;
	}
}
