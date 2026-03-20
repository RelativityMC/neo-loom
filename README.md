# Neo Loom

A fork of Fabric Loom that supports the NeoForge modding toolchain. Heavily based on Architectury Loom. 

Feel free to reach out on [our Discord server](https://discord.relativitymc.org/).

## Currently implemented
- 1.21+ No-Remap setup
- 26.1+ Remap setup
- Jar-in-Jar
- ClassTweaker / AccessWidener -> AccessTransformer conversion
- Devlaunch

## Current to-dos
- JUnit
- Production launch tasks
- Forge support
- split-sources, client-only, server-only setups for (Neo)Forge

## Using Neo Loom

Add this to `settings.gradle`:  
```gradle
pluginManagement {
	repositories {
		maven {
			name = 'Fabric'
			url = 'https://maven.fabricmc.net/'
		}
		maven {
			name = 'RelativityMC'
			url = 'https://repo.codemc.io/repository/relativitymc/'
		}
		gradlePluginPortal()
	}
}
```

In your buildscript, using [Modern Yarn](https://github.com/RelativityMC/yarn):  
```gradle
plugins {
    id 'org.relativitymc.neo-loom-remap' version '1.15-SNAPSHOT'
}

repositories {
    maven {
        url = "https://repo.codemc.io/repository/relativitymc/"
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
	neoForge "net.neoforged:neoforge:${project.neoforge_version}"
    mappings loom.layered {
		it.mappings "org.relativitymc:modern-yarn:${project.yarn_mappings}:v2"
		it.mappings "org.relativitymc:modern-yarn-mappings-patch-neoforge:26.1+build.1"
	}
}

remapJar {
    atAccessWideners.add("modid.accesswidener")
}

loom {
    useIntermediateMappings = true
    intermediaryUrl = 'https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1$s/intermediary-%1$s-v2.jar'
}
```

In your buildscript, without any mappings:  
```gradle
plugins {
    id 'org.relativitymc.neo-loom' version '1.15-SNAPSHOT'
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
	neoForge "net.neoforged:neoforge:${project.neoforge_version}"
}
```

Full forge run configs:
```
loom {
	runs {
		serverData {
			server()
			environment("serverData")
		}
		clientData {
			client()
			environment("clientData")
		}
        gameTestServer {
			server()
			environment("gameTestServer")
		}
	}
}
```

# Original README below

# Fabric Loom

A [Gradle](https://gradle.org/) plugin to setup a deobfuscated development environment for Minecraft mods. Primarily used in the Fabric toolchain.

* Has built in support for tiny mappings (Used by [Yarn](https://github.com/FabricMC/yarn))
* Utilises the Fernflower and CFR decompilers to generate source code with comments.
* Designed to support modern versions of Minecraft (Tested with 1.14.4 and upwards)
* Built in support for IntelliJ IDEA, Eclipse and Visual Studio Code to generate run configurations for Minecraft.
* Loom targets the latest version of Gradle 7 or newer 
* Supports Java 16 upwards

## Use Loom to develop mods

To get started developing your own mods please follow the guide on [Setting up a mod development environment](https://fabricmc.net/wiki/tutorial:setup).

## Debugging Loom (Only needed if you want to work on Loom itself)

_This guide assumes you are using IntelliJ IDEA, other IDE's have not been tested; your experience may vary._

1. Import as a Gradle project by opening the build.gradle
2. Create a Gradle run configuration to run the following tasks `build publishToMavenLocal -x test`. This will build Loom and publish to a local maven repo without running the test suite. You can run it now.
3. Prepare a project for using the local version of Loom:
   * A good starting point is to clone the [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) into your working directory
   * Add `mavenLocal()` to the repositories:
     * If you're using `id 'fabric-loom'` inside `plugins`, the correct `repositories` block is inside `pluginManagement` in settings.gradle
     * If you're using `apply plugin:` for Loom, the correct `repositories` block is inside `buildscript` in build.gradle
   * Change the loom version to `0.6.local`. For example `id 'fabric-loom' version '0.6.local'`
4. Create a Gradle run configuration:
   * Set the Gradle project path to the project you have just configured above
   * Set some tasks to run, such as `clean build` you can change these to suit your needs.
   * Add the run configuration you created earlier to the "Before Launch" section to rebuild loom each time you debug
5. You should now be able to run the configuration in debug mode, with working breakpoints.
