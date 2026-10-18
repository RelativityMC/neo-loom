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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;

public final class ArchitecturyCommonJson implements JsonBackedModMetadata, SingleIdModMetadataFile {
	private static final Logger LOGGER = LoggerFactory.getLogger(ArchitecturyCommonJson.class);

	public static final String FILE_NAME = "architectury.common.json";
	private static final String ACCESS_WIDENER_KEY = "accessWidener";

	private final JsonObject json;

	private ArchitecturyCommonJson(JsonObject json) {
		this.json = Objects.requireNonNull(json, "json");
	}

	public static ArchitecturyCommonJson of(byte[] utf8) {
		return of(new String(utf8, StandardCharsets.UTF_8));
	}

	public static ArchitecturyCommonJson of(String text) {
		return of(LoomGradlePlugin.GSON.fromJson(text, JsonObject.class));
	}

	public static ArchitecturyCommonJson of(Path path) throws IOException {
		return of(Files.readString(path, StandardCharsets.UTF_8));
	}

	public static ArchitecturyCommonJson of(File file) throws IOException {
		return of(file.toPath());
	}

	public static ArchitecturyCommonJson of(JsonObject json) {
		return new ArchitecturyCommonJson(json);
	}

	public static @Nullable ArchitecturyCommonJson tryParse(ModMetadataFiles.ResourceGetter getter) {
		byte @Nullable [] bytes = getter.tryGetFile(FILE_NAME);

		if (bytes == null) {
			return null;
		}

		ArchitecturyCommonJson architecturyCommonJson;

		try {
			architecturyCommonJson = of(bytes);
			return architecturyCommonJson;
		} catch (Throwable t) {
			LOGGER.info("Failed to parse architectury common json", t);
		}

		return null;
	}

	@Override
	public JsonObject getJson() {
		return json;
	}

	@Override
	public @Nullable String getId() {
		return null;
	}

	@Override
	public Set<String> getAccessWideners() {
		if (json.has(ACCESS_WIDENER_KEY)) {
			return Set.of(json.get(ACCESS_WIDENER_KEY).getAsString());
		} else {
			return Set.of();
		}
	}

	@Override
	public Set<String> getAccessTransformers() {
		return Set.of();
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		return List.of();
	}

	/**
	 * {@return {@value #FILE_NAME}}.
	 */
	@Override
	public String getFileName() {
		return FILE_NAME;
	}

	@Override
	public List<String> getMixinConfigs() {
		return List.of();
	}

	@Override
	public List<String> getNeoEnumExtensions() {
		return List.of();
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj || obj instanceof ArchitecturyCommonJson acj && acj.json.equals(json);
	}

	@Override
	public int hashCode() {
		return json.hashCode();
	}
}
