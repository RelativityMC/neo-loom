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

package org.relativitymc.neoloom.neoforge.task;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.classtweaker.visitors.ClassTweakerRemapperVisitor;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.service.ScopedServiceFactory;
import net.fabricmc.tinyremapper.TinyRemapper;

import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.io.AccessTransformFormats;
import dev.architectury.loom.accesstransformer.Aw2At;
import dev.architectury.loom.util.LfWriter;

@DisableCachingByDefault
public abstract class GenerateNeoForgePublishingDataTask extends AbstractLoomTask {
	private static final String NAME = Constants.Task.GENERATE_NEOFORGE_PUBLISHING_DATA;

	@InputFiles
	@PathSensitive(PathSensitivity.NONE)
	public abstract ConfigurableFileCollection getInputClassTweakers();

	@OutputFile
	public abstract RegularFileProperty getOutputAccessTransformers();

	@OutputFile
	public abstract RegularFileProperty getOutputInterfaceInjections();

	@Nested
	@Optional
	public abstract Property<TinyRemapperService.Options> getTinyRemapperServiceOptions();

	@Inject
	public GenerateNeoForgePublishingDataTask() {
		setGroup(Constants.TaskGroup.FABRIC);

		getOutputAccessTransformers().convention(getProject().getLayout().getBuildDirectory().file("neoForgePublishing/accesstransformer.cfg"));
		getOutputAccessTransformers().finalizeValueOnRead();

		getOutputInterfaceInjections().convention(getProject().getLayout().getBuildDirectory().file("neoForgePublishing/interfaceinjections.json"));
		getOutputInterfaceInjections().finalizeValueOnRead();
	}

	public static void setup(Project project, ConfigurableFileCollection collection) {
		try {
			project.getTasks().named(NAME, GenerateNeoForgePublishingDataTask.class).configure(task -> {
				task.getInputClassTweakers().from(collection);
			});
			return;
		} catch (UnknownTaskException e) {
			// fall through
		}

		var newTask = project.getTasks().register(NAME, GenerateNeoForgePublishingDataTask.class, task -> {
			task.getInputClassTweakers().from(collection);

			LoomGradleExtension extension = LoomGradleExtension.get(project);

			if (!extension.disableObfuscation()) {
				task.getTinyRemapperServiceOptions().set(TinyRemapperService.createSimple(
						project,
						project.provider(MappingsNamespace.NAMED::toString),
						extension.getProductionNamespace(),
						TinyRemapperService.ClasspathLibraries.EXCLUDE
				));
			}
		});

		Configuration atConfiguration = project.getConfigurations().create(Constants.NeoForge.AT_ELEMENTS, configuration -> {
			configuration.setCanBeConsumed(true);
			configuration.setCanBeResolved(false);
			configuration.attributes(attributes -> {
				attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.CATEGORY_ATTRIBUTE.getType(), Constants.NeoForge.AT_CATEGORY));
			});
		});
		project.getArtifacts().add(Constants.NeoForge.AT_ELEMENTS, newTask.map(GenerateNeoForgePublishingDataTask::getOutputAccessTransformers), artifact -> {
			artifact.builtBy(newTask);
			artifact.setClassifier(Constants.NeoForge.AT_CATEGORY);
		});

		Configuration ijConfiguration = project.getConfigurations().create(Constants.NeoForge.IJ_ELEMENTS, configuration -> {
			configuration.setCanBeConsumed(true);
			configuration.setCanBeResolved(false);
			configuration.attributes(attributes -> {
				attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.CATEGORY_ATTRIBUTE.getType(), Constants.NeoForge.IJ_CATEGORY));
			});
		});
		project.getArtifacts().add(Constants.NeoForge.IJ_ELEMENTS, newTask.map(GenerateNeoForgePublishingDataTask::getOutputInterfaceInjections), artifact -> {
			artifact.builtBy(newTask);
			artifact.setClassifier(Constants.NeoForge.IJ_CATEGORY);
		});

		AdhocComponentWithVariants component = (AdhocComponentWithVariants) project.getComponents().getByName("java");
		component.addVariantsFromConfiguration(atConfiguration, details -> {
			if (details.getConfigurationVariant().getArtifacts().isEmpty()) {
				details.skip();
			}
		});
		component.addVariantsFromConfiguration(ijConfiguration, details -> {
			if (details.getConfigurationVariant().getArtifacts().isEmpty()) {
				details.skip();
			}
		});
	}

	@TaskAction
	public void generatePublishingData() throws IOException {
		AccessTransformSet atSet = AccessTransformSet.create();
		Map<String, Set<String>> interfaceInjections = new HashMap<>();

		for (File ctFile : getInputClassTweakers().getFiles()) {
			try (var reader = Files.newBufferedReader(ctFile.toPath())) {
				Map<String, Set<String>> finalInterfaceInjections = interfaceInjections;
				AccessTransformSet finalAtSet = atSet;
				ClassTweakerVisitor classTweakerVisitor = new ClassTweakerVisitor() {
					@Override
					public AccessWidenerVisitor visitAccessWidener(String owner) {
						return Aw2At.createAccessWidenerVisitor(finalAtSet, owner, true);
					}

					@Override
					public void visitInjectedInterface(String owner, String iface, boolean transitive) {
						if (!transitive) return;

						finalInterfaceInjections.computeIfAbsent(owner, unused -> new HashSet<>()).add(iface);
					}
				};

				if (getTinyRemapperServiceOptions().isPresent()) {
					try (var serviceFactory = new ScopedServiceFactory()) {
						TinyRemapperService tinyRemapperService = serviceFactory.get(getTinyRemapperServiceOptions().get());
						TinyRemapper tinyRemapper = tinyRemapperService.getTinyRemapperForRemapping();

						classTweakerVisitor = new ClassTweakerRemapperVisitor(
								classTweakerVisitor,
								tinyRemapper.getEnvironment().getRemapper(),
								getTinyRemapperServiceOptions().get().getFrom().get(),
								getTinyRemapperServiceOptions().get().getTo().get()
						);
					}
				}

				ClassTweakerReader.create(classTweakerVisitor).read(reader);
			}
		}

		try (Writer writer = new LfWriter(Files.newBufferedWriter(getOutputAccessTransformers().get().getAsFile().toPath()))) {
			AccessTransformFormats.FML.write(writer, atSet);
		}

		{
			JsonObject json = new JsonObject();

			interfaceInjections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(entry -> {
				String className = entry.getKey();
				JsonArray array = new JsonArray();
				entry.getValue().stream().sorted().forEachOrdered(array::add);
				json.add(className, array);
			});

			try (Writer writer = Files.newBufferedWriter(getOutputInterfaceInjections().get().getAsFile().toPath())) {
				LoomGradlePlugin.GSON.toJson(json, writer);
			}
		}
	}
}
