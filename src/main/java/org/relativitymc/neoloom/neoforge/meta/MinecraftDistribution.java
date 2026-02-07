package org.relativitymc.neoloom.neoforge.meta;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * @see <a href="https://github.com/neoforged/GradleMinecraftDependencies/blob/c0ad4ad30230d7023d397888081d51ad7dd84d3c/buildSrc/src/main/groovy/net/neoforged/minecraftdependencies/GenerateModuleMetadata.groovy#L101">GradleMinecraftDependencies project</a>
 */
public interface MinecraftDistribution extends Named {
    Attribute<MinecraftDistribution> ATTRIBUTE = Attribute.of("net.neoforged.distribution", MinecraftDistribution.class);

    String CLIENT = "client";
    String SERVER = "server";
}