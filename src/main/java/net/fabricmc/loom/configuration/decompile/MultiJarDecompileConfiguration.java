/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.configuration.decompile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

import net.fabricmc.loom.api.decompilers.DecompilerOptions;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftJar;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Strings;

public final class MultiJarDecompileConfiguration extends DecompileConfiguration<MappedMinecraftProvider> {
	public MultiJarDecompileConfiguration(Project project, MappedMinecraftProvider minecraftProvider) {
		super(project, minecraftProvider);
	}

	@Override
	public String getTaskName(MinecraftJar.Type type) {
		return "gen%sSources".formatted(Strings.capitalize(type.toString()));
	}

	@Override
	public void afterEvaluation() {
		Map<String, List<TaskProvider<Task>>> decompTasks = new HashMap<>(); // decomp name -> tasks
		Map<MinecraftJar.Type, TaskProvider<Task>> decompOuterTasks = new HashMap<>(); // jar type -> task

		for (DecompilerOptions options : extension.getDecompilerOptions()) {
			decompTasks.put(options.getFormattedName(), new ArrayList<>());
		}

		for (MinecraftJar minecraftJar : minecraftProvider.getMinecraftJars()) {
			if (decompOuterTasks.containsKey(minecraftJar.getType())) {
				throw new IllegalArgumentException("Duplicate jar type %s".formatted(minecraftJar.getType()));
			}

			TaskProvider<Task> taskProvider = createDecompileTasks(minecraftJar.getType(), decompTasks, task -> {
				task.getInputJarName().set(minecraftJar.getName());
				task.getSourcesOutputJar().fileValue(GenerateSourcesTask.getJarFileWithSuffix("-sources.jar", minecraftJar.getPath()));
			});
			decompOuterTasks.put(minecraftJar.getType(), taskProvider);
		}

		// chain tasks so only one can run at a time
		{
			List<TaskProvider<Task>> list = decompTasks.values().stream().flatMap(Collection::stream).toList();

			for (int i = 1, listSize = list.size(); i < listSize; i++) {
				TaskProvider<Task> prev = list.get(i - 1);
				TaskProvider<Task> cur = list.get(i);
				cur.configure(task -> task.mustRunAfter(prev));
			}
		}

		for (Map.Entry<String, List<TaskProvider<Task>>> entry : decompTasks.entrySet()) {
			String decompilerName = entry.getKey();
			List<TaskProvider<Task>> tasks = entry.getValue();
			project.getTasks().register("genSourcesWith" + decompilerName, task -> {
				task.setDescription("Decompile minecraft using %s.".formatted(decompilerName));
				task.setGroup(Constants.TaskGroup.FABRIC);

				tasks.forEach(task::dependsOn);
			});
		}

		project.getTasks().register("genSources", task -> {
			task.setDescription("Decompile minecraft using the default decompiler.");
			task.setGroup(Constants.TaskGroup.FABRIC);

			decompOuterTasks.values().forEach(task::dependsOn);
		});
	}

	private TaskProvider<Task> createDecompileTasks(MinecraftJar.Type type, Map<String, List<TaskProvider<Task>>> decompTasks, Action<GenerateSourcesTask> configureAction) {
		extension.getDecompilerOptions().forEach(options -> {
			final String decompilerName = options.getFormattedName();
			final String taskName = "%sWith%s".formatted(getTaskName(type), decompilerName);

			TaskProvider<GenerateSourcesTask> taskProvider = project.getTasks().register(taskName, GenerateSourcesTask.class, options);
			taskProvider.configure(task -> {
				configureAction.execute(task);
				task.dependsOn(project.getTasks().named("validateAccessWidener"));
				task.setDescription("Decompile minecraft using %s.".formatted(decompilerName));
				task.setGroup(Constants.TaskGroup.FABRIC);
			});

			decompTasks.get(decompilerName).add((TaskProvider<Task>) (Object) taskProvider);
		});

		return project.getTasks().register(getTaskName(type), task -> {
			task.setDescription("Decompile minecraft (%s) using the default decompiler.".formatted(type));
			task.setGroup(Constants.TaskGroup.FABRIC);

			task.dependsOn(project.getTasks().named("%sWith%s".formatted(getTaskName(type), DecompileConfiguration.DEFAULT_DECOMPILER)));
		});
	}
}
