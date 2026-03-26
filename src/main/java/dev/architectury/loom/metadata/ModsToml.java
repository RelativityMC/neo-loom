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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;
import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.fabricmc.loom.util.ExceptionUtil;

import org.relativitymc.neoloom.neoforge.meta.ModPlatform;

public final class ModsToml implements ModMetadataFile {
	public static final String FILE_PATH = "META-INF/mods.toml";
	public static final String NEOFORGE_FILE_PATH = "META-INF/neoforge.mods.toml";
	private final Config config;

	private ModsToml(Config config) {
		this.config = Objects.requireNonNull(config);
	}

	public static ModsToml of(byte[] utf8) {
		return of(new String(utf8, StandardCharsets.UTF_8));
	}

	public static ModsToml of(String text) {
		try {
			return new ModsToml(new TomlParser().parse(text));
		} catch (ParsingException e) {
			throw ExceptionUtil.createDescriptiveWrapper(IllegalArgumentException::new, "Could not parse mods.toml", e);
		}
	}

	public static ModsToml of(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return new ModsToml(new TomlParser().parse(reader));
		} catch (ParsingException e) {
			throw ExceptionUtil.createDescriptiveWrapper(IllegalArgumentException::new, "Could not parse mods.toml", e);
		}
	}

	public static ModsToml of(File file) throws IOException {
		return of(file.toPath());
	}

	@Override
	public Set<String> getIds() {
		final Optional<List<Config>> mods = config.getOptional("mods");
		if (mods.isEmpty()) return Set.of();

		final List<String> modIds = new ArrayList<>();

		for (final Config mod : mods.get()) {
			final Optional<String> modId = mod.getOptional("modId");
			modId.ifPresent(modIds::add);
		}

		return Set.copyOf(modIds);
	}

	@Override
	public Set<String> getAccessWideners() {
		return Set.of();
	}

	@Override
	public Set<String> getAccessTransformers(ModPlatform platform) {
		if (platform == ModPlatform.NEOFORGE) {
			final List<? extends Config> ats = config.get("accessTransformers");

			if (ats != null) {
				final Set<String> result = new HashSet<>();

				for (Config atEntry : ats) {
					final String file = atEntry.get("file");
					if (file != null) result.add(file);
				}

				return result;
			}
		}

		return Set.of();
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		return List.of();
	}

	@Override
	public String getFileName() {
		return FILE_PATH;
	}

	@Override
	public List<String> getMixinConfigs() {
		return List.of();
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof ModsToml modsToml && modsToml.config.equals(config);
	}

	@Override
	public int hashCode() {
		return config.hashCode();
	}
}
