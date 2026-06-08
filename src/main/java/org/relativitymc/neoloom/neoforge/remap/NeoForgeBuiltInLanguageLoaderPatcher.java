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

package org.relativitymc.neoloom.neoforge.remap;

import java.util.ListIterator;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loom.util.Constants;

public class NeoForgeBuiltInLanguageLoaderPatcher extends ClassVisitor {
	private String className;

	protected NeoForgeBuiltInLanguageLoaderPatcher(ClassVisitor next) {
		super(Constants.ASM_VERSION, next);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.className = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

		// Lnet/neoforged/fml/loading/BuiltInLanguageLoader;version()Ljava/lang/String;
		if ("version".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
			MethodNode methodNode = new MethodNode(access, name, descriptor, signature, exceptions);
			return new MethodVisitor(api, methodNode) {
				@Override
				public void visitEnd() {
					super.visitEnd();

					// figure out whether we are before startup overhaul: https://github.com/neoforged/FancyModLoader/pull/357
					// Lnet/neoforged/fml/loading/VersionInfo;fmlVersion()Ljava/lang/String;
					boolean beforeStartupOverhaul = StreamSupport.stream(methodNode.instructions.spliterator(), false)
							.anyMatch(insn -> {
								return insn instanceof MethodInsnNode methodInsnNode
										&& methodInsnNode.getOpcode() == Opcodes.INVOKEVIRTUAL
										&& "net/neoforged/fml/loading/VersionInfo".equals(methodInsnNode.owner)
										&& "fmlVersion".equals(methodInsnNode.name)
										&& "()Ljava/lang/String;".equals(methodInsnNode.desc)
										&& !methodInsnNode.itf;
							});

					if (beforeStartupOverhaul) {
						ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

						while (iterator.hasNext()) {
							AbstractInsnNode insn = iterator.next();

							// Replace Lnet/neoforged/fml/loading/JarVersionLookupHandler;getVersion(Ljava/lang/Class;)Ljava/util/Optional;
							// with Ljava/util/Optional;empty()Ljava/util/Optional;
							if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
							MethodInsnNode methodInsnNode = (MethodInsnNode) insn;

							if (!"net/neoforged/fml/loading/JarVersionLookupHandler".equals(methodInsnNode.owner)
									|| !"getVersion".equals(methodInsnNode.name)
									|| !"(Ljava/lang/Class;)Ljava/util/Optional;".equals(methodInsnNode.desc)
									|| methodInsnNode.itf) {
								continue;
							}

							iterator.remove();
							iterator.add(new InsnNode(Opcodes.POP));
							iterator.add(new MethodInsnNode(
									Opcodes.INVOKESTATIC,
									Type.getInternalName(Optional.class),
									"empty",
									"()Ljava/util/Optional;",
									false
							));

							break;
						}
					}

					methodNode.accept(methodVisitor);
				}
			};
		}

		return methodVisitor;
	}
}
