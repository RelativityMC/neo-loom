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

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

import net.fabricmc.loom.util.Platform;

/**
 * @see <a href="https://github.com/neoforged/GradleMinecraftDependencies/blob/c0ad4ad30230d7023d397888081d51ad7dd84d3c/buildSrc/src/main/groovy/net/neoforged/minecraftdependencies/GenerateModuleMetadata.groovy#L182">GradleMinecraftDependencies</a>
 */
public interface OperatingSystem extends Named {
	Attribute<OperatingSystem> ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", OperatingSystem.class);

	String LINUX = "linux";
	String MACOSX = "osx";
	String WINDOWS = "windows";

	static String getCurrent() {
		return switch (Platform.CURRENT.getOperatingSystem()) {
		case WINDOWS -> WINDOWS;
		case MAC_OS -> MACOSX;
		case LINUX -> LINUX;
		};
	}
}
