/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.test.integration.neoLoom

import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import net.fabricmc.loom.test.util.GradleProjectTestTrait

import static net.fabricmc.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class SimpleRemappedNeoForgeTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildSrc("devServerTest")
		gradle.buildGradle << """
				repositories {
					maven {
						url = "https://repo.codemc.io/repository/relativitymc/"
					}
					maven {
						url = "https://maven.minecraftforge.net/"
					}
				}

				dependencies {
					minecraft 'com.mojang:minecraft:${mcVersion}'
					forgeUserdev '${forgeNotation}'
					mappings loom.layered {
						it.mappings ${mappings}
						${mappingsPatches}
					}
                }

                loom {
                	useIntermediateMappings = true
					${intermediary}
				}
		"""
		def sourceFile = new File(gradle.projectDir, "src/main/java/example/Test.java")
		sourceFile.parentFile.mkdirs()
		@Language("JAVA") String src =  """
		package example;

		import net.minecraft.util.Identifier;

		import org.spongepowered.asm.mixin.Mixin; // Make sure we applied loaders deps via the installer data

		public class Test {
			public static void main(String[] args) {
			    Identifier id = Identifier.of("loom", "test");
			}
		}
		"""
		sourceFile.text = src

		def neoforgeModsToml = new File(gradle.projectDir, "src/main/resources/META-INF/neoforge.mods.toml")
		neoforgeModsToml.parentFile.mkdirs()
		@Language("TOML") String neoforgeModsTomlSrc = """
		modLoader="lowcodefml"
		loaderVersion="*"
		license="MIT"
		[[mods]]
		modId="testmod"
		version="1.0.0"
		displayName="testmod"
		description='''
		Example mod description.
		'''

		[[dependencies.testmod]]
		modId="neoforge"
		type="required"
		versionRange="*"
		ordering="NONE"
		side="BOTH"

		[[dependencies.testmod]]
		modId="minecraft"
		type="required"
		versionRange="*"
		ordering="NONE"
		side="BOTH"
		"""
		neoforgeModsToml.text = neoforgeModsTomlSrc

		def forgeModsToml = new File(gradle.projectDir, "src/main/resources/META-INF/forge.mods.toml")
		forgeModsToml.parentFile.mkdirs()
		@Language("TOML") String forgeModsTomlSrc = """
		modLoader="lowcodefml"
		loaderVersion="*"
		license="MIT"

		[[mods]]
		modId="testmod"
		version="1.0.0"
		displayName="Test Mod"
		description='''Example mod description.
		Newline characters can be used like this, and rendered on the mods screen properly.'''

		[[dependencies.testmod]]
		modId="forge"
		mandatory=true
		versionRange="*"
		ordering="NONE"
		side="BOTH"

		[[dependencies.testmod]]
		modId="minecraft"
		mandatory=true
		versionRange="*"
		ordering="NONE"
		side="BOTH"
		"""
		forgeModsToml.text = forgeModsTomlSrc

		when:
		def result = gradle.run(tasks: [
			"build",
			"configureClientLaunch"
		])

		// custom stdio isn't supported in configuration cache: https://github.com/gradle/gradle/issues/33858
		def resultRunServer = gradle.run(tasks: ["runServer"], configurationCache: false)

		then:
		result.task(":build").outcome == SUCCESS
		result.task(":configureClientLaunch").outcome == SUCCESS
		resultRunServer.task(":runServer").outcome == SUCCESS

		where:
		mcVersion   | forgeNotation                                      | mappings                                             | mappingsPatches                                                                     | intermediary
		// "1.20.1" | "net.minecraftforge:forge:1.20.1-47.4.20:userdev"  | "\"net.fabricmc:yarn:1.20.1+build.9:v2\""            | ""                                                                                  | ""
		"1.21.1"    | "net.minecraftforge:forge:1.21.1-52.1.14:userdev"  | "\"net.fabricmc:yarn:1.21.1+build.3:v2\""            | ""                                                                                  | ""
		"1.21.1"    | "net.neoforged:neoforge:21.1.233:userdev"          | "\"net.fabricmc:yarn:1.21.1+build.3:v2\""            | "it.mappings \"dev.architectury:yarn-mappings-patch-neoforge:1.21+build.4\""        | ""
		"1.21.11"   | "net.minecraftforge:forge:1.21.11-61.1.0:userdev"  | "\"net.fabricmc:yarn:1.21.11+build.6:v2\""           | "it.mappings \"dev.architectury:yarn-mappings-patch-forge:1.21.9+build.6\""         | ""
		"1.21.11"   | "net.neoforged:neoforge:21.11.42:userdev"          | "\"net.fabricmc:yarn:1.21.11+build.6:v2\""           | "it.mappings \"dev.architectury:yarn-mappings-patch-neoforge:1.21+build.4\""        | ""
		"26.1.2"    | "net.neoforged:neoforge:26.1.2.67-beta:userdev"    | "\"org.relativitymc:modern-yarn:26.1.2+build.3:v2\"" | "it.mappings \"org.relativitymc:modern-yarn-mappings-patch-neoforge:26.1+build.1\"" | "intermediaryUrl = 'https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1\\\$s/intermediary-%1\\\$s-v2.jar'"
		"26.1.2"    | "net.minecraftforge:forge:26.1.2-64.0.8:userdev"   | "\"org.relativitymc:modern-yarn:26.1.2+build.3:v2\"" | "it.mappings \"org.relativitymc:modern-yarn-mappings-patch-forge:26.1+build.2\""    | "intermediaryUrl = 'https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1\\\$s/intermediary-%1\\\$s-v2.jar'"
		"26.2"      | "net.neoforged:neoforge:26.2.0.7-beta:userdev"     | "\"org.relativitymc:modern-yarn:26.2+build.1:v2\""   | "it.mappings \"org.relativitymc:modern-yarn-mappings-patch-neoforge:26.1+build.1\"" | "intermediaryUrl = 'https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1\\\$s/intermediary-%1\\\$s-v2.jar'"
		"26.2"      | "net.minecraftforge:forge:26.2-65.0.1:userdev"     | "\"org.relativitymc:modern-yarn:26.2+build.1:v2\""   | "it.mappings \"org.relativitymc:modern-yarn-mappings-patch-forge:26.1+build.2\""    | "intermediaryUrl = 'https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1\\\$s/intermediary-%1\\\$s-v2.jar'"
	}
}
