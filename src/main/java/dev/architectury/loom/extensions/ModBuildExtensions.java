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

package dev.architectury.loom.extensions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.task.service.MappingsService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.service.ServiceFactory;

import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.io.AccessTransformFormats;
import dev.architectury.loom.accesstransformer.Aw2At;
import dev.architectury.loom.util.LfWriter;

public final class ModBuildExtensions {
	public static Set<String> readMixinConfigsFromManifest(File jarFile) {
		try (JarFile jar = new JarFile(jarFile)) {
			@Nullable Manifest manifest = jar.getManifest();

			if (manifest != null) {
				Attributes attributes = manifest.getMainAttributes();
				String mixinConfigs = attributes.getValue(Constants.NeoForge.MIXIN_CONFIGS_MANIFEST_KEY);

				if (mixinConfigs != null) {
					return Set.of(mixinConfigs.split(","));
				}
			}

			return Set.of();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read mixin configs from jar " + jarFile.getAbsolutePath(), e);
		}
	}

	public static void convertAwToAt(Set<String> atAccessWideners, Path outputFile, @Nullable ServiceFactory serviceFactory, @Nullable Provider<MappingsService.Options> options) throws IOException {
		if (atAccessWideners.isEmpty()) {
			return;
		}

		AccessTransformSet at = AccessTransformSet.create();

		try (FileSystemUtil.Delegate fileSystem = FileSystemUtil.getJarFileSystem(outputFile, false)) {
			FileSystem fs = fileSystem.get();
			Path atPath = fs.getPath(Constants.NeoForge.ACCESS_TRANSFORMER_PATH);

			if (Files.exists(atPath)) {
				throw new FileAlreadyExistsException("Jar " + outputFile + " already contains an access transformer - cannot convert AWs!");
			}

			for (String aw : atAccessWideners) {
				Path awPath = fs.getPath(aw);

				if (Files.notExists(awPath)) {
					throw new NoSuchFileException("Could not find AW '" + aw + "' to convert into AT!");
				}

				try (BufferedReader reader = Files.newBufferedReader(awPath, StandardCharsets.UTF_8)) {
					at.merge(Aw2At.toAccessTransformSet(reader));
				}

				Files.delete(awPath);
			}

			if (serviceFactory != null && options != null) {
				MappingsService service = serviceFactory.get(options);
				at = at.remap(service.getMemoryMappingTree(), service.getFrom(), service.getTo());
			}

			try (Writer writer = new LfWriter(Files.newBufferedWriter(atPath))) {
				AccessTransformFormats.FML.write(writer, at);
			}
		}
	}
}
