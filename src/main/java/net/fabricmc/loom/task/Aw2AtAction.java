/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.task;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.jvm.tasks.Jar;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.architectury.loom.extensions.ModBuildExtensions;

/**
 * Configuration-cache-compatible action for converting AWs to ATs.
 * Do NOT turn me into a record!
 */
public abstract class Aw2AtAction implements Action<Task>, Serializable {
	@Input
	public abstract SetProperty<String> getAtAccessWideners();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	public static void addToTask(Jar task, @UnknownNullability List<String> atAccessWideners) {
		Aw2AtAction aw2AtAction = task.getProject().getObjects().newInstance(Aw2AtAction.class);
		aw2AtAction.getAtAccessWideners().addAll(atAccessWideners);
		task.doLast(aw2AtAction);
	}

	@Override
	public void execute(Task t) {
		final Jar jarTask = (Jar) t;

		final WorkQueue workQueue = getWorkerExecutor().noIsolation();

		workQueue.submit(ConversionAction.class, p -> {
			p.getArchiveFile().set(jarTask.getArchiveFile());
			p.getAtAccessWideners().set(getAtAccessWideners());
		});
	}

	public interface Aw2AtParameters extends WorkParameters {
		RegularFileProperty getArchiveFile();
		SetProperty<String> getAtAccessWideners();
	}

	public abstract static class ConversionAction implements WorkAction<Aw2AtParameters> {
		private static final Logger LOGGER = LoggerFactory.getLogger(Aw2AtAction.class);

		@Override
		public void execute() {
			final File jarFile = getParameters().getArchiveFile().get().getAsFile();
			final Set<String> atAccessWideners = getParameters().getAtAccessWideners().get();

			if (!atAccessWideners.isEmpty()) {
				try {
					ModBuildExtensions.convertAwToAt(atAccessWideners, jarFile.toPath(), null, null);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				LOGGER.info("Converted {} AWs in {}", atAccessWideners.size(), jarFile.getName());
			}
		}
	}
}
