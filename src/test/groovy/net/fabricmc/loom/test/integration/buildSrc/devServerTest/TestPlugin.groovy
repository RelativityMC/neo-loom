/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.test.integration.buildSrc.devServerTest

import org.gradle.api.Plugin
import org.gradle.api.Project

import net.fabricmc.loom.task.RunGameTask

class TestPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		println("Dev launch Test plugin")

		project.afterEvaluate {
			def task = project.getTasks().getByName("runServer") as RunGameTask

			def runner = new ServerTaskRunner()
			task.setStandardOutput(runner.getStdoutForGradle(System.out))
			task.setStandardInput(runner.getStdinForGradle())
			task.doFirst {
				def runDir = project.getLayout().projectDirectory.file("run/").asFile
				new File(runDir, "eula.txt").text = "eula=true"
				new File(runDir, "server.properties").text = "level-type=flat"
			}
			task.doLast {
				if (!runner.isStopping()) {
					throw new IllegalStateException("Server did not stop properly. Check logs for details. ")
				}
			}
		}
	}
}
