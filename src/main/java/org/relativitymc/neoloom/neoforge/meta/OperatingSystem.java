package org.relativitymc.neoloom.neoforge.meta;

import net.fabricmc.loom.util.Platform;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * @see <a href="https://github.com/neoforged/GradleMinecraftDependencies/blob/c0ad4ad30230d7023d397888081d51ad7dd84d3c/buildSrc/src/main/groovy/net/neoforged/minecraftdependencies/GenerateModuleMetadata.groovy#L182">GradleMinecraftDependencies</a>
 */
public interface OperatingSystem extends Named {
    Attribute<OperatingSystem> ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", OperatingSystem.class);

    String LINUX = "linux";
    String MACOSX = "osx";
    String WINDOWS = "windows";

	public static String getCurrent() {
		return switch (Platform.CURRENT.getOperatingSystem()) {
			case WINDOWS -> WINDOWS;
			case MAC_OS -> MACOSX;
			case LINUX -> LINUX;
		};
	}
}
