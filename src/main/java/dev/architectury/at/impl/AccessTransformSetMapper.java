/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Architectury
 * Copyright (c) 2018 Minecrell (https://github.com/Minecrell)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package dev.architectury.at.impl;

import java.util.Objects;
import java.util.Optional;

import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.bombe.type.signature.MethodSignature;

import net.fabricmc.mappingio.tree.MappingTreeView;

import dev.architectury.at.AccessTransformSet;

final class AccessTransformSetMapper {
	private AccessTransformSetMapper() {
	}

	static AccessTransformSet remap(AccessTransformSet set, MappingTreeView mappings, String from, String to) {
		Objects.requireNonNull(set, "set");
		Objects.requireNonNull(mappings, "mappings");

		int fromNs = mappings.getNamespaceId(from);
		int toNs = mappings.getNamespaceId(to);

		if (fromNs == MappingTreeView.NULL_NAMESPACE_ID) {
			throw new IllegalArgumentException("Source namespace '" + from + "' is not present in the mapping tree");
		} else if (toNs == MappingTreeView.NULL_NAMESPACE_ID) {
			throw new IllegalArgumentException("Target namespace '" + to + "' is not present in the mapping tree");
		}

		AccessTransformSet remapped = AccessTransformSet.create();
		set.getClasses().forEach((className, classSet) -> {
			MappingTreeView.ClassMappingView mapping = mappings.getClass(className, fromNs);
			String newClassName = mapping != null ? mapping.getName(toNs) : className;
			remap(mappings, mapping, classSet, remapped.getOrCreateClass(newClassName), fromNs, toNs);
		});
		return remapped;
	}

	private static void remap(MappingTreeView mappings, MappingTreeView.ClassMappingView mapping, AccessTransformSet.Class set, AccessTransformSet.Class remapped, int fromNs, int toNs) {
		remapped.merge(set.get());
		remapped.mergeAllFields(set.allFields());
		remapped.mergeAllMethods(set.allMethods());

		if (mapping == null) {
			set.getFields().forEach(remapped::mergeField);
			set.getMethods().forEach(remapped::mergeMethod);
		} else {
			set.getFields().forEach((name, transform) ->
					remapped.mergeField(
							Optional.ofNullable(mapping.getField(name, null, fromNs))
									.flatMap(field -> Optional.ofNullable(field.getName(toNs)))
									.orElse(name),
							transform
					)
			);

			set.getMethods().forEach((signature, transform) -> {
				remapped.mergeMethod(
						Optional.ofNullable(mapping.getMethod(signature.getName(), signature.getDescriptor().toString(), fromNs))
								.map(method -> {
									String name = method.getName(toNs);
									if (name == null) name = signature.getName();

									String desc = method.getDesc(toNs);
									return MethodSignature.of(name, desc);
								})
								.orElseGet(() -> new MethodSignature(
										signature.getName(),
										MethodDescriptor.of(mappings.mapDesc(signature.getDescriptor().toString(), fromNs, toNs))
								)),
						transform
				);
			});
		}
	}
}
