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

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import net.fabricmc.tinyremapper.api.TrRemapper;
import net.fabricmc.loom.util.Constants;

/**
 * A class visitor that replaces a set of string constants in the processed class file(s).
 */
public final class StringConstantPatcher extends ClassVisitor {
	private final Map<String, String> constantChanges;

	private static final String LAUNCH_HANDLER_INPUT_CLASS_FILE = "net/minecraft/client/Minecraft.class";
	private static final String LAUNCH_HANDLER_OUTPUT_CLASS_FILE = "net/minecraft/client/main/Main.class";

	private static final String DETECTED_VERSION = "net/minecraft/DetectedVersion";
	private static final String MINECRAFT = "net/minecraft/client/Minecraft";

	private StringConstantPatcher(ClassVisitor next, Map<String, String> constantChanges) {
		super(Constants.ASM_VERSION, next);
		this.constantChanges = constantChanges;
	}

	private StringConstantPatcher(ClassVisitor next, String from, String to) {
		this(next, Map.of(from, to));
	}

	/**
	 * Patches the Minecraft.class check in FML's CommonUserdevLaunchHandler
	 * to refer to a class that is found in any mapping set (Main.class).
	 *
	 * <p>See <a href="https://github.com/architectury/architectury-loom/issues/212">issue #212</a>
	 */
	public static ClassVisitor forUserdevLaunchHandler(ClassVisitor next) {
		return new StringConstantPatcher(next, LAUNCH_HANDLER_INPUT_CLASS_FILE, LAUNCH_HANDLER_OUTPUT_CLASS_FILE);
	}

	private static ClassVisitor forRemapping(ClassVisitor next, TrRemapper remapper, String... names) {
		final Map<String, String> constantChanges = new HashMap<>();

		for (String name : names) {
			final @Nullable String target = remapper.map(name);

			if (target == null || name.equals(target)) {
				continue;
			}

			constantChanges.put(name + ".class", target + ".class");
		}

		if (!constantChanges.isEmpty()) {
			return new StringConstantPatcher(next, constantChanges);
		} else {
			return next;
		}
	}

	/**
	 * Patches the DetectedVersion.class check in FML's FMLLoader
	 * to remap that reference to the current deobfuscated ns.
	 *
	 * <p>See <a href="https://github.com/architectury/architectury-loom/issues/299">issue #299</a>
	 */
	public static ClassVisitor forFmlLoader(ClassVisitor next, TrRemapper remapper) {
		return forRemapping(next, remapper, DETECTED_VERSION);
	}

	/**
	 * Patches the Minecraft.class check in FML's GameLocator
	 * to remap that reference to the current deobfuscated ns.
	 *
	 * <p>See <a href="https://github.com/architectury/architectury-loom/issues/299">issue #299</a>
	 */
	public static ClassVisitor forGameLocator(ClassVisitor next, TrRemapper remapper) {
		return forRemapping(next, remapper, MINECRAFT);
	}

	/**
	 * Patches the class checks in FML's RequiredSystemFiles
	 * to remap that reference to the current deobfuscated ns.
	 *
	 * <p>See <a href="https://github.com/architectury/architectury-loom/issues/299">issue #299</a>
	 */
	public static ClassVisitor forRequiredSystemFiles(ClassVisitor next, TrRemapper remapper) {
		return forRemapping(next, remapper, DETECTED_VERSION, MINECRAFT);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return new MethodPatcher(super.visitMethod(access, name, descriptor, signature, exceptions));
	}

	private final class MethodPatcher extends MethodVisitor {
		MethodPatcher(MethodVisitor next) {
			super(Constants.ASM_VERSION, next);
		}

		@Override
		public void visitLdcInsn(Object value) {
			if (value instanceof String key) {
				final @Nullable String target = constantChanges.get(key);

				if (target != null) {
					value = target;
				}
			}

			super.visitLdcInsn(value);
		}
	}
}
