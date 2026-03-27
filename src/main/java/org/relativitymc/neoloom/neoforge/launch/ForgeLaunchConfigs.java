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

package org.relativitymc.neoloom.neoforge.launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gradle.api.artifacts.ExternalModuleDependency;

import net.fabricmc.loom.LoomGradleExtension;

public class ForgeLaunchConfigs {
	public static final Config NEOFORGE_1_21 = new Config(
			new EnumMap<>(Map.of(
					LaunchTarget.CLIENT, "net.neoforged.fml.startup.Client",
					LaunchTarget.CLIENT_DATA, "net.neoforged.fml.startup.DataClient",
					LaunchTarget.SERVER_DATA, "net.neoforged.fml.startup.DataServer",
					LaunchTarget.GAMETEST_SERVER, "net.neoforged.fml.startup.GameTestServer",
					LaunchTarget.SERVER, "net.neoforged.fml.startup.Server",
					LaunchTarget.JUNIT, "NONE"
			)),
			List.of(LaunchFeature.NEOFORGE_J21)
	);
	public static final Config NEOFORGE_26_1_0 = new Config(
			new EnumMap<>(Map.of(
					LaunchTarget.CLIENT, "net.neoforged.fml.startup.Client",
					LaunchTarget.CLIENT_DATA, "net.neoforged.fml.startup.DataClient",
					LaunchTarget.SERVER_DATA, "net.neoforged.fml.startup.DataServer",
					LaunchTarget.GAMETEST_SERVER, "net.neoforged.fml.startup.GameTestServer",
					LaunchTarget.SERVER, "net.neoforged.fml.startup.Server",
					LaunchTarget.JUNIT, "NONE"
			)),
			List.of(LaunchFeature.NEOFORGE_J25)
	);

	public record Config(EnumMap<LaunchTarget, String> mainClass, List<LaunchFeature> launchFeatures) {
		public List<String> collectExtraVmArgs(LaunchTarget target) {
			return this.launchFeatures().stream()
					.flatMap(launchFeature -> launchFeature.getExtraVmArgs(target).stream())
					.toList();
		}

		public List<String> collectExtraProgramArgs(LaunchTarget target, LoomGradleExtension extension) {
			return this.launchFeatures().stream()
					.flatMap(launchFeature -> launchFeature.getExtraProgramArgs(target, extension).stream())
					.toList();
		}
	}

	public enum LaunchTarget {
		CLIENT("client"),
		CLIENT_DATA("clientData"),
		SERVER("server"),
		SERVER_DATA("serverData"),
		GAMETEST_SERVER("gameTestServer"),
		JUNIT("junit");

		private static final Map<String, LaunchTarget> VALUES;

		static {
			VALUES = Arrays.stream(LaunchTarget.values())
					.collect(Collectors.toMap(
							LaunchTarget::getId,
							Function.identity()
					));
		}

		public static LaunchTarget fromId(String id) {
			return Objects.requireNonNull(VALUES.get(id), () -> "Invalid launch id: %s".formatted(id));
		}

		private final String id;

		LaunchTarget(String id) {
			this.id = id;
		}

		public String getId() {
			return this.id;
		}
	}

	public enum LaunchFeature {
		NEOFORGE_J21() {
			@Override
			public List<String> getExtraVmArgs(LaunchTarget target) {
				return List.of(
						"--add-opens",
						"java.base/java.lang.invoke=ALL-UNNAMED",
						"--add-exports",
						"jdk.naming.dns/com.sun.jndi.dns=java.naming"
				);
			}

			@Override
			public List<String> getExtraProgramArgs(LaunchTarget target, LoomGradleExtension extension) {
				ArrayList<String> args = new ArrayList<>();

				if (target == LaunchTarget.CLIENT || target == LaunchTarget.JUNIT) {
					args.add("--version");
					args.add("NeoLoomDevLaunch");
				}

				args.add("--fml.mcVersion");
				args.add(extension.getMinecraftProvider().minecraftVersion());
				args.add("--fml.neoForgeVersion");
				ExternalModuleDependency neoForgeDependency = extension.getMetadataProvider().getForgeUserdevDependency();
				args.add(neoForgeDependency != null ? neoForgeDependency.getVersion() : "unknown");
				args.add("--fml.neoFormVersion");
				args.add("loom.stub");

				return args;
			}
		},
		NEOFORGE_J25() {
			@Override
			public List<String> getExtraVmArgs(LaunchTarget target) {
				return List.of(
						"--sun-misc-unsafe-memory-access=allow",
						"--enable-native-access=ALL-UNNAMED",
						"--add-opens",
						"java.base/java.lang.invoke=ALL-UNNAMED",
						"--add-exports",
						"jdk.naming.dns/com.sun.jndi.dns=java.naming"
				);
			}

			@Override
			public List<String> getExtraProgramArgs(LaunchTarget target, LoomGradleExtension extension) {
				ArrayList<String> args = new ArrayList<>();

				if (target == LaunchTarget.CLIENT || target == LaunchTarget.JUNIT) {
					args.add("--version");
					args.add("NeoLoomDevLaunch");
				}

				return args;
			}
		};

		public abstract List<String> getExtraVmArgs(LaunchTarget target);

		public abstract List<String> getExtraProgramArgs(LaunchTarget target, LoomGradleExtension extension);
	}
}
