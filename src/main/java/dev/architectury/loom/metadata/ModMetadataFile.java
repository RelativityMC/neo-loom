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

import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor;

import dev.architectury.loom.util.collection.CollectionUtil;

import org.relativitymc.neoloom.neoforge.meta.ModPlatform;

/**
 * The metadata file of a mod, such as {@link ArchitecturyCommonJson architectury.common.json} or
 * {@link QuiltModJson quilt.mod.json}.
 *
 * @see net.fabricmc.loom.util.fmj.FabricModJson
 */
public interface ModMetadataFile {
	/**
	 * {@return all mod IDs in this mod metadata file}.
	 */
	Set<String> getIds();

	/**
	 * {@return the mod ID in this mod metadata file, or {@code null} if absent}.
	 */
	default @Nullable String getId() {
		return CollectionUtil.first(getIds()).orElse(null);
	}

	/**
	 * {@return the paths to the access widener file of this mod, or an empty set if absent}.
	 */
	Set<String> getAccessWideners();

	/**
	 * {@return the paths to the access transformer files of this mod, or an empty set if absent}.
	 *
	 * @param platform the platform to run the query on
	 */
	Set<String> getAccessTransformers(ModPlatform platform);

	/**
	 * {@return the injected interface data in this mod metadata file}.
	 *
	 * @param modId the mod ID to use as a fallback if {@link #getId} returns {@code null}
	 * @throws IllegalArgumentException if both {@code modId} and {@link #getId} are {@code null}
	 */
	List<InterfaceInjectionProcessor.InjectedInterface> getInjectedInterfaces(@Nullable String modId);

	/**
	 * {@return the file name of this mod metadata file}.
	 */
	String getFileName();

	/**
	 * {@return a list of the mixin configs declared in this mod metadata file}.
	 */
	List<String> getMixinConfigs();
}
