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

import java.nio.file.Path;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.fabricmc.loom.util.Constants;

/**
 * This patches ForgeDevLaunchHandler on older forge releases before this commit for use in dev.
 * <a href="https://github.com/MinecraftForge/MinecraftForge/commit/0e74886f8d570d855af57be2c43a8093b3fc5f2f">0e74886f8d570d855af57be2c43a8093b3fc5f2f</a>
 */
public class ForgeOldUserdevLaunchHandlerPatcher extends ClassVisitor {
	private String className;

	protected ForgeOldUserdevLaunchHandlerPatcher(ClassVisitor next) {
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

		if ("processStreams".equals(name) && "([Ljava/lang/String;Lnet/minecraftforge/fml/loading/VersionInfo;Ljava/util/stream/Stream$Builder;Ljava/util/stream/Stream$Builder;)V".equals(descriptor)) {
			return new MethodVisitor(api, methodVisitor) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
					// INVOKESTATIC net/minecraftforge/fml/loading/targets/ForgeUserdevLaunchHandler.findJarOnClasspath ([Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;
					if (opcode == Opcodes.INVOKESTATIC
							&& "net/minecraftforge/fml/loading/targets/ForgeUserdevLaunchHandler".equals(owner)
							&& "findJarOnClasspath".equals(name)
							&& "([Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;".equals(descriptor)) {
						super.visitInsn(Opcodes.POP2);
						super.visitLdcInsn(Constants.NeoForge.PROP_MERGED_JAR);
						super.visitMethodInsn(
								Opcodes.INVOKESTATIC,
								Type.getInternalName(System.class),
								"getProperty",
								Type.getMethodDescriptor(Type.getType(String.class), Type.getType(String.class)),
								false
						);
						super.visitInsn(Opcodes.ICONST_0);
						super.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(String.class));
						super.visitMethodInsn(
								Opcodes.INVOKESTATIC,
								Type.getInternalName(Path.class),
								"of",
								Type.getMethodDescriptor(Type.getType(Path.class), Type.getType(String.class), Type.getType(String[].class)),
								true
						);
						return;
					}

					super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				}
			};
		}

		return methodVisitor;
	}
}
