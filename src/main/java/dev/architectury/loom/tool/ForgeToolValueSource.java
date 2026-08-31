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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

public abstract class ForgeToolValueSource implements ValueSource<ExecResult, ForgeToolValueSource.Parameters> {
	@Inject
	protected abstract ExecOperations getExecOperations();

	public static Provider<ExecResult> create(Project project, Action<? super ForgeToolExecutor.Settings> configurator) {
		return project.getProviders().of(ForgeToolValueSource.class, spec -> {
			ForgeToolExecutor.Settings settings = ForgeToolExecutor.getDefaultSettings(project);
			configurator.execute(settings);
			spec.getParameters().getSettings().set(settings);
		});
	}

	/**
	 * Executes an external Java process during project configuration.
	 *
	 * @param project      the project
	 * @param configurator an action that configures the exec settings
	 */
	public static void exec(Project project, Action<? super ForgeToolExecutor.Settings> configurator) {
		create(project, configurator).get().rethrowFailure().assertNormalExitValue();
	}

	@Override
	public ExecResult obtain() {
		return ForgeToolExecutor.exec(getExecOperations(), getParameters().getSettings().get());
	}

	public interface Parameters extends ValueSourceParameters {
		@Nested
		Property<ForgeToolExecutor.Settings> getSettings();
	}
}
