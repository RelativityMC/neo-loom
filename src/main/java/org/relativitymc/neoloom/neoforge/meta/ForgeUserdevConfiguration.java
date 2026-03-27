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

package org.relativitymc.neoloom.neoforge.meta;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loom.util.ZipUtils;

public record ForgeUserdevConfiguration(
		String sourcesNotation,
		String universalJarNotation,
		List<String> librariesNotations,
		List<String> testLibrariesNotations,
		String mcpNotation,
		String binPatcherNotation,
		Map<String, List<String>> extraDependenciesNotations,
		Map<String, LaunchConfiguration> launchConfigurations
) {

	public static ForgeUserdevConfiguration fromUserdevJar(File userdevJar) {
		try {
			JsonObject userdevJson = ZipUtils.unpackGson(userdevJar.toPath(), "config.json", JsonObject.class);

			int specVersion = userdevJson.get("spec").getAsInt();
			if (specVersion != 2) {
				throw new UnsupportedOperationException("Unsupported userdev spec: " + specVersion);
			}

			return new ForgeUserdevConfiguration(
					userdevJson.get("sources").getAsString(),
					userdevJson.get("universal").getAsString(),
					userdevJson.get("libraries").getAsJsonArray().asList().stream()
							.map(JsonElement::getAsString)
							.toList(),
					// neoforge only
					Optional.ofNullable(userdevJson.get("testLibraries"))
							.map(jsonElement -> {
								return jsonElement.getAsJsonArray().asList().stream()
										.map(JsonElement::getAsString)
										.toList();
							})
							.orElse(List.of()),
					userdevJson.get("mcp").getAsString(),
					userdevJson.get("binpatcher").getAsJsonObject().get("version").getAsString(),
					// forge only
					Optional.ofNullable(userdevJson.get("extraDependencies"))
							.map(jsonElement -> {
								return jsonElement.getAsJsonObject().entrySet().stream()
										.map(entry -> {
											return Map.entry(
													entry.getKey(),
													entry.getValue().getAsJsonArray().asList().stream()
															.map(JsonElement::getAsString)
															.toList()
											);
										})
										.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
							})
							.orElse(Map.of()),
					userdevJson.get("runs").getAsJsonObject().entrySet().stream()
							.map(entry -> Map.entry(entry.getKey(), LaunchConfiguration.fromJsonObjectV2(entry.getValue().getAsJsonObject())))
							.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
			);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public record LaunchConfiguration(
			String main,
			List<String> programArgs,
			List<String> jvmArgs,
			Map<String, String> jvmProperties
	) {

		public static LaunchConfiguration fromJsonObjectV2(JsonObject launchEntry) {
			return new LaunchConfiguration(
					launchEntry.get("main").getAsString(),
					launchEntry.get("args").getAsJsonArray().asList().stream()
							.map(JsonElement::getAsString)
							.toList(),
					launchEntry.get("jvmArgs").getAsJsonArray().asList().stream()
							.map(JsonElement::getAsString)
							.toList(),
					launchEntry.get("props").getAsJsonObject().entrySet().stream()
							.map(entry -> Map.entry(entry.getKey(), entry.getValue().getAsString()))
							.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
			);
		}

	}

}
