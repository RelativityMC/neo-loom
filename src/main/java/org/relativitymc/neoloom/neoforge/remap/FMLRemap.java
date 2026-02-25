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

package org.relativitymc.neoloom.neoforge.remap;

import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrRemapper;

public class FMLRemap {
	private static final String FORGE_OBJECT_HOLDER = "net/minecraftforge/fml/common/asm/ObjectHolderDefinalize";
	private static final String FORGE_MOD_DIR_TRANSFORMER_DISCOVERER = "net/minecraftforge/fml/loading/ModDirTransformerDiscoverer";
	private static final String NEOFORGE_OBJECT_HOLDER = "net/neoforged/fml/common/asm/ObjectHolderDefinalize";
	private static final String NEOFORGE_LAUNCH_HANDLER = "net/neoforged/fml/loading/targets/CommonUserdevLaunchHandler";
	private static final String NEOFORGE_LOADER = "net/neoforged/fml/loading/FMLLoader";
	private static final String NEOFORGE_GAME_LOCATOR = "net/neoforged/fml/loading/moddiscovery/locators/GameLocator";
	private static final String NEOFORGE_REQUIRED_SYSTEM_FILES = "net/neoforged/fml/loading/moddiscovery/locators/RequiredSystemFiles";

	public static void configureRemapper(TinyRemapper.Builder tinyRemapperBuilder) {
		tinyRemapperBuilder.extraPostApplyVisitor((cls, next) -> {
			TrRemapper remapper = cls.getEnvironment().getRemapper();

			if (cls.getName().equals(FORGE_OBJECT_HOLDER) || cls.getName().equals(NEOFORGE_OBJECT_HOLDER)) {
				return new RemapObjectHolderVisitor(next, remapper);
			}

			if (cls.getName().equals(FORGE_MOD_DIR_TRANSFORMER_DISCOVERER)) {
				// TODO
			}

			if (cls.getName().equals(NEOFORGE_LAUNCH_HANDLER)) {
				return StringConstantPatcher.forUserdevLaunchHandler(next);
			}

			if (cls.getName().equals(NEOFORGE_LOADER)) {
				return StringConstantPatcher.forFmlLoader(next, remapper);
			}

			if (cls.getName().equals(NEOFORGE_GAME_LOCATOR)) {
				return StringConstantPatcher.forGameLocator(next, remapper);
			}

			if (cls.getName().equals(NEOFORGE_REQUIRED_SYSTEM_FILES)) {
				return StringConstantPatcher.forRequiredSystemFiles(next, remapper);
			}

			return next;
		});
	}
}
