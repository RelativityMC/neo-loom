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

package org.relativitymc.neoloom.neoforge.enumextension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.util.ZipUtils;

import dev.architectury.loom.metadata.ModMetadata;
import dev.architectury.loom.metadata.ModMetadataFiles;

public class EnumExtensionRemapper {
	public static void remap(
			Path path,
			Remapper remapper
	) throws IOException {
		ModMetadata modMetadata = ModMetadataFiles.fromJar(path);

		if (modMetadata == null || modMetadata.getNeoEnumExtensions().isEmpty()) {
			return;
		}

		ZipUtils.transformJson(JsonObject.class, path, modMetadata.getNeoEnumExtensions().stream().collect(Collectors.toMap(s -> s, s -> json -> {
			if (json.has("entries") && json.get("entries").isJsonArray()) {
				for (JsonElement element : json.get("entries").getAsJsonArray()) {
					JsonObject entry = element.getAsJsonObject();

					String enumClazz = entry.get("enum").getAsString();
					entry.add("enum", new JsonPrimitive(remapper.map(enumClazz)));

					String enumConstructor = entry.get("constructor").getAsString();
					entry.add("constructor", new JsonPrimitive(remapper.mapMethodDesc(enumConstructor)));

					JsonElement parametersRaw = entry.get("parameters");

					if (parametersRaw.isJsonObject()) { // only object is interesting
						JsonObject parameters = parametersRaw.getAsJsonObject();

						String paramClazz = parameters.get("class").getAsString();
						parameters.add("class", new JsonPrimitive(remapper.map(paramClazz)));

						if (parameters.has("field")) {
							String paramField = parameters.get("field").getAsString();
							parameters.add("field", new JsonPrimitive(remapper.mapFieldName(paramClazz, paramField, "Lnet/neoforged/fml/common/asm/enumextension/EnumProxy;")));
						}

						if (parameters.has("method")) {
							String paramMethod = parameters.get("method").getAsString();
							parameters.add("method", new JsonPrimitive(remapper.mapMethodName(paramClazz, paramMethod, Type.getMethodDescriptor(Type.getType(Object.class), Type.INT_TYPE, Type.getType(Class.class)))));
						}
					}
				}
			}

			return json;
		})));
	}
}
