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

package org.relativitymc.neoloom.neoforge.util;

import java.util.ArrayDeque;

import org.objectweb.asm.signature.SignatureVisitor;

public class TypeSignatureToStringVisitor extends SignatureVisitor {
	private final StringBuilder sb = new StringBuilder();
	private final ArrayDeque<Object> stack = new ArrayDeque<>();

	private static final Object ARRAY = new Object();

	private static final class ClassContext {
		boolean parsingTypeArgs;
	}

	public TypeSignatureToStringVisitor(int api) {
		super(api);
	}

	public String getString() {
		return sb.toString();
	}

	private void finishType() {
		while (!stack.isEmpty() && stack.peek() == ARRAY) {
			sb.append("[]");
			stack.pop();
		}
	}

	@Override
	public void visitBaseType(char descriptor) {
		switch (descriptor) {
		case 'Z' -> sb.append("boolean");
		case 'C' -> sb.append("char");
		case 'B' -> sb.append("byte");
		case 'S' -> sb.append("short");
		case 'I' -> sb.append("int");
		case 'F' -> sb.append("float");
		case 'J' -> sb.append("long");
		case 'D' -> sb.append("double");
		case 'V' -> sb.append("void");
		default -> throw new IllegalArgumentException("Unknown base type: " + descriptor);
		}

		finishType();
	}

	@Override
	public void visitTypeVariable(String name) {
		sb.append(name);
		finishType();
	}

	@Override
	public SignatureVisitor visitArrayType() {
		stack.push(ARRAY);
		return this;
	}

	@Override
	public void visitClassType(String name) {
		stack.push(new ClassContext());
		sb.append(name.replace('/', '.'));
	}

	@Override
	public void visitInnerClassType(String name) {
		ClassContext ctx = (ClassContext) stack.peek();

		if (ctx.parsingTypeArgs) {
			sb.append('>');
			ctx.parsingTypeArgs = false;
		}

		sb.append('.').append(name);
	}

	private void nextTypeArgument() {
		ClassContext ctx = (ClassContext) stack.peek();

		if (!ctx.parsingTypeArgs) {
			sb.append('<');
			ctx.parsingTypeArgs = true;
		} else {
			sb.append(", ");
		}
	}

	@Override
	public void visitTypeArgument() {
		nextTypeArgument();
		sb.append('?');
	}

	@Override
	public SignatureVisitor visitTypeArgument(char wildcard) {
		nextTypeArgument();

		if (wildcard == EXTENDS) {
			sb.append("? extends ");
		} else if (wildcard == SUPER) {
			sb.append("? super ");
		}

		return this;
	}

	@Override
	public void visitEnd() {
		ClassContext ctx = (ClassContext) stack.pop();

		if (ctx.parsingTypeArgs) {
			sb.append('>');
		}

		finishType();
	}
}
