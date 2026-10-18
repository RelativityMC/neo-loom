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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class MultimapImpl<K, V, C extends Collection<V>> implements Multimap.Specialized<K, V, C> {
	private final Map<K, C> backing;
	private final Supplier<C> collectionBuilder;
	private final C empty;

	MultimapImpl(Map<K, C> backing, Supplier<C> collectionBuilder, C empty) {
		this.backing = backing;
		this.collectionBuilder = collectionBuilder;
		this.empty = empty;
	}

	@Override
	public C get(K key) {
		return Objects.requireNonNullElse(backing.get(key), empty);
	}

	private C getContainer(K key) {
		return backing.computeIfAbsent(key, unused -> collectionBuilder.get());
	}

	@Override
	public void put(K key, V value) {
		getContainer(key).add(value);
	}

	@Override
	public void putAll(K key, Collection<? extends V> values) {
		getContainer(key).addAll(values);
	}

	@Override
	public Set<K> keySet() {
		return backing.keySet();
	}

	@Override
	public Map<K, ? extends C> asMap() {
		return backing;
	}

	@Override
	public Set<Map.Entry<K, C>> entrySet() {
		return backing.entrySet();
	}
}
