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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecResult;

import net.fabricmc.loom.util.service.Service;
import net.fabricmc.loom.util.service.ServiceFactory;
import net.fabricmc.loom.util.service.ServiceType;

/**
 * A service that can execute Forge tools in tasks and during project configuration.
 */
public final class ForgeToolService extends Service<ForgeToolService.Options> {
	public static final ServiceType<Options, ForgeToolService> TYPE = new ServiceType<>(Options.class, ForgeToolService.class);

	public interface Options extends Service.Options {
		/**
		 * The default settings from {@link ForgeToolExecutor}.
		 * It contains the verbosity and JVM toolchain options that are dependent on the project state.
		 */
		@Nested
		Property<ForgeToolExecutor.Settings> getBaseSettings();

		@Inject
		ObjectFactory getObjects();

		@Inject
		ProviderFactory getProviders();
	}

	public static Provider<Options> createOptions(Project project) {
		return TYPE.create(project, options -> {
			options.getBaseSettings().set(ForgeToolExecutor.getDefaultSettings(project));
		});
	}

	public ForgeToolService(Options options, ServiceFactory serviceFactory) {
		super(options, serviceFactory);
	}

	/**
	 * Executes the tool specified in the spec.
	 *
	 * @param configurator an action that configures the spec
	 * @return the execution result
	 */
	public ExecResult exec(Action<? super ForgeToolExecutor.Settings> configurator) {
		return getOptions().getProviders().of(ForgeToolValueSource.class, spec -> {
			final ForgeToolExecutor.Settings settings = getOptions().getObjects().newInstance(ForgeToolExecutor.Settings.class);
			ForgeToolExecutor.copySettings(getOptions().getBaseSettings().get(), settings);
			configurator.execute(settings);
			spec.getParameters().getSettings().set(settings);
		}).get();
	}
}
