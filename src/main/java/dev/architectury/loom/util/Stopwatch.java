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

package dev.architectury.loom.util;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

public final class Stopwatch {
	private @Nullable Instant start;

	private Stopwatch() {
	}

	public boolean isRunning() {
		return start != null;
	}

	public void start() {
		if (isRunning()) {
			throw new IllegalStateException("Stopwatch already started");
		}

		start = Instant.now();
	}

	public Stopped stop() {
		if (!isRunning()) {
			throw new IllegalStateException("Stopwatch not started");
		}

		final Instant end = Instant.now();
		final Duration duration = Duration.between(start, end);
		return new Stopped(duration);
	}

	public static Stopwatch createStarted() {
		var stopwatch = new Stopwatch();
		stopwatch.start();
		return stopwatch;
	}

	public static Stopwatch createUnstarted() {
		return new Stopwatch();
	}

	@Override
	public String toString() {
		return isRunning() ? "Stopwatch[running]" : "Stopwatch[unstarted]";
	}

	public record Stopped(Duration duration) {
		@Override
		public String toString() {
			if (duration.toDays() > 0) {
				return duration.toDays() + "d";
			} else if (duration.toHours() > 0) {
				return duration.toHours() + "h " + duration.toMinutesPart() + "min";
			} else if (duration.toMinutes() > 0) {
				return duration.toMinutes() + "min " + duration.toSecondsPart() + "s";
			} else if (duration.toSeconds() > 0) {
				return duration.toSeconds() + "s";
			} else if (duration.toMillis() > 0) {
				return duration.toMillis() + "ms";
			}

			return duration.toNanos() + "ns";
		}
	}
}
