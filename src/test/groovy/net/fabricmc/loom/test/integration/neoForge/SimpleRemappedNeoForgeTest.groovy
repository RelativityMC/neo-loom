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

package net.fabricmc.loom.test.integration.neoForge

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
		gradle.buildGradle << '''
				repositories {
					maven {
						url = "https://repo.codemc.io/repository/relativitymc/"
					}
				}

				dependencies {
					minecraft 'com.mojang:minecraft:26.1-snapshot-6'
					neoForge 'net.neoforged:neoforge:26.1.0.0-alpha.9+snapshot-6'
					mappings loom.layered {
						it.mappings "org.relativitymc:modern-yarn:26.1-snapshot-6+build.3:v2"
						it.mappings "org.relativitymc:modern-yarn-mappings-patch-neoforge:26.1+build.1"
					}
                }

                loom {
                	useIntermediateMappings = true
					intermediaryUrl = 'https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1$s/intermediary-%1$s-v2.jar'
				}
		'''
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

		when:
		def result = gradle.run(tasks: [
			"build",
			"configureClientLaunch"
		])

		then:
		result.task(":build").outcome == SUCCESS
		result.task(":configureClientLaunch").outcome == SUCCESS
	}
}
