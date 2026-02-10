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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.CheckSignatureAdapter;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;

import org.relativitymc.neoloom.neoforge.meta.ModPlatform;

public final class ArchitecturyCommonJson implements JsonBackedModMetadataFile, SingleIdModMetadataFile {
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
	public Set<String> getAccessTransformers(ModPlatform platform) {
		return Set.of();
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		if (modId == null) {
			throw new IllegalArgumentException("getInjectedInterfaces: mod ID has to be provided for architectury.common.json");
		}

		return getInjectedInterfaces(json, modId);
	}

	static List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(JsonObject json, String modId) {
		Objects.requireNonNull(modId, "mod ID");

		if (json.has("injected_interfaces")) {
			JsonObject addedIfaces = json.getAsJsonObject("injected_interfaces");

			final List<InterfaceInjectionProcessor.InjectedInterface> result = new ArrayList<>();

			for (String className : addedIfaces.keySet()) {
				final JsonArray ifacesInfo = addedIfaces.getAsJsonArray(className);

				for (JsonElement ifaceElement : ifacesInfo) {
					String ifaceInfo = ifaceElement.getAsString();

					String name = ifaceInfo;
					String generics = null;

					if (ifaceInfo.contains("<") && ifaceInfo.contains(">")) {
						name = ifaceInfo.substring(0, ifaceInfo.indexOf("<"));
						generics = ifaceInfo.substring(ifaceInfo.indexOf("<"));

						// First Generics Check, if there are generics, are them correctly written?
						SignatureReader reader = new SignatureReader("Ljava/lang/Object" + generics + ";");
						CheckSignatureAdapter checker = new CheckSignatureAdapter(CheckSignatureAdapter.CLASS_SIGNATURE, null);
						reader.accept(checker);
					}

					result.add(new InterfaceInjectionProcessor.InjectedInterface(modId, className, name, generics));
				}
			}

			return result;
		}

		return Collections.emptyList();
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
	public boolean equals(Object obj) {
		return this == obj || obj instanceof ArchitecturyCommonJson acj && acj.json.equals(json);
	}

	@Override
	public int hashCode() {
		return json.hashCode();
	}
}
