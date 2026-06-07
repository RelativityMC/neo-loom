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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import net.fabricmc.loom.util.Constants;

/**
 * This patches ForgeDevLaunchHandler on older forge releases before this commit for use in dev.
 * <a href="https://github.com/MinecraftForge/MinecraftForge/commit/0e74886f8d570d855af57be2c43a8093b3fc5f2f">0e74886f8d570d855af57be2c43a8093b3fc5f2f</a>
 */
public class ForgeOldDevLaunchHandlerPatcher extends ClassVisitor {
	private String className;

	protected ForgeOldDevLaunchHandlerPatcher(ClassVisitor next) {
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

		if ("getMinecraftPaths".equals(name) && "()Ljava/util/List;".equals(descriptor)) {
			MethodNode methodNode = new MethodNode(access, name, descriptor, signature, exceptions);
			return new MethodVisitor(api, methodNode) {
				@Override
				public void visitEnd() {
					super.visitEnd();

					Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
					Frame<SourceValue>[] frames;

					try {
						frames = analyzer.analyze(ForgeOldDevLaunchHandlerPatcher.this.className, methodNode);
					} catch (AnalyzerException e) {
						throw new RuntimeException(e);
					}

					InsnList insns = methodNode.instructions;

					insn_loop: for (int i = 0; i < insns.size(); i++) {
						AbstractInsnNode insn = insns.get(i);
						if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
						MethodInsnNode methodInsn = (MethodInsnNode) insn;

						if (!"net/minecraftforge/fml/loading/targets/ForgeDevLaunchHandler".equals(methodInsn.owner)
								|| !"findJarOnClasspath".equals(methodInsn.name)
								|| !"([Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodInsn.desc)) {
							continue;
						}

						Frame<SourceValue> frame = frames[i];
						if (frame.getStackSize() < 2) continue;
						SourceValue stack = frame.getStack(frame.getStackSize() - 1);

						for (AbstractInsnNode source : stack.insns) {
							if (!(source instanceof LdcInsnNode ldc) || !"client-extra".equals(ldc.cst)) {
								continue insn_loop;
							}
						}

						// now we are sure that it is doing findJarOnClasspath(..., "client-extra")
						InsnList replacements = new InsnList();
						replacements.add(new InsnNode(Opcodes.POP2));
						replacements.add(new LdcInsnNode("assets/.mcassetsroot"));
						replacements.add(new MethodInsnNode(
								Opcodes.INVOKESTATIC,
								"net/minecraftforge/fml/loading/targets/ForgeDevLaunchHandler",
								"getPathFromResource",
								"(Ljava/lang/String;)Ljava/nio/file/Path;",
								false
						));

						insns.insertBefore(insn, replacements);
						insns.remove(insn);
						break; // match only once
					}

					methodNode.accept(methodVisitor);
				}
			};
		}

		return methodVisitor;
	}
}
