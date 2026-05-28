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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;

import org.relativitymc.neoloom.neoforge.meta.ModPlatform;

public final class ForgeModMetadata implements ModMetadata {
	private static final Logger LOGGER = LoggerFactory.getLogger(ForgeModMetadata.class);

	public static final String FORGE_FILE_PATH = "META-INF/mods.toml";
	public static final String NEOFORGE_FILE_PATH = "META-INF/neoforge.mods.toml";
	private final Config config;
	private final ModPlatform platform;
	private final Set<String> extraAccessTransformers;

	private ForgeModMetadata(Config config, ModPlatform platform, Set<String> extraAccessTransformers) {
		this.config = Objects.requireNonNull(config);
		this.platform = Objects.requireNonNull(platform);
		this.extraAccessTransformers = Objects.requireNonNull(extraAccessTransformers);
	}

	public static @Nullable ForgeModMetadata tryParse(ModMetadataFiles.ResourceGetter getter) {
		ModPlatform platform = ModPlatform.FORGE;
		byte @Nullable [] bytes = getter.tryGetFile(FORGE_FILE_PATH);

		if (bytes == null) {
			platform = ModPlatform.NEOFORGE;
			bytes = getter.tryGetFile(NEOFORGE_FILE_PATH);
		}

		if (bytes == null) {
			return null;
		}

		CommentedConfig parsed;

		try {
			parsed = new TomlParser().parse(new ByteArrayInputStream(bytes));
		} catch (ParsingException e) {
			LOGGER.error("Failed to parse forge mod config for platform {}", platform, e);
			parsed = CommentedConfig.of(TomlFormat.instance());
		}

		return new ForgeModMetadata(
				parsed,
				platform,
				getter.tryGetFile(Constants.NeoForge.ACCESS_TRANSFORMER_PATH) != null ? Set.of(Constants.NeoForge.ACCESS_TRANSFORMER_PATH) : Set.of()
		);
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
	public Set<String> getAccessTransformers() {
		if (this.platform == ModPlatform.NEOFORGE) {
			final List<? extends Config> ats = config.get("accessTransformers");

			if (ats != null) {
				final Set<String> result = new HashSet<>();

				for (Config atEntry : ats) {
					final String file = atEntry.get("file");
					if (file != null) result.add(file);
				}

				result.addAll(this.extraAccessTransformers);

				return result;
			}
		}

		return new HashSet<>(this.extraAccessTransformers);
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		return List.of();
	}

	@Override
	public String getFileName() {
		return FORGE_FILE_PATH;
	}

	@Override
	public List<String> getMixinConfigs() {
		return List.of();
	}

	@Override
	public List<String> getNeoEnumExtensions() {
		final Optional<List<Config>> mods = config.getOptional("mods");
		if (mods.isEmpty()) return List.of();

		final Set<String> enumExtensions = new HashSet<>();

		for (final Config mod : mods.get()) {
			final Optional<String> enumExtension = mod.getOptional("enumExtensions");
			enumExtension.ifPresent(enumExtensions::add);
		}

		return List.copyOf(enumExtensions);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof ForgeModMetadata forgeModMetadata && forgeModMetadata.config.equals(config);
	}

	@Override
	public int hashCode() {
		return config.hashCode();
	}
}
