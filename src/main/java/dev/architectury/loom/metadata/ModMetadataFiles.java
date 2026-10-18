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
import java.util.List;
import java.util.function.Function;

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
	private static final List<Function<ResourceGetter, @Nullable ModMetadata>> MOD_METADATA_TYPES = List.of(
			ForgeModMetadata::tryParse
	);

	private static @Nullable ModMetadata tryRead(ResourceGetter getter) {
		for (Function<ResourceGetter, @Nullable ModMetadata> parser : MOD_METADATA_TYPES) {
			ModMetadata metadata = parser.apply(getter);

			if (metadata != null) {
				return metadata;
			}
		}

		return null;
	}

	/**
	 * Reads the mod metadata file from a jar.
	 *
	 * @param jar the path to the jar file
	 * @return the mod metadata file, or {@code null} if not found
	 */
	public static @Nullable ModMetadata fromJar(Path jar) throws IOException {
		return tryRead(path -> {
			try {
				return ZipUtils.unpackNullable(jar, path);
			} catch (IOException e) {
				LOGGER.error("IO Error while reading {} from {}", path, jar);
				return null;
			}
		});
	}

	/**
	 * Reads the mod metadata file from a directory.
	 *
	 * @param directory the path to the directory
	 * @return the mod metadata file, or {@code null} if not found
	 */
	public static @Nullable ModMetadata fromDirectory(Path directory) throws IOException {
		return tryRead(path -> {
			final Path metadataPath = directory.resolve(path);

			if (Files.exists(metadataPath)) {
				try {
					return Files.readAllBytes(metadataPath);
				} catch (IOException e) {
					LOGGER.error("IO Error while reading {} from {}", path, metadataPath);
					return null;
				}
			} else {
				return null;
			}
		});
	}

	/**
	 * Reads the first mod metadata file from source sets.
	 *
	 * @param project    the project owning the source sets
	 * @param sourceSets the source sets to read from
	 * @return the mod metadata file, or {@code null} if not found
	 */
	public static @Nullable ModMetadata fromSourceSets(Project project, SourceSet... sourceSets) throws IOException {
		return tryRead(path -> {
			final @Nullable File file = SourceSetHelper.findFirstFileInResource(path, project, sourceSets);

			if (file != null) {
				try {
					return Files.readAllBytes(file.toPath());
				} catch (IOException e) {
					LOGGER.error("IO Error while reading {} from {}", path, file.toPath());
					return null;
				}
			} else {
				return null;
			}
		});
	}

	public interface ResourceGetter {
		byte @Nullable [] tryGetFile(String path);
	}
}
