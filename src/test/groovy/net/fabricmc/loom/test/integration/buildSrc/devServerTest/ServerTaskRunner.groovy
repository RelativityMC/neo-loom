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

package net.fabricmc.loom.test.integration.buildSrc.devServerTest

class ServerTaskRunner {

	private final StringBuffer buffer = new StringBuffer()
	private final StringBuilder allStdout = new StringBuilder()
	private final PipedInputStream stdinForGradle = new PipedInputStream()
	private final PrintStream stdin = new PrintStream(new PipedOutputStream(this.stdinForGradle))
	private boolean isStopping = false

	ServerTaskRunner() throws IOException {
	}

	InputStream getStdinForGradle() {
		return this.stdinForGradle
	}

	OutputStream getStdoutForGradle(OutputStream delegate) {
		PrintStream delegatePrintStream = new PrintStream(delegate)
		return new OutputStream() {
					@Override
					void write(int b) throws IOException {
						delegate.write(b)
						allStdout.append((char) b)
						if (b == (char) '\n') {
							try {
								String out = buffer.toString()
								if (!isStopping && out.contains("Done ") && out.contains("For help, type \"help\"")) {
									isStopping = true

									Thread.start {
										delegatePrintStream.println("Stopping server in 1s")

										try {
											Thread.sleep(1000)
										} catch (InterruptedException ignored) {
										}

										delegatePrintStream.println("Sending stop command")
										stdin.println("stop")
										stdin.close()
									}
								}
							} finally {
								buffer.setLength(0)
							}
						} else {
							buffer.append((char) b)
						}
					}
				}
	}

	boolean isStopping() {
		return this.isStopping
	}
}
