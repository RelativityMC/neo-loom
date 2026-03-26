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

package dev.architectury.loom.tool;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

public abstract class JavaExecutableFetcher {
	@Inject
	protected abstract JavaToolchainService getToolchainService();

	public static Provider<String> getJavaToolchainExecutable(Project project) {
		return project.provider(() -> {
			final JavaExecutableFetcher fetcher = project.getObjects().newInstance(JavaExecutableFetcher.class);
			final JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
			final JavaToolchainSpec toolchain = java.getToolchain();

			if (!toolchain.getLanguageVersion().isPresent()) {
				// Toolchain not configured, we'll use the runtime Java version.
				return null;
			}

			final JavaLauncher launcher = fetcher.getToolchainService().launcherFor(toolchain).get();
			return launcher.getExecutablePath().getAsFile().getAbsolutePath();
		});
	}

	public static Provider<String> getJavaToolchainExecutable(Project project, int version) {
		return project.provider(() -> {
			final JavaExecutableFetcher fetcher = project.getObjects().newInstance(JavaExecutableFetcher.class);

			final JavaLauncher launcher = fetcher.getToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(version))).get();
			return launcher.getExecutablePath().getAsFile().getAbsolutePath();
		});
	}
}
