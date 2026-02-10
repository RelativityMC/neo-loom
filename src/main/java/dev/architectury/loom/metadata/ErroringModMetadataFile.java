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

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;

import org.relativitymc.neoloom.neoforge.meta.ModPlatform;

/**
 * A fallback mod metadata file that represents a non-fatal format error
 * in another file type. An example of such file type is mods.toml,
 * which doesn't necessarily need to be parsed &ndash; it primarily needs to just exist.
 */
@VisibleForTesting
public final class ErroringModMetadataFile implements ModMetadataFile {
	private final String fileName;

	ErroringModMetadataFile(String fileName) {
		this.fileName = fileName;
	}

	@Override
	public Set<String> getIds() {
		return Set.of();
	}

	@Override
	public Set<String> getAccessWideners() {
		return Set.of();
	}

	@Override
	public Set<String> getAccessTransformers(ModPlatform platform) {
		return Set.of();
	}

	@Override
	public List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId) {
		return List.of();
	}

	@Override
	public String getFileName() {
		return fileName + " [erroring]";
	}

	@Override
	public List<String> getMixinConfigs() {
		return List.of();
	}
}
