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

package dev.architectury.loom.util.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Stream;

import net.fabricmc.loom.util.Pair;

public interface Multimap<K, V> {
	static <K, V> Specialized<K, V, SequencedSet<V>> setMultimap() {
		return new MultimapImpl<>(new HashMap<>(), LinkedHashSet::new, Collections.emptySortedSet());
	}

	static <K, V> Specialized<K, V, List<V>> listMultimap() {
		return new MultimapImpl<>(new HashMap<>(), ArrayList::new, List.of());
	}

	Collection<V> get(K key);
	void put(K key, V value);
	void putAll(K key, Collection<? extends V> values);
	Set<K> keySet();
	Map<K, ? extends Collection<V>> asMap();
	Set<? extends Map.Entry<K, ? extends Collection<V>>> entrySet();

	default Stream<Pair<K, V>> streamEntries() {
		return entrySet().stream()
				.flatMap(entry -> entry.getValue().stream().map(value -> new Pair<>(entry.getKey(), value)));
	}

	interface Specialized<K, V, C extends Collection<V>> extends Multimap<K, V> {
		@Override
		C get(K key);

		@Override
		Map<K, ? extends C> asMap();

		@Override
		Set<Map.Entry<K, C>> entrySet();
	}
}
